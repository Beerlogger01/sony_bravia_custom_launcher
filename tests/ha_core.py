"""Run against Home Assistant Core with an ephemeral HTTPS listener and synthetic states."""
import asyncio
import json
import os
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import tempfile

from aiohttp import ClientSession
from homeassistant import bootstrap, loader
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import area_registry as ar, entity_registry as er

async def test(config,lan=False):
    root=Path(__file__).resolve().parents[1]
    shutil.copytree(root/"custom_components",config/"custom_components",dirs_exist_ok=True)
    cert,key=config/"fixture.pem",config/"fixture.key"
    subprocess.run(["openssl","req","-x509","-newkey","rsa:2048","-nodes","-days","1",
                    "-subj","/CN=localhost","-addext","subjectAltName=IP:127.0.0.1", "-keyout",str(key),"-out",str(cert)],check=True,stderr=subprocess.DEVNULL)
    hass=HomeAssistant(str(config))
    loader.async_setup(hass)
    hass.config.skip_pip=True
    settings={"homeassistant":{"latitude":0,"longitude":0,"elevation":0,"unit_system":"metric","time_zone":"UTC","country":"US"},
              "http":{"server_host":"127.0.0.1","server_port":18448,"ssl_certificate":str(cert),"ssl_key":str(key)}}
    if lan:
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as route:
            route.connect(("192.0.2.1",9))  # Determine local interface without sending data.
            host=route.getsockname()[0]
        settings["http"]={"server_host":host,"server_port":18448}
    await bootstrap.async_from_config_dict(settings,hass)
    await hass.async_start()
    try:
        cfg=hass.config_entries
        form=await cfg.flow.async_init("bravia_launcher",context={"source":"user"})
        added=await cfg.flow.async_configure(form["flow_id"],{"name":"Test screen"})
        entry=added["result"]
        await hass.async_block_till_done()
        assert entry.state.value=="loaded",entry.state
        registry=er.async_get(hass)
        reg=registry.async_get_or_create("sensor","fixture","room",suggested_object_id="sample_room")
        eid=reg.entity_id
        for i in range(731):
            hass.states.async_set(f"sensor.fixture_{i}",str(i))
        hass.states.async_set(eid,"20",{"device_class":"temperature","unit_of_measurement":"°C"})
        async def option(step,data=None):
            menu=await cfg.options.async_init(entry.entry_id)
            form=await cfg.options.async_configure(menu["flow_id"],{"next_step_id":step})
            if data is None:return form
            return await cfg.options.async_configure(form["flow_id"],data)
        areas=ar.async_get(hass)
        room=areas.async_create("Test zone")
        reg=registry.async_update_entity(eid,area_id=room.id)
        await option("cards",{"areas":[room.id],"favorites":[eid]})
        form=await cfg.flow.async_init("bravia_launcher",context={"source":"user"})
        second=(await cfg.flow.async_configure(form["flow_id"],{"name":"Other screen"}))["result"]
        await hass.async_block_till_done()
        assert second.entry_id!=entry.entry_id and "digest" not in second.data
        ctx=ssl.create_default_context(cafile=str(cert))
        prefix="https://127.0.0.1:18448/api/bravia_launcher/v1"
        if lan:
            prefix=f"http://{host}:18448/api/bravia_launcher/v1"
            async with ClientSession() as probe:
                async with probe.get(prefix+"/events") as res:assert res.status==403
            await option("transport",{"lan_http":True,"tv_ip":host})
            assert entry.options["areas"]==[room.id]
        async with ClientSession() as client:
            form=await option("pair")
            code=form["description_placeholders"]["code"]
            async with client.post(prefix+"/pair",json={"code":code},ssl=ctx) as res:
                assert res.status==200,res.status
                token=(await res.json())["token"]
            assert token not in json.dumps(dict(entry.data))
            hdr={"Authorization":"Bearer "+token}
            async with client.post(prefix+"/pair",json={"code":code},ssl=ctx) as res:assert res.status==401
            async with client.get(prefix+"/events",ssl=ctx) as res:assert res.status==401
            async with client.get(prefix+"/events",headers=hdr,ssl=ctx) as stream:
                assert stream.status==200,stream.status
                async def message():
                    async with asyncio.timeout(8):
                        while line:=await stream.content.readline():
                            if line.startswith(b"data: "):return json.loads(line[6:])
                    raise AssertionError("unexpected EOF")
                snap=await message()
                assert len(snap["rows"])==1 and snap["favorites"]==[reg.id]
                # A normal TV sees a filtered projection. Exercise the largest explicit selection too.
                fixture_ids=[f"sensor.fixture_{i}" for i in range(731)]
                await option("cards",{"entities":fixture_ids,"areas":[room.id],"favorites":[eid]})
                assert len((await message())["upsert"])==731
                for i, entity_id in enumerate(fixture_ids):
                    hass.states.async_set(entity_id,str(i+1))
                burst=await message()
                assert len(burst["upsert"])==731 and not burst["remove"]
                await option("cards",{"areas":[room.id],"favorites":[eid]})
                assert len((await message())["remove"])==731
                hass.states.async_set(eid,"22",{"device_class":"temperature","unit_of_measurement":"°C"})
                assert (await message())["upsert"][0]["value"]=="22"
                async with client.post(prefix+"/action",json={"key":reg.id,"action":"turn_on"},headers=hdr,ssl=ctx) as res:assert res.status==403
                new_eid="sensor.fixture_renamed_room"
                hass.states.async_remove(eid)
                reg=registry.async_update_entity(eid,new_entity_id=new_eid)
                eid=new_eid
                hass.states.async_set(eid,"23")
                assert (await message())["upsert"][0]["key"]==reg.id
                registry.async_update_entity(eid, name="Renamed reading")
                hass.states.async_set(eid,"unavailable",{"friendly_name":"Renamed reading"})
                renamed=(await message())["upsert"][0]
                assert renamed["key"]==reg.id and renamed["title"]=="Renamed reading" and not renamed["available"]
                registry.async_update_entity(eid, area_id=None)
                assert (await message())["remove"]==[reg.id]
                registry.async_update_entity(eid, area_id=room.id)
                assert (await message())["upsert"][0]["area"]==room.name
                hass.states.async_remove(eid)
                assert (await message())["remove"]==[reg.id]
                lamp=registry.async_get_or_create("light","fixture","lamp",suggested_object_id="test_lamp")
                hass.states.async_set(lamp.entity_id,"off")
                await option("cards",{"entities":[lamp.entity_id],"control":[lamp.entity_id]})
                allowed=(await message())["upsert"][0]
                assert allowed["key"]==lamp.id and allowed["controllable"]
                calls=[]
                @callback
                def record(call):
                    calls.append(call.data["entity_id"])
                hass.services.async_register("light","turn_on",record)
                async with client.post(prefix+"/action",json={"key":lamp.id,"action":"turn_on"},headers=hdr,ssl=ctx) as res:
                    assert res.status==200 and calls==[lamp.entity_id]
                await option("cards",{"entities":[lamp.entity_id]})
                assert not (await message())["upsert"][0]["controllable"]
                async with client.post(prefix+"/action",json={"key":lamp.id,"action":"turn_on"},headers=hdr,ssl=ctx) as res:
                    assert res.status==403 and calls==[lamp.entity_id]
                # Create a script card through the public options flow.
                script=registry.async_get_or_create("script","fixture","clean")
                hass.states.async_set(script.entity_id,"off")
                calls.clear()
                hass.services.async_register("script","turn_on",record)
                form=await option("designer",{"card":"new"})
                assert form["step_id"]=="edit"
                edited=await cfg.options.async_configure(form["flow_id"],dict(template="script",group="Сценарии",
                    operation="turn_on",link="Избранное",title="Clean",entity=script.entity_id,confirm=True,event=True,event_states="on"))
                assert edited["type"]=="create_entry",edited
                card_id=next(iter(entry.options["designer"]))
                cardkey="card:"+card_id
                snapshot=await message()
                assert any(row["key"]==cardkey and row["operation"]=="turn_on" for row in snapshot["upsert"])
                assert not any(row["key"]==script.id for row in snapshot["upsert"])
                for target,op,status in [(script.id,"turn_on",403),(cardkey,"turn_off",403),(cardkey,"turn_on",200)]:
                    async with client.post(prefix+"/action",json={"key":target,"action":op},headers=hdr,ssl=ctx) as res:
                        assert res.status==status,(res.status,target,op)
                assert calls==[script.entity_id]
                await option("layout",{"home":"Сценарии","red":"Свет","live":True})
                assert (await message())["layout"]["live"]
                hass.states.async_set(script.entity_id,"on")
                assert any(r["key"]==cardkey and r["event"] for r in (await message())["upsert"])
                switch=registry.async_get_entity_id("switch","bravia_launcher",entry.entry_id+"_live_events")
                await hass.services.async_call("switch","turn_off",{"entity_id":switch},blocking=True)
                quiet=await message()
                assert not quiet["layout"]["live"] and not any(r.get("event") for r in quiet["upsert"])
                await option("cards",{"areas":[room.id]})
                assert card_id in entry.options["designer"] and entry.options["layout"]["red"]=="Свет"
                await option("revoke",{})
                await asyncio.wait_for(stream.content.read(),3)
            async with client.get(prefix+"/events",headers=hdr,ssl=ctx) as res:assert res.status==401
            code=(await option("pair"))["description_placeholders"]["code"]
            async with client.post(prefix+"/pair",json={"code":code},ssl=ctx) as res:
                replacement=(await res.json())["token"]
        print("HA Core: config/options, pairing, replay and invalid token, TLS snapshot/delta/removal, scoped control denial, stream revocation. PASS")
    finally:
        await hass.async_stop()

    # Recreate Core from the same storage, with a new runtime and empty state machine.
    restarted=HomeAssistant(str(config))
    loader.async_setup(restarted)
    restarted.config.skip_pip=True
    await bootstrap.async_from_config_dict(settings,restarted)
    await restarted.async_start()
    try:
        assert len(restarted.config_entries.async_entries("bravia_launcher"))==2
        async with ClientSession() as client:
            async with client.get(prefix+"/events",headers=hdr,ssl=ctx) as res:assert res.status==401
            async with client.get(prefix+"/events",headers={"Authorization":"Bearer "+replacement},ssl=ctx) as res:
                assert res.status==200
                assert "rows" in json.loads((await res.content.readline())[6:])
                assert await restarted.config_entries.async_reload(entry.entry_id)
                assert not (await asyncio.wait_for(res.content.read(), 5)).strip()
            async with client.get(prefix+"/events",headers={"Authorization":"Bearer "+replacement},ssl=ctx) as res:
                assert res.status==200 and "rows" in json.loads((await res.content.readline())[6:])
                restored=restarted.config_entries.async_get_entry(entry.entry_id)
                restarted.config_entries.async_update_entry(restored,data={})
                await asyncio.wait_for(res.content.read(),5)
        print("HA restart: configuration, rotated credential and new snapshot verified.")
    finally:
        await restarted.async_stop()

if __name__=="__main__":
    for lan in (False,True):
        with tempfile.TemporaryDirectory() as temp:asyncio.run(test(Path(temp),lan))
