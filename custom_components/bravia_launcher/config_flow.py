"""Configure a named television, its projection and revocable credentials."""
import re
from uuid import uuid4
from .cards import COLORS, OPERATIONS, SCREENS, TEMPLATES
from datetime import datetime, timezone
import voluptuous as vol
from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import selector
from homeassistant.components.network import async_get_source_ip
from .transport import private_ip
from .const import DOMAIN
from .projection import identity, resolve

class BraviaFlow(config_entries.ConfigFlow, domain=DOMAIN):
    VERSION = 1

    async def async_step_user(self,user_input=None):
        if user_input is not None:
            return self.async_create_entry(title=user_input["name"],data={})
        return self.async_show_form(step_id="user", data_schema=vol.Schema({vol.Required("name"):str}))

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return Options()

class Options(config_entries.OptionsFlow):
    async def async_step_init(self,user_input=None):
        return self.async_show_menu(step_id="init",menu_options=["designer","layout","transport","cards","pair","revoke","status"])

    async def async_step_layout(self,user_input=None):
        opts=self.config_entry.options
        if user_input is not None:
            return self.async_create_entry(title="",data={**opts,"layout":user_input})
        layout=opts.get("layout",{})
        choices=SCREENS+["card:"+k for k,c in opts.get("designer",{}).items() if c.get("visible",True)]
        def choice(values):
            return selector.SelectSelector(selector.SelectSelectorConfig(options=[
                {"value":v,"label":opts.get("designer",{}).get(v[5:],{}).get("title",v) if v.startswith("card:") else v} for v in values]))
        fields={vol.Required("home",default=layout.get("home","Избранное") if layout.get("home") in choices else "Избранное"):choice(choices),
                vol.Required("live",default=layout.get("live",False)):bool,
                vol.Required("safety",default=layout.get("safety",True)):bool,
                vol.Required("hide_lights",default=layout.get("hide_lights",False)):bool}
        for color in COLORS:
            old=layout.get(color,"—")
            fields[vol.Required(color,default=old if old in choices else "—")]=choice(["—",*choices])
        return self.async_show_form(step_id="layout",data_schema=vol.Schema(fields))

    async def async_step_designer(self,user_input=None):
        cards=self.config_entry.options.get("designer",{})
        if user_input is not None:
            self.card_id=user_input["card"]
            return await self.async_step_edit()
        choices=[{"value":"new","label":"+"}]+[{"value":k,"label":v["title"]} for k,v in cards.items()]
        return self.async_show_form(step_id="designer",data_schema=vol.Schema({vol.Required("card",default="new"):
            selector.SelectSelector(selector.SelectSelectorConfig(options=choices))}))

    async def async_step_edit(self,user_input=None):
        opts=self.config_entry.options
        cards=dict(opts.get("designer",{}))
        old=cards.get(self.card_id,{})
        errors={}
        if user_input is not None:
            data=dict(user_input)
            eid=data.pop("entity",None)
            template=data["template"]
            domain=eid.split(".",1)[0] if eid else ""
            operation=data["operation"]
            if data.pop("delete",False):
                cards.pop(self.card_id,None)
            elif template not in ("link","active") and (not eid or self.hass.states.get(eid) is None):
                errors["entity"]="invalid_entity"
            elif template in ("light","vacuum","scene","script") and template!=domain:
                errors["entity"]="invalid_entity"
            elif operation!="—" and not (operation=="toggle" and domain=="light" and template=="light") and (
                    template!=domain or operation not in OPERATIONS.get(domain,set())):
                errors["operation"]="invalid_operation"
            else:
                data["target"]=identity(self.hass,eid) if eid else ""
                cards[self.card_id if self.card_id!="new" else uuid4().hex]=data
            if not errors:
                return self.async_create_entry(title="",data={**opts,"designer":cards})
        def select(values):
            return selector.SelectSelector(selector.SelectSelectorConfig(options=values))
        fields={}
        for key,values,default in (("template",TEMPLATES,"sensor"),("group",SCREENS[:9],"Датчики"),
                ("operation",["—","toggle","turn_on","start","pause","return_to_base"],"—"),
                ("link",SCREENS+["HDMI "+str(i) for i in range(1,5)],"Избранное")):
            fields[vol.Required(key,default=old.get(key,default))]=select(values)
        fields[vol.Required("title",default=old.get("title",""))]=vol.All(str,vol.Length(min=1,max=80))
        value=resolve(self.hass,old["target"]) if old.get("target") else None
        fields[vol.Optional("entity",description={"suggested_value":value} if value else {})]=selector.EntitySelector()
        fields[vol.Optional("icon",description={"suggested_value":old.get("icon","")})]=str
        fields[vol.Optional("active_states",description={"suggested_value":old.get("active_states","")})]=str
        for k,default in (("order",50),("delay",0),("duration",300)):
            fields[vol.Required(k,default=old.get(k,default))]=vol.All(vol.Coerce(int),vol.Range(min=0,max=86400))
        for k,default in (("visible",True),("confirm",True),("event",False),("warn",False),("delete",False)):
            fields[vol.Required(k,default=old.get(k,default))]=bool
        return self.async_show_form(step_id="edit",data_schema=vol.Schema(fields),errors=errors)

    async def async_step_transport(self,user_input=None):
        options=self.config_entry.options
        errors={}
        local=await async_get_source_ip(self.hass)
        suggested=local.rsplit(".",1)[0]+".40" if private_ip(local) else ""
        if user_input is not None:
            if user_input["lan_http"] and not private_ip(user_input["tv_ip"]):
                errors["tv_ip"]="invalid_ip"
            else:
                if any(options.get(k, default)!=user_input[k] for k,default in (("lan_http",False),("tv_ip",suggested))):
                    self.hass.data[DOMAIN]["pairing"].codes.pop(self.config_entry.entry_id,None)
                    self.hass.config_entries.async_update_entry(self.config_entry,data={})
                return self.async_create_entry(title="",data={**options,**user_input})
        fields={vol.Required("lan_http",default=options.get("lan_http",False)):bool,
                vol.Required("tv_ip",default=options.get("tv_ip",suggested)):str}
        return self.async_show_form(step_id="transport",data_schema=vol.Schema(fields),errors=errors)

    async def async_step_cards(self,user_input=None):
        options=self.config_entry.options
        errors={}
        if user_input is not None:
            path=user_input.get("dashboard","").strip()
            if path and not re.fullmatch(r"/[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*",path):
                errors["dashboard"]="invalid_path"
            else:
                data={**{k:options[k] for k in ("tv_ip","lan_http","layout","designer") if k in options},**user_input,"dashboard":path}
                for field in ("entities","favorites","hidden","control"):
                    data[field]=[identity(self.hass,eid) for eid in data.get(field,[])]
                return self.async_create_entry(title="",data=data)
        fields={vol.Optional("areas",description={"suggested_value": options.get("areas",[])}):selector.AreaSelector(selector.AreaSelectorConfig(multiple=True))}
        for field in ("entities","favorites","hidden","control"):
            values=[eid for key in options.get(field,[]) if (eid:=resolve(self.hass,key))]
            config={"multiple":True}
            if field=="control":
                config["filter"]={"domain":["light","switch"]}
            fields[vol.Optional(field,description={"suggested_value": values})]=selector.EntitySelector(selector.EntitySelectorConfig(**config))
        fields[vol.Optional("appearance",default=options.get("appearance","TV"))]=selector.SelectSelector(
            selector.SelectSelectorConfig(options=["TV","Aurora Glass","Midnight Minimal","Warm Ambient"]))
        fields[vol.Optional("dashboard",description={"suggested_value": options.get("dashboard","")})]=str
        return self.async_show_form(step_id="cards",data_schema=vol.Schema(fields),errors=errors)

    async def async_step_pair(self,user_input=None):
        if user_input is not None:
            return self.async_abort(reason="done")
        code=self.hass.data[DOMAIN]["pairing"].issue(self.config_entry.entry_id)
        return self.async_show_form(step_id="pair",data_schema=vol.Schema({}),description_placeholders={"code":code})

    async def async_step_revoke(self,user_input=None):
        if user_input is not None:
            self.hass.data[DOMAIN]["pairing"].codes.pop(self.config_entry.entry_id,None)
            self.hass.config_entries.async_update_entry(self.config_entry,data={})
            self.hass.data[DOMAIN]["entries"][self.config_entry.entry_id]["wake"].set()
            return self.async_abort(reason="revoked")
        return self.async_show_form(step_id="revoke",data_schema=vol.Schema({}))

    async def async_step_status(self,user_input=None):
        if user_input is not None:
            return self.async_abort(reason="done")
        state=self.hass.data[DOMAIN]["entries"][self.config_entry.entry_id]
        last=datetime.fromtimestamp(state["last"],timezone.utc).isoformat() if state["last"] else "—"
        return self.async_show_form(step_id="status",data_schema=vol.Schema({}),description_placeholders={"live":"●" if state["connected"] else "○","last":last})
