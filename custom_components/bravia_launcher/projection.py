"""Project explicitly selected entities and areas into the legacy native-card schema."""
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
                           features=a.get("supported_features",0),battery=a.get("battery_level"),
                           controllable=s.domain in ("light","switch") and key in options.get("control",[]))
    return result
