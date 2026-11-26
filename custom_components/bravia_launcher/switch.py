"""Persistent per-TV switch for household events."""
from homeassistant.components.switch import SwitchEntity
from homeassistant.core import callback


async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([LiveEvents(entry)])


class LiveEvents(SwitchEntity):
    _attr_should_poll = False

    def __init__(self, entry):
        self.entry = entry
        self._attr_name = f"{entry.title} · Live Activities"
        self._attr_unique_id = f"{entry.entry_id}_live_events"

    @property
    def is_on(self):
        return self.entry.options.get("layout", {}).get("live", False)

    async def async_added_to_hass(self):
        async def updated(hass, entry):
            self.async_write_ha_state()
        self.async_on_remove(self.entry.add_update_listener(updated))

    @callback
    def _set(self, value):
        options = dict(self.entry.options)
        options["layout"] = {**options.get("layout", {}), "live": value}
        self.hass.config_entries.async_update_entry(self.entry, options=options)

    async def async_turn_on(self, **kwargs):
        self._set(True)

    async def async_turn_off(self, **kwargs):
        self._set(False)
