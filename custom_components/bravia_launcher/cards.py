"""TV card policy. Only operations in this table can reach the HA service bus."""
from datetime import datetime, timezone

SCREENS = ["Избранное","Свет","Сейчас активно","Сценарии","Датчики","Техника","Климат","Система","Проблемы","Недоступные","dashboard"]
TEMPLATES = ["light","sensor","device","active","vacuum","script","scene","link","info"]
OPERATIONS = {"script":{"turn_on"}, "scene":{"turn_on"}, "vacuum":{"start","pause","return_to_base"}}
COLORS = ["red","green","yellow","blue"]

def classify(state):
    value, dc = state.state, state.attributes.get("device_class")
    if value == "unavailable":
        return "unavailable"
    if value == "unknown":
        return "unknown"
    if state.domain == "binary_sensor" and dc in ("smoke","gas","safety","moisture","carbon_monoxide") and value == "on":
        return "safety"
    if (state.domain == "binary_sensor" and dc == "problem" and value == "on") or (state.domain == "vacuum" and value == "error"):
        return "fault"
    if state.domain == "binary_sensor" and ((dc in ("battery","tamper","door","opening","window") and value=="on") or (dc=="connectivity" and value=="off")):
        return "warning"
    return "normal"

def active(state, override):
    if state.state in ("unknown","unavailable"):
        return False
    if override:
        return state.state in [v.strip() for v in override.split(",")]
    return state.state in {"light":{"on"},"vacuum":{"cleaning"},"media_player":{"playing"}}.get(state.domain,set()) or (
        state.domain in ("climate","humidifier") and state.attributes.get("hvac_action",state.attributes.get("action")) in ("heating","cooling","humidifying","drying"))

def age(state):
    return max(0,(datetime.now(timezone.utc)-state.last_changed).total_seconds())
