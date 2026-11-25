"""Reduce HA compact state events to the TV's read-only projection."""
SECTIONS = {"light": "Свет", "switch": "Техника", "vacuum": "Техника",
            "humidifier": "Климат", "climate": "Климат", "media_player": "Техника"}
CLIMATE = {"humidity", "temperature", "illuminance", "moisture"}
FAULTS = {"problem", "smoke", "gas", "safety", "tamper"}
ATTRS = {"friendly_name", "device_class", "unit_of_measurement", "battery_level"}

class Catalog:
    def __init__(self):
        self.states = {}
        self.entities = {}
        self.devices = {}
        self.areas = {}

    def registry(self, kind, rows):
        key = {"entities":"entity_id", "devices":"id", "areas":"area_id"}[kind]
        setattr(self, kind, {row[key]: row for row in rows})

    def event(self, event):
        for eid in event.get("r", []):
            self.states.pop(eid, None)
        for eid, obj in event.get("a", {}).items():
            self.states[eid] = [obj.get("s", "unknown"),
                                {k:v for k,v in obj.get("a", {}).items() if k in ATTRS}]
        for eid, diff in event.get("c", {}).items():
            if eid not in self.states:
                continue
            state = self.states[eid]
            plus = diff.get("+", {})
            state[0] = plus.get("s", state[0])
            state[1].update({k:v for k,v in plus.get("a", {}).items() if k in ATTRS})
            for attr in diff.get("-", {}).get("a", []):
                state[1].pop(attr, None)

    def rows(self, overrides):
        rows = []
        for eid, (value, attrs) in self.states.items():
            reg = self.entities.get(eid, {})
            if reg.get("disabled_by") or reg.get("hidden_by"):
                continue
            device = self.devices.get(reg.get("device_id"), {})
            area = self.areas.get(reg.get("area_id") or device.get("area_id"), {}).get("name", "")
            domain = eid.partition(".")[0]
            dc = attrs.get("device_class", "")
            section = SECTIONS.get(domain)
            if domain == "sensor" and dc in CLIMATE:
                # Temperature alone cannot distinguish a room sensor from a fridge or CPU.
                section = "Датчики"
            if domain == "binary_sensor" and dc in FAULTS:
                section = "Система"
            if domain in ("sensor", "binary_sensor") and dc in ("running", "power", "enum"):
                section = "Техника"
            if domain == "sensor" and reg.get("platform") == "systemmonitor":
                section = "Система"
            if domain == "sensor" and section is None and isinstance(attrs.get("unit_of_measurement"), str):
                section = "Датчики"
            if domain == "binary_sensor" and dc in {"connectivity", "battery"}:
                section = "Система"
            key = reg.get("id") or "entity:" + eid
            section = overrides.get(key, overrides.get(eid, section))
            if not section:
                continue
            rows.append(dict(key=key, eid=eid, title=str(reg.get("name") or attrs.get("friendly_name") or eid)[:100],
                             value=str(value)[:80], area=area, device=device.get("name_by_user") or device.get("name") or "",
                             unit=attrs.get("unit_of_measurement") or "", dc=dc, section=section, domain=domain))
        return sorted(rows, key=lambda row: (row["area"], row["title"], row["eid"]))
