package house.panel;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.HttpURLConnection;

/** All model/listener changes run on the main looper. One interruptible stream per foreground session. */
final class Feed {
    interface Listener { void changed(); }
    private static Feed instance;
    static Feed get(Context c) {
        if (instance == null) instance = new Feed(c.getApplicationContext());
        return instance;
    }
    final SharedPreferences prefs;
    JSONArray rows = new JSONArray();
    String status = "Нет связи", dashboard = "";
    JSONObject layout=new JSONObject();
    long stamp;
    final Set<Listener> listeners = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;
    private volatile HttpURLConnection active;
    private boolean cachePending;
    private final Runnable persist = () -> saveSnapshot();
    private void saveSnapshot(){
        if(!cachePending)return;
        cachePending=false;
        prefs.edit().putString("rows",rows.toString()).putLong("stamp",stamp).putString("path",dashboard).apply();
    }
    private volatile int generation;
    private Feed(Context c) {
        prefs = c.getSharedPreferences("panel",0);
        try { rows = new JSONArray(prefs.getString("rows","[]")); } catch (JSONException ignored) {}
        try {layout=new JSONObject(prefs.getString("layout","{}"));}catch(JSONException ignored){}
        stamp = prefs.getLong("stamp",0);
        dashboard = prefs.getString("path","");
    }
    void add(Listener l) {
        if (listeners.add(l) && listeners.size()==1) restart();
        l.changed();
    }
    void remove(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) stop();
    }
    void stop() {
        generation++;
        main.removeCallbacks(persist);
        saveSnapshot();
        HttpURLConnection old=active;
        if(old!=null)new Thread(old::disconnect,"bravia-close").start();
        if (worker != null) worker.interrupt();
        worker = null;
        status = "Нет связи";
    }
    void restart() {
        stop();
        if (listeners.isEmpty()) return;
        String url = prefs.getString("bridge","");
        if (url.isEmpty()) { status="Настройте подключение"; notifyListeners(); return; }
        status="Подключение…";notifyListeners();
        int id = generation;
        String sealed=prefs.getString("secret","");
        worker = new Thread(() -> stream(id,url,sealed),"bravia-state");
        worker.start();
    }
    void notifyListeners() { for (Listener l:new ArrayList<>(listeners)) l.changed(); }
    private void status(int id,String message) {
        main.post(() -> { if(id==generation && !status.equals(message)){ status=message; notifyListeners(); } });
    }
    private void stream(int id,String endpoint,String sealed) {
        int delay=1000;
        while (id==generation) {
            HttpURLConnection c=null;
            String failure="Нет связи";
            try {
                c=TvApi.open(endpoint+"/v1/events");
                active=c;
                if(id!=generation)return;
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(35000);
                c.setRequestProperty("Authorization","Bearer "+Vault.open(sealed));
                int code=c.getResponseCode();
                if(code==401 || code==403) { status(id,"Ключ отклонён"); return; }
                if(code!=200) throw new IOException();
                try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))) {
                    String line;
                    while(id==generation && (line=in.readLine())!=null) {
                        if(!line.startsWith("data: ")) continue;
                        JSONObject data=new JSONObject(line.substring(6));
                        delay=1000;
                        main.post(() -> accept(id,data));
                    }
                }
            } catch(javax.net.ssl.SSLHandshakeException e) { failure="Проверьте сертификат TLS"; }
            catch(java.net.ConnectException e){failure="HA не отвечает";}
            catch(java.net.SocketTimeoutException e){failure="Таймаут HA";}
            catch(Exception e){failure="Поток SSE прерван";}
            finally { if(c!=null)c.disconnect(); if(active==c)active=null; }
            status(id,failure);
            try { Thread.sleep(delay); } catch(InterruptedException e){ return; }
            delay=Math.min(60000,delay*2);
        }
    }
    void accept(int id,JSONObject data) {
        if(id!=generation) return;
        status=data.optBoolean("live")?"В сети":data.optString("reason","");
        if(status.isEmpty())status="HA недоступен";
        if(data.optLong("stamp")>0 && data.has("upsert")) {
            LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
            for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);merged.put(r.optString("key"),r);}
            JSONArray gone=data.optJSONArray("remove"), added=data.optJSONArray("upsert");
            for(int i=0;i<gone.length();i++)merged.remove(gone.optString(i));
            for(int i=0;i<added.length();i++){JSONObject r=added.optJSONObject(i);merged.put(r.optString("key"),r);}
            try {data.put("rows",new JSONArray(merged.values()));} catch(JSONException ignored){}
        }
        if(data.optLong("stamp")>0 && data.optJSONArray("rows")!=null) {
            JSONArray updated=data.optJSONArray("rows");
            if(data.optBoolean("live") && stamp>0){
                Set<String> old=new HashSet<>(), now=new HashSet<>();
                for(int i=0;i<rows.length();i++)old.add(rows.optJSONObject(i).optString("key"));
                for(int i=0;i<updated.length();i++)now.add(updated.optJSONObject(i).optString("key"));
                Set<String> fresh=new HashSet<>(prefs.getStringSet("new",Collections.emptySet()));
                Set<String> before=new HashSet<>(fresh);
                fresh.retainAll(now);now.removeAll(old);fresh.addAll(now);
                if(!fresh.equals(before))prefs.edit().putStringSet("new",fresh).apply();
            }
            String appearance=data.optString("appearance","TV");
            if(!appearance.equals(prefs.getString("appearance","TV")))prefs.edit().putString("appearance",appearance).apply();
            JSONObject options=data.optJSONObject("layout");
            if(options==null)options=new JSONObject();
            if(!options.toString().equals(layout.toString()))prefs.edit().putString("layout",options.toString()).apply();
            layout=options;
            JSONArray remote=data.optJSONArray("favorites");
            if(remote!=null && !remote.toString().equals(prefs.getString("remotePins","[]"))){
                prefs.edit().putString("remotePins",remote.toString()).putString("pins",remote.toString()).apply();
            }
            rows=updated;
            stamp=data.optLong("stamp");
            dashboard=data.optString("dashboard");
            if(!cachePending){cachePending=true;main.postDelayed(persist,10000);}
        }
        notifyListeners();
    }
    ArrayList<String> pins() {
        ArrayList<String> result=new ArrayList<>();
        try { JSONArray a=new JSONArray(prefs.getString("pins","[]"));
            for(int i=0;i<a.length();i++) result.add(a.getString(i));
        } catch(JSONException ignored) {}
        return result;
    }
    void acknowledge(String key) {
        Set<String> fresh=new HashSet<>(prefs.getStringSet("new",Collections.emptySet()));
        if(fresh.remove(key))prefs.edit().putStringSet("new",fresh).apply();
    }
    void savePins(List<String> order) {
        prefs.edit().putString("pins",new JSONArray(order).toString()).apply();
        notifyListeners();
    }
    String ageLabel() {
        if(status.equals("В сети")) return status;
        return status+(stamp>0?" · устарело "+new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(stamp*1000)):"");
    }
    static boolean problem(JSONObject r) {
        if(r.has("health"))return Arrays.asList("warning","fault","safety").contains(r.optString("health"));
        return "error".equals(r.optString("value"))
            || (Arrays.asList("problem","smoke","gas","safety","tamper","battery","door","window","opening","garage_door","moisture").contains(r.optString("dc")) && "on".equals(r.optString("value")))
            || ("connectivity".equals(r.optString("dc")) && "off".equals(r.optString("value")));
    }
    static String reading(JSONObject r) {
        String val=r.optString("value");
        switch(val) {
            case "cleaning": val="Убирает"; break;
            case "returning": val="На базу"; break;
            case "docked": val="На базе"; break;
            case "idle": val="Ожидание"; break;
            case "charging": val="Заряжается"; break;
            case "error": val="Ошибка"; break;
            case "unavailable": val="Недоступно"; break;
            case "unknown": val="Неизвестно"; break;
            case "on": val="Вкл"; break;
            case "off": val="Выкл"; break;
        }
        int battery=r.optInt("battery",-1);
        if("vacuum".equals(r.optString("domain")) && battery>=0 && battery<=100)
            return val+" · "+battery+"%";
        return val+" "+r.optString("unit");
    }
    String[] featured() {
        ArrayList<String> result=new ArrayList<>();
        Map<String,JSONObject> byKey=new HashMap<>();
        for(int i=0;i<rows.length();i++){ JSONObject r=rows.optJSONObject(i); byKey.put(r.optString("key"),r); }
        for(String key:pins()){
            JSONObject r=byKey.get(key);
            if(r==null || problem(r) || Arrays.asList("unknown","unavailable").contains(r.optString("value")))continue;
            result.add(r.optString("title")); result.add(reading(r).trim());
            if(result.size()==6)break;
        }
        return result.toArray(new String[0]);
    }
    String event(){
        if(!layout.optBoolean("live") || !"В сети".equals(status))return "";
        JSONObject best=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row.optBoolean("event") && (best==null || row.optInt("order")<best.optInt("order")))best=row;
        }
        return best==null?"":best.optString("title")+"  ·  "+reading(best).trim();
    }
    String[] summary() {
        Set<String> lit=new HashSet<>(),problems=new HashSet<>(); String robot="—"; ArrayList<String> active=new ArrayList<>();
        Map<String,JSONObject> byKey=new HashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);
            byKey.put(r.optString("key"),r);
            if(problem(r) && (!"safety".equals(r.optString("health")) || layout.optBoolean("safety",true))) problems.add(r.optString("eid"));
            if("light".equals(r.optString("domain")) && "on".equals(r.optString("value"))) lit.add(r.optString("eid"));
            if("vacuum".equals(r.optString("domain"))) robot=reading(r);
            if(r.optBoolean("active") && active.size()<2) active.add(r.optString("title"));
        }
        List<String> favorites=new ArrayList<>();
        for(String key:pins()) {
            JSONObject r=byKey.get(key);
            if(r!=null && !problem(r) && !Arrays.asList("unknown","unavailable").contains(r.optString("value")) && favorites.size()<3) favorites.add(r.optString("title")+"  "+reading(r));
        }
        return new String[]{ageLabel(), stamp==0?"—":String.valueOf(lit.size()),robot,!"В сети".equals(status)?"—":String.valueOf(problems.size()),
            android.text.TextUtils.join("     ·     ",favorites),android.text.TextUtils.join(" · ",active)};
    }
}
