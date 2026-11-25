import asyncio
import json
import secrets
import tempfile
import time
import unittest
from pathlib import Path
from aiohttp import web
from aiohttp.test_utils import TestClient, TestServer
from catalog import Catalog
from history import History
from server import make_app

def state(value, title="Комната", dc="temperature", unit="°C"):
    return {"s": value, "a": {"friendly_name":title,"device_class":dc,"unit_of_measurement":unit}}

class CompactTests(unittest.TestCase):
    def test_731_add_change_remove_and_registry(self):
        c = Catalog()
        c.event({"a":{f"sensor.mock_{i}":state(str(i)) for i in range(731)}})
        self.assertEqual(len(c.rows({})),731)
        c.registry("areas",[dict(area_id="a",name="Спальня")])
        c.registry("devices",[dict(id="d",area_id="a",name="Климатический датчик")])
        c.registry("entities",[dict(entity_id="sensor.mock_0",id="stable",device_id="d"),
                               dict(entity_id="sensor.mock_1",disabled_by="user")])
        c.event({"c":{"sensor.mock_0":{"+":{"s":"19.8","a":{"friendly_name":"Воздух"}},"-":{"a":["unit_of_measurement"]}}}})
        rows=c.rows({}); self.assertEqual(len(rows),730)
        row=next(r for r in rows if r["key"]=="stable")
        self.assertEqual((row["title"],row["value"],row["area"],row["unit"]),("Воздух","19.8","Спальня",""))
        self.assertIn("sensor.mock_1",c.states)
        c.event({"r":["sensor.mock_0"],"a":{"sensor.renamed":state("20")}})
        c.registry("entities",[dict(entity_id="sensor.renamed",id="stable")])
        self.assertEqual(next(r["eid"] for r in c.rows({}) if r["key"]=="stable"),"sensor.renamed")
        c.event({"r":["sensor.renamed"]})
        self.assertFalse(any(r["key"]=="stable" for r in c.rows({})))

    def test_unknown_unavailable_and_absent_metadata(self):
        c=Catalog(); c.event({"a":{"sensor.a":state("unknown"),"sensor.b":state("unavailable"),
                                   "device_tracker.mock":state("private")}})
        rows=c.rows({}); self.assertEqual({r["value"] for r in rows},{"unknown","unavailable"})
        self.assertTrue(all(r["key"].startswith("entity:") for r in rows))

    def test_history_isolation_and_expiry(self):
        h=History(":memory:"); c=Catalog()
        c.event({"a":{"sensor.room":state("20"),"sensor.cpu":state("60")}})
        h.record(c.rows({}),100000)
        self.assertEqual([p[1] for p in h.series("entity:sensor.room","°C",100000)],[20])
        self.assertEqual(h.series("entity:sensor.room","°F",100000),[])
        self.assertEqual(h.series("entity:sensor.room","°C",200000),[])

class Integration(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.inbox=asyncio.Queue()
        self.valid=secrets.token_urlsafe()
        self.tv=secrets.token_urlsafe()
        self.allow=True
        self.commands=[]
        async def mock(request):
            ws=web.WebSocketResponse()
            await ws.prepare(request)
            await ws.send_json({"type":"auth_required"})
            auth=await ws.receive_json()
            if not self.allow or auth["access_token"]!=self.valid:
                await ws.send_json({"type":"auth_invalid"})
                return ws
            await ws.send_json({"type":"auth_ok"})
            async for msg in ws:
                command=json.loads(msg.data); self.commands.append(command["type"])
                cid=command["id"]; kind=command["type"]
                result=[]
                if kind=="config/entity_registry/list": result=[dict(entity_id="sensor.mock",id="stable")]
                if kind=="lovelace/dashboards/list": result=[dict(title="Домашний дашборд",url_path="verified-path")]
                await ws.send_json(dict(type="result",success=True,id=cid,result=result))
                if kind=="subscribe_entities":
                    await ws.send_json(dict(type="event",id=cid,event={"a":{"sensor.mock":state("18")}}))
                    await self.inbox.put((ws,cid))
            return ws
        app=web.Application();app.router.add_get("/api/websocket",mock)
        self.ha=TestServer(app);await self.ha.start_server()
        self.tmp=tempfile.TemporaryDirectory()
        self.client=TestClient(TestServer(make_app(dict(ha_ws=str(self.ha.make_url("/api/websocket")).replace("http:","ws:"),
            ha_token=self.valid,tv_key=self.tv,history_db=str(Path(self.tmp.name)/"history")))))
    async def asyncTearDown(self):
        await self.client.close();await self.ha.close();self.tmp.cleanup()
    async def read(self,stream):
        while True:
            line=await asyncio.wait_for(stream.content.readline(),8)
            if line.startswith(b"data: "):return json.loads(line[6:])
    async def test_subscribe_updates_reconnect_and_readonly(self):
        await self.client.start_server()
        headers={"Authorization":"Bearer "+self.tv}
        self.assertEqual((await self.client.get("/v1/events")).status,401)
        self.assertEqual((await self.client.post("/v1/events",headers=headers)).status,405)
        stream=await self.client.get("/v1/events",headers=headers)
        ws,cid=await asyncio.wait_for(self.inbox.get(),3)
        first=await self.read(stream)
        live=first if first["live"] else await self.read(stream)
        self.assertEqual(live["dashboard"],"verified-path")
        self.assertEqual((live.get("rows") or live["upsert"])[0]["key"],"stable")
        await ws.send_json(dict(type="event",id=cid,event={"c":{"sensor.mock":{"+":{"s":"21"}}}}))
        self.assertEqual((await self.read(stream))["upsert"][0]["value"],"21")
        await ws.send_json(dict(type="event",id=cid,event={"r":["sensor.mock"]}))
        self.assertEqual((await self.read(stream))["remove"],["stable"])
        await ws.close()
        self.assertFalse((await self.read(stream))["live"])
        self.assertTrue((await self.read(stream))["live"])
        self.assertEqual(self.commands.count("subscribe_entities"),2)
        self.assertNotIn("call_service",self.commands)
        stream.close()
    async def test_invalid_ha_token_remains_offline(self):
        self.allow=False;await self.client.start_server()
        stream=await self.client.get("/v1/events",headers={"Authorization":"Bearer "+self.tv})
        self.assertFalse((await self.read(stream))["live"])
        await asyncio.sleep(.15)
        self.assertEqual(self.commands,[])
        stream.close()

if __name__=="__main__":unittest.main()
