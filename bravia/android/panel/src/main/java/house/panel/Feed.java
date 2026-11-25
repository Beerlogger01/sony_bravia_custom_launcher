package house.panel;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

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
    long stamp;
    final Set<Listener> listeners = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;
    private volatile HttpsURLConnection active;
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
        HttpsURLConnection old=active;
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
        int id = generation;
        worker = new Thread(() -> stream(id,url),"bravia-state");
        worker.start();
    }
    void notifyListeners() { for (Listener l:new ArrayList<>(listeners)) l.changed(); }
    private void status(int id,String message) {
        main.post(() -> { if(id==generation && !status.equals(message)){ status=message; notifyListeners(); } });
    }
    private void stream(int id,String endpoint) {
        int delay=1000;
        while (id==generation) {
            HttpsURLConnection c=null;
            try {
                c=(HttpsURLConnection)new URL(endpoint+"/v1/events").openConnection();
                active=c;
                if(id!=generation)return;
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(35000);
                c.setRequestProperty("Authorization","Bearer "+Vault.open(prefs.getString("secret","")));
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
            } catch(Exception e) { /* Never log credentials or household states. */ }
            finally { if(c!=null)c.disconnect(); if(active==c)active=null; }
            status(id,"Нет связи");
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
        return Arrays.asList("unavailable","unknown","error").contains(r.optString("value"))
            || (Arrays.asList("problem","smoke","gas","safety","tamper","battery").contains(r.optString("dc")) && "on".equals(r.optString("value")))
            || ("connectivity".equals(r.optString("dc")) && "off".equals(r.optString("value")));
    }
    static String reading(JSONObject r) {
        String val=r.optString("value");
        switch(val) {
            case "cleaning": val="Убирает"; break;
            case "returning": val="На базу"; break;
            case "docked": val="На базе"; break;
            case "charging": val="Заряжается"; break;
            case "error": val="Ошибка"; break;
            case "unavailable": val="Недоступно"; break;
            case "unknown": val="Неизвестно"; break;
            case "on": val="Вкл"; break;
            case "off": val="Выкл"; break;
        }
        return val+" "+r.optString("unit");
    }
    String[] featured() {
        ArrayList<String> result=new ArrayList<>();
        Map<String,JSONObject> byKey=new HashMap<>();
        for(int i=0;i<rows.length();i++){ JSONObject r=rows.optJSONObject(i); byKey.put(r.optString("key"),r); }
        for(String key:pins()){
            JSONObject r=byKey.get(key);
            if(r==null || problem(r))continue;
            result.add(r.optString("title")); result.add(reading(r).trim());
            if(result.size()==6)break;
        }
        return result.toArray(new String[0]);
    }
    String[] summary() {
        int lights=0,issues=0; String robot="—"; ArrayList<String> active=new ArrayList<>();
        Map<String,JSONObject> byKey=new HashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);
            byKey.put(r.optString("key"),r);
            if(problem(r)) issues++;
            if("light".equals(r.optString("domain")) && "on".equals(r.optString("value"))) lights++;
            if("vacuum".equals(r.optString("domain"))) robot=reading(r);
            if("Техника".equals(r.optString("section")) && Arrays.asList("on","running","cleaning","playing").contains(r.optString("value")) && active.size()<2) active.add(r.optString("title"));
        }
        List<String> favorites=new ArrayList<>();
        for(String key:pins()) {
            JSONObject r=byKey.get(key);
            if(r!=null && !problem(r) && favorites.size()<3) favorites.add(r.optString("title")+"  "+reading(r));
        }
        return new String[]{ageLabel(), stamp==0?"—":String.valueOf(lights),robot,stamp==0?"—":String.valueOf(issues),
            android.text.TextUtils.join("     ·     ",favorites),android.text.TextUtils.join(" · ",active)};
    }
}
