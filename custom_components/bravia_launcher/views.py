"""Scoped HTTPS endpoints; no HA service name, service data or target supplied by the TV."""
import asyncio
from collections import deque
import hmac
import json
import math
import secrets
import time

from aiohttp import web
from homeassistant.components.http import HomeAssistantView, KEY_HASS
from .const import DOMAIN, PREFIX
from .credentials import digest
from .projection import rows

def runtime(request):
    if not request.secure:
        raise web.HTTPForbidden(text="HTTPS required")
    return request.app[KEY_HASS]

def authorize(hass, token):
    if not token or len(token) > 128:
        raise web.HTTPUnauthorized()
    value = digest(token)
    for entry in hass.config_entries.async_entries(DOMAIN):
        if entry.entry_id in hass.data[DOMAIN]["entries"] and hmac.compare_digest(entry.data.get("digest",""),value):
            return entry
    raise web.HTTPUnauthorized()

def bearer(request):
    header = request.headers.get("Authorization","")
    return header[7:] if header.startswith("Bearer ") else ""

async def body(request):
    if request.content_type != "application/json" or request.content_length is None or request.content_length > 2048:
        raise web.HTTPBadRequest()
    try:
        data = await request.json()
    except ValueError as exc:
        raise web.HTTPBadRequest() from exc
    if not isinstance(data, dict):
        raise web.HTTPBadRequest()
    return data

class Pair(HomeAssistantView):
    url, name, requires_auth = PREFIX+"/pair", DOMAIN+":pair", False

    async def post(self, request):
        hass = runtime(request)
        data = await body(request)
        code = data.get("code")
        if not isinstance(code,str) or len(code)!=8 or not code.isascii() or not code.isdigit():
            raise web.HTTPBadRequest()
        try:
            key = hass.data[DOMAIN]["pairing"].redeem(code)
        except PermissionError as exc:
            raise web.HTTPTooManyRequests() from exc
        if not key or not (entry := hass.config_entries.async_get_entry(key)):
            raise web.HTTPUnauthorized()
        token = secrets.token_urlsafe(32)
        hass.config_entries.async_update_entry(entry, data={"digest":digest(token)})
        hass.data[DOMAIN]["entries"][key]["wake"].set()
        return web.json_response({"token":token},headers={"Cache-Control":"no-store"})

class Events(HomeAssistantView):
    url, name, requires_auth = PREFIX+"/events", DOMAIN+":events", False

    async def get(self, request):
        hass = runtime(request)
        token = bearer(request)
        entry = authorize(hass, token)
        state = hass.data[DOMAIN]["entries"][entry.entry_id]
        if state["connected"]:
            raise web.HTTPConflict()
        state["connected"] = True
        stream = web.StreamResponse(headers={"Content-Type":"text/event-stream","Cache-Control":"no-store","X-Accel-Buffering":"no"})
        before = None
        try:
            await stream.prepare(request)
            while hass.data[DOMAIN]["entries"].get(entry.entry_id) is state:
                state["wake"].clear()
                try:
                    authorize(hass, token)
                except web.HTTPUnauthorized:
                    break
                now = int(time.time())
                current = rows(hass, entry.options)
                state["last"] = now
                state["watch"] = {row["eid"] for row in current.values()}
                points = state.setdefault("history",{})
                for key in list(points):
                    if key not in current:
                        del points[key]
                for key, row in current.items():
                    if row["dc"] not in ("humidity","illuminance","temperature"):
                        continue
                    try:
                        value = float(row["value"])
                    except ValueError:
                        continue
                    if not math.isfinite(value):
                        continue
                    samples = points.setdefault(key,deque(maxlen=288))
                    if samples and row["unit"] != samples[-1][2]:
                        samples.clear()
                    if not samples or now//300 != samples[-1][0]//300:
                        samples.append((now,value,row["unit"]))
                settings = (entry.options.get("dashboard","").strip("/"),entry.options.get("favorites",[]),entry.options.get("appearance","TV"))
                data = dict(live=True,stamp=now,dashboard=settings[0],favorites=settings[1],appearance=settings[2])
                if before is None:
                    data["rows"] = list(current.values())
                else:
                    data.update(upsert=[r for k,r in current.items() if before.get(k)!=r],
                                remove=list(before.keys()-current.keys()))
                changed = before is None or data.get("upsert") or data.get("remove") or settings != previous_settings
                payload = "data: "+json.dumps(data,ensure_ascii=False,separators=(",",":"))+"\n\n" if changed else ": alive\n\n"
                await asyncio.wait_for(stream.write(payload.encode()),10)
                before, previous_settings = current, settings
                while not state["wake"].is_set():
                    try:
                        await asyncio.wait_for(state["wake"].wait(),15)
                    except TimeoutError:
                        await asyncio.wait_for(stream.write(b": alive\n\n"),10)
                        state["last"] = int(time.time())
                await asyncio.sleep(0.35)
        except (ConnectionError,TimeoutError):
            pass
        finally:
            state["connected"] = False
            if hass.data[DOMAIN]["entries"].get(entry.entry_id) is state and state["last"]:
                hass.config_entries.async_update_entry(entry, data={**entry.data, "last": state["last"]})
        return stream

class History(HomeAssistantView):
    url, name, requires_auth = PREFIX+"/history", DOMAIN+":history", False
    async def get(self,request):
        hass=runtime(request)
        entry=authorize(hass,bearer(request))
        key=request.query.get("key","")
        if key not in rows(hass,entry.options):
            raise web.HTTPForbidden()
        samples=hass.data[DOMAIN]["entries"][entry.entry_id].get("history",{}).get(key,())
        return web.json_response([[t,v] for t,v,_ in samples if t>=time.time()-86400])

class Action(HomeAssistantView):
    url, name, requires_auth = PREFIX+"/action", DOMAIN+":action", False
    async def post(self,request):
        hass=runtime(request)
        data=await body(request)
        entry=authorize(hass,bearer(request))
        key,action=data.get("key"),data.get("action")
        if not isinstance(key,str) or action not in ("turn_on","turn_off"):
            raise web.HTTPBadRequest()
        row=rows(hass,entry.options).get(key)
        if not row or not row["controllable"] or not row["available"]:
            raise web.HTTPForbidden()
        await hass.services.async_call(row["domain"],action,{"entity_id":row["eid"]},blocking=True)
        return web.json_response({"ok":True})

def register(hass):
    for view in (Pair,Events,History,Action):
        hass.http.register_view(view)
