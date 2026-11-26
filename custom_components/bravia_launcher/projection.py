"""Project explicitly selected entities and areas into the legacy native-card schema."""
from .cards import active, age, classify, OPERATIONS
from homeassistant.helpers import area_registry as ar, device_registry as dr, entity_registry as er

def identity(hass, eid):
    entry = er.async_get(hass).async_get(eid)
    return entry.id if entry else "entity:" + eid

def resolve(hass, key):
    if key.startswith("entity:"):
        return key[7:]
    entry = er.async_get(hass).async_get(key)
    return entry.entity_id if entry else None

def rows(hass, options):
    entities, devices, areas = er.async_get(hass), dr.async_get(hass), ar.async_get(hass)
    selected = dict.fromkeys(resolve(hass, key) for key in options.get("entities", []))
    for area in options.get("areas", []):
        selected.update(dict.fromkeys(e.entity_id for e in er.async_entries_for_area(entities, area)))
        for device in dr.async_entries_for_area(devices, area):
            selected.update(dict.fromkeys(e.entity_id for e in er.async_entries_for_device(entities, device.id)
                                          if not e.area_id or e.area_id == area))
    original=set(selected)
    cards=options.get("designer",{})
    for card in cards.values():
        if card.get("visible") and card.get("target"):
            selected.setdefault(resolve(hass,card["target"]),None)
    result = {}
    for eid in selected:
        if not eid or not (s := hass.states.get(eid)):
            continue
        e = entities.async_get(eid)
        if e and (e.disabled_by or e.hidden_by):
            continue
        key = e.id if e else "entity:" + eid
        if key in options.get("hidden", []):
            continue
        dev = devices.async_get(e.device_id) if e and e.device_id else None
        area_id = (e.area_id if e else None) or (dev.area_id if dev else None)
        area = areas.async_get_area(area_id) if area_id else None
        a = s.attributes
        dc = str(a.get("device_class", ""))
        section = {"light":"Свет","vacuum":"Техника","switch":"Техника","humidifier":"Климат","climate":"Климат","binary_sensor":"Система"}.get(s.domain,"Датчики")
        result[key] = dict(key=key,eid=eid,domain=s.domain,title=str((e.name if e else None) or s.name)[:100],dc=dc,
                           value=s.state,unit=a.get("unit_of_measurement",""),area=area.name if area else "",
                           device=(dev.name_by_user or dev.name or "") if dev else "",section=section,
                           available=s.state not in ("unknown","unavailable"),changed=s.last_changed.isoformat(),
                           health=classify(s),active=active(s,""),features=a.get("supported_features",0),battery=a.get("battery_level"),
                           controllable=s.domain in ("light","switch") and key in options.get("control",[]))
    for cid,card in sorted(cards.items(),key=lambda item:item[1].get("order",50)):
        if not card.get("visible",True):
            continue
        target=card.get("target","")
        base=result.get(target)
        if not base and card["template"] not in ("link","active"):
            continue
        row=dict(base or {},key="card:"+cid,title=card["title"],section=card["group"],
                 icon=card.get("icon",""),template=card["template"],confirm=card.get("confirm",True),
                 link=card.get("link",""),controllable=False,operation="",order=card.get("order",50))
        if base:
            state=hass.states.get(base["eid"])
            elapsed=age(state)
            row["active"]=active(state,card.get("active_states"))
            if row["health"]=="unavailable" and card.get("warn") and elapsed>=card.get("delay",0):
                row["health"]="warning"
            markers=[v.strip() for v in card.get("event_states","").split(",") if v.strip()]
            trigger=state.state in markers if markers else row["active"]
            row["event"]=bool(options.get("layout",{}).get("live") and card.get("event") and row["available"] and trigger and elapsed<card.get("duration",300))
            deadline=None
            if row["event"]:
                eta_eid=resolve(hass,card["eta_key"]) if card.get("eta_key") else None
                eta=hass.states.get(eta_eid) if eta_eid else None
                if eta and eta.state not in ("unknown","unavailable") and eta.attributes.get("device_class") in ("duration","timestamp"):
                    row["eta"]=f"{eta.state} {eta.attributes.get('unit_of_measurement','')}".strip()
                deadline=state.last_changed.timestamp()+card.get("duration",300)
            if row["health"]=="unavailable" and card.get("warn") and elapsed<card.get("delay",0):
                deadline=state.last_changed.timestamp()+card["delay"]
            if deadline:
                row["refresh_at"]=deadline
            op=card.get("operation")
            dom=row["domain"]
            if op=="toggle" and dom=="light" and row["available"]:
                row.update(operation="toggle",controllable=True)
            elif op in OPERATIONS.get(dom,set()) and card["template"]==dom and hass.services.has_service(dom,op) and (
                    row["available"] or dom=="scene" and state.state=="unknown"):
                row.update(operation=op,available=True)
        else:
            row.update(available=True,domain="link",value="",eid="",dc="",unit="",active=False)
        result[row["key"]]=row
    return {k:r for k,r in result.items() if k.startswith("card:") or r["eid"] in original}
