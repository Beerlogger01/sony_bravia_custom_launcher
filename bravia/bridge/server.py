import asyncio
import contextlib
import hmac
import json
import os
import random
import ssl
import time
from pathlib import Path
from aiohttp import ClientSession, ClientTimeout, web
from catalog import Catalog
from history import History

REGISTRY = {"entities": "entity_registry", "devices": "device_registry", "areas": "area_registry"}

class Feed:
    def __init__(self, overrides, history=None):
        self.history = history
        self.overrides = overrides
        self.latest = dict(live=False, stamp=0, rows=[], dashboard="")
        self.clients = set()

    def publish(self, **changes):
        if changes.get("live") and self.history:
            self.history.record(changes["rows"], time.time())
        if all(self.latest.get(k) == v for k, v in changes.items()):
            return
        self.latest = dict(self.latest, **changes)
        if self.latest["live"]:
            self.latest["stamp"] = int(time.time())
        for event in self.clients:
            event.set()

async def session(ws, token, feed):
    async def read():
        return await asyncio.wait_for(ws.receive_json(), 45)
    if (await read()).get("type") != "auth_required":
        raise ValueError("handshake")
    await ws.send_json(dict(type="auth", access_token=token))
    if (await read()).get("type") != "auth_ok":
        raise PermissionError("authentication")
    catalog = Catalog()
    requests = {}
    refresh_again = set()
    number = 0
    async def send(kind, command, **args):
        nonlocal number
        number += 1
        requests[number] = kind
        await ws.send_json(dict(id=number, type=command, **args))
    for kind, reg in REGISTRY.items():
        await send(kind+"-event", "subscribe_events", event_type=reg+"_updated")
        await send(kind, "config/"+reg+"/list")
    await send("dash", "lovelace/dashboards/list")
    await send("states", "subscribe_entities")
    dashboard = ""
    initialized = False
    # A separate reader lets frequent unrelated events share a single projection pass.
    dirty = asyncio.Event()
    async def flush():
        while True:
            await dirty.wait()
            await asyncio.sleep(.35)
            dirty.clear()
            if initialized:
                feed.publish(live=True, reason="", rows=catalog.rows(feed.overrides), dashboard=dashboard)
    task = asyncio.create_task(flush())
    try:
        while True:
            try:
                msg = await asyncio.wait_for(ws.receive_json(), 25)
            except asyncio.TimeoutError:
                await send("heartbeat", "ping")
                msg = await read()
            kind = requests.get(msg.get("id"), "")
            if msg["type"] == "result":
                if kind == "states" and not msg["success"]:
                    raise ValueError("subscription rejected")
                if msg["success"] and kind in REGISTRY:
                    catalog.registry(kind, msg["result"])
                if kind == "dash" and msg["success"]:
                    dashboard = next((d["url_path"] for d in msg["result"]
                                      if d.get("title") == "Домашний дашборд"), "")
                if kind in REGISTRY or kind == "dash":
                    requests.pop(msg["id"], None)
                    if kind in refresh_again:
                        refresh_again.remove(kind)
                        await send(kind, "config/"+REGISTRY[kind]+"/list")
            elif msg["type"] == "event":
                if kind == "states":
                    catalog.event(msg["event"])
                    initialized = True
                elif kind.endswith("-event"):
                    table = kind[:-6]
                    if table not in requests.values():
                        await send(table, "config/"+REGISTRY[table]+"/list")
                    else:
                        refresh_again.add(table)
            elif msg["type"] == "pong":
                requests.pop(msg["id"], None)
            dirty.set()
    finally:
        task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await task

async def reconnect(url, token, feed):
    delay = 1
    async with ClientSession(timeout=ClientTimeout(total=None, sock_connect=12)) as client:
        while True:
            try:
                async with client.ws_connect(url, heartbeat=20, max_msg_size=16*1024**2) as ws:
                    await session(ws, token, feed)
            except PermissionError:
                feed.publish(live=False, reason="HA отклонил токен")
            except Exception:
                pass  # No payloads, URLs or credentials in logs.
            if feed.latest["live"]:
                delay = 1
            feed.publish(live=False)
            await asyncio.sleep(delay + random.random())
            delay = min(delay*2, 60)

def make_app(config):
    rules = {r["entity"]: r["section"] for r in config.get("cards", [])}
    feed = Feed(rules, History(config.get("history_db", "/data/history.sqlite")))
    secret = config["tv_key"].encode()
    if len(secret) < 24:
        raise ValueError("tv_key needs at least 24 characters")
    def authorize(request):
        if not hmac.compare_digest(request.headers.get("Authorization","").encode(), b"Bearer "+secret):
            raise web.HTTPUnauthorized()
    async def history(request):
        authorize(request)
        key = request.query.get("key", "")
        row = next((r for r in feed.latest["rows"] if r["key"] == key), None)
        if row is None:
            raise web.HTTPNotFound()
        return web.json_response(feed.history.series(key, row["unit"], time.time()))
    async def events(request):
        authorize(request)
        response = web.StreamResponse(headers={"Content-Type":"text/event-stream","Cache-Control":"no-store"})
        await response.prepare(request)
        wake = asyncio.Event()
        feed.clients.add(wake)
        wake.set()
        previous = None
        try:
            while True:
                if wake.is_set():
                    wake.clear()
                    packet = feed.latest.copy()
                    current = {r["key"]: r for r in packet["rows"]}
                    if previous is not None:
                        del packet["rows"]
                        packet["upsert"] = [r for k,r in current.items() if previous.get(k) != r]
                        packet["remove"] = list(previous.keys() - current.keys())
                    previous = current
                    payload = "data: "+json.dumps(packet, ensure_ascii=False, separators=(",", ":"))+"\n\n"
                else:
                    payload = ": heartbeat\n\n"
                await asyncio.wait_for(response.write(payload.encode()), 10)
                try:
                    await asyncio.wait_for(wake.wait(), 15)
                except asyncio.TimeoutError:
                    pass
        finally:
            feed.clients.discard(wake)
    async def lifecycle(app):
        task = asyncio.create_task(reconnect(config["ha_ws"], config["ha_token"], feed))
        yield
        task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await task
        feed.history.db.close()
    app = web.Application()
    app.router.add_get("/v1/events", events)
    app.router.add_get("/v1/history", history)
    app.cleanup_ctx.append(lifecycle)
    return app

if __name__ == "__main__":
    cfg = json.loads(Path(os.getenv("BRAVIA_CONFIG", "/data/options.json")).read_text())
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(cfg["cert"], cfg["key"])
    web.run_app(make_app(cfg), port=8443, ssl_context=tls, access_log=None)
