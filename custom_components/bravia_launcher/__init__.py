"""Bravia Launcher integration. One entry and one scoped credential per television."""
import asyncio
from homeassistant.core import callback
from .const import DOMAIN
from .credentials import Pairing
from .views import register

async def async_setup(hass, config):
    hass.data[DOMAIN] = {"entries": {}, "pairing": Pairing()}
    register(hass)
    return True

async def async_setup_entry(hass, entry):
    state = {"wake": asyncio.Event(), "connected": False, "last": entry.data.get("last"), "watch": set()}
    hass.data[DOMAIN]["entries"][entry.entry_id] = state
    @callback
    def notify(event):
        if not state["connected"]:
            return
        if event.event_type == "state_changed":
            eid = event.data.get("entity_id")
            if eid not in state["watch"] and event.data.get("old_state") is not None:
                return
        state["wake"].set()
    for name in ("state_changed", "entity_registry_updated", "device_registry_updated", "area_registry_updated"):
        entry.async_on_unload(hass.bus.async_listen(name, notify))
    async def changed(hass, entry):
        state["wake"].set()
    entry.async_on_unload(entry.add_update_listener(changed))
    await hass.config_entries.async_forward_entry_setups(entry, ["switch"])
    return True

async def async_unload_entry(hass, entry):
    if not await hass.config_entries.async_unload_platforms(entry, ["switch"]):
        return False
    data = hass.data[DOMAIN]
    data["pairing"].codes.pop(entry.entry_id, None)
    data["entries"].pop(entry.entry_id)["wake"].set()
    return True
