package house.panel;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class PanelActivity extends Activity implements Feed.Listener {
    private Feed feed;
    private TextView status;
    private ListView list;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> keys=new ArrayList<>();
    private final ArrayList<String> labels=new ArrayList<>();
    private final HashMap<String,JSONObject> catalog=new HashMap<>();
    private String section="";
    private static final String[] MENU={"Избранное","Климат","Техника","Система","Свет","Датчики","Проблемы",
        "Каталог · добавить на главную","Открыть Home Assistant","Kiosk · эксперимент","Подключение"};
    public void onCreate(Bundle state) {
        super.onCreate(state);
        feed=Feed.get(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pad=dp(28);
        box.setPadding(pad,pad,pad,pad); box.setBackgroundColor(0xff102126); setContentView(box);
        status=new TextView(this); status.setTextSize(23); status.setTextColor(0xff8ce2bc); box.addView(status);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>()){
            @Override public View getView(int position,View recycled,ViewGroup parent){
                TextView view=(TextView)super.getView(position,recycled,parent);view.setTextSize(19);return view;
            }
        };
        list=new ListView(this); list.setAdapter(adapter); box.addView(list);
        list.setOnItemClickListener((p,v,i,id)-> select(i));
        if(state!=null)section=state.getString("section","");
    }
    public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("section",section);}
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    public void onStart(){super.onStart();feed.add(this);}
    public void onStop(){feed.remove(this);super.onStop();}
    public void changed(){
        String heading=(section.isEmpty()?"Домашний дашборд":section)+"     /     "+feed.ageLabel();
        if(!heading.contentEquals(status.getText()))status.setText(heading);
        int pos=list.getSelectedItemPosition();
        String selected=pos>=0 && pos<keys.size()?keys.get(pos):"";
        catalog.clear();
        for(int i=0;i<feed.rows.length();i++){
            JSONObject row=feed.rows.optJSONObject(i); catalog.put(row.optString("key"),row);
        }
        ArrayList<String> next=new ArrayList<>(), ids=new ArrayList<>();
        if(section.isEmpty()) {Collections.addAll(next,MENU);ids.addAll(next);int count=feed.prefs.getStringSet("new",Collections.emptySet()).size();if(count>0)next.set(7,MENU[7]+"   ·   Новые: "+count);}
        else {
            List<String> pins=feed.pins();
            Iterable<String> source=section.equals(MENU[0])?pins:orderedKeys();
            for(String key:source){
                JSONObject r=catalog.get(key);
                if(r==null){ ids.add(key);next.add("Привязка отсутствует · "+key);continue;}
                boolean problem=Feed.problem(r);
                if(section.equals(MENU[6]) && !problem)continue;
                if(!(section.equals(MENU[7])||section.equals(MENU[0])||section.equals(MENU[6]))
                    && (!section.equals(feed.prefs.getString("section."+key,r.optString("section")))||problem))continue;
                ids.add(key);next.add((feed.prefs.getStringSet("new",Collections.emptySet()).contains(key)?"Новое   ":"")+(pins.contains(key)?"★   ":"")+r.optString("title")+"     "+Feed.reading(r)
                    +"     "+r.optString("area")+(section.equals(MENU[7])?"   ·   "+r.optString("device"):""));
            }
        }
        if(next.isEmpty()){next.add("Пока нет данных");ids.add("");}
        if(keys.equals(ids) && !next.equals(labels)){
            // List identity and focus stay stable while individual readings change.
            int first=list.getFirstVisiblePosition();
            for(int i=0;i<next.size();i++){
                if(next.get(i).equals(labels.get(i)))continue;
                View child=list.getChildAt(i-first);
                if(child instanceof TextView)((TextView)child).setText(next.get(i));
            }
            labels.clear();labels.addAll(next);
            adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(next);
        } else if(!keys.equals(ids)){
            labels.clear(); labels.addAll(next);keys.clear();keys.addAll(ids);
            adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(next);adapter.notifyDataSetChanged();
            int index=keys.indexOf(selected);list.setSelection(index>=0?index:Math.max(0,Math.min(pos,keys.size()-1)));
        }
    }
    private List<String> orderedKeys(){
        ArrayList<String> result=new ArrayList<>();
        for(int i=0;i<feed.rows.length();i++)result.add(feed.rows.optJSONObject(i).optString("key"));
        java.text.Collator collator=java.text.Collator.getInstance();
        result.sort((a,b)->{
            JSONObject left=catalog.get(a),right=catalog.get(b);
            for(String field:new String[]{"area","device","title"}){
                int order=collator.compare(left.optString(field),right.optString(field));
                if(order!=0)return order;
            }
            return a.compareTo(b);
        });
        return result;
    }
    private void select(int i){
        if(section.isEmpty()){
            if(i==10){settings();return;}
            if(i==8||i==9){dashboard(i==9);return;}
            section=MENU[i];changed();list.setSelection(0);return;
        }
        String key=keys.get(i); if(key.isEmpty())return;
        feed.acknowledge(key);
        ArrayList<String> pins=feed.pins(); int index=pins.indexOf(key);
        String[] choices=index<0?new String[]{"Закрепить"}:new String[]{"Открепить","Переместить выше","Переместить ниже"};
        JSONObject row=catalog.get(key);
        ArrayList<String> actions=new ArrayList<>(Arrays.asList(choices));
        if(row!=null && Arrays.asList("temperature","humidity","illuminance").contains(row.optString("dc"))) actions.add("История за 24 часа");
        if(row!=null)actions.add("Выбрать раздел");
        new AlertDialog.Builder(this).setTitle(row==null?key:row.optString("eid")).setItems(actions.toArray(new String[0]),(d,n)->{
            if(actions.get(n).equals("История за 24 часа")){
                startActivity(new Intent(this,Trend.class).putExtra("key",key).putExtra("unit",row.optString("unit")).putExtra("title",row.optString("title")));return;
            }
            if(actions.get(n).equals("Выбрать раздел")){
                String[] sections={"Климат","Техника","Система","Свет","Датчики"};
                new AlertDialog.Builder(this).setItems(sections,(a,j)->{feed.prefs.edit().putString("section."+key,sections[j]).apply();changed();}).show();return;
            }
            if(index<0)pins.add(key);
            else if(n==0)pins.remove(index);
            else Collections.swap(pins,index,Math.min(pins.size()-1,Math.max(0,index+(n==1?-1:1))));
            feed.savePins(pins);
        }).show();
    }
    private EditText field(LinearLayout box,String label,String value,boolean password){
        TextView title=new TextView(this);title.setText(label);box.addView(title);
        EditText input=new EditText(this);input.setSingleLine();
        if(password)input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(value);box.addView(input);return input;
    }
    private static String origin(String text,boolean httpsOnly){
        Uri u=Uri.parse(text.trim());
        if(u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null
            || !Arrays.asList("","/").contains(u.getPath()) || !("https".equals(u.getScheme())||(!httpsOnly && "http".equals(u.getScheme()))))throw new IllegalArgumentException();
        return u.getScheme()+"://"+u.getEncodedAuthority();
    }
    private void settings(){
        LinearLayout fields=new LinearLayout(this);fields.setPadding(dp(20),0,dp(20),0);fields.setOrientation(LinearLayout.VERTICAL);
        EditText bridge=field(fields,"HTTPS-адрес bridge",feed.prefs.getString("bridge",""),false);
        EditText key=field(fields,"Ключ телевизора · для нового адреса введите заново","",true);
        EditText ha=field(fields,"Адрес HA для браузера",feed.prefs.getString("browser",""),false);
        EditText path=field(fields,"Путь дашборда · пусто для автоопределения",feed.prefs.getString("manual",""),false);
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Подключение").setView(scroll)
            .setPositiveButton("Сохранить",null).setNegativeButton("Назад",null)
            .setNeutralButton("Забыть", (d,n)-> new AlertDialog.Builder(this).setMessage("Удалить ключ, кеш и избранное?")
                .setPositiveButton("Удалить",(a,b)->{try{Vault.clear();feed.stop();feed.prefs.edit().clear().commit();feed.rows=new JSONArray();feed.stamp=0;feed.dashboard="";feed.restart();}catch(Exception e){toast("Ошибка хранилища");}})
                .setNegativeButton("Назад",null).show()).create();
        dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{try{
            String url=origin(bridge.getText().toString(),true), rawKey=key.getText().toString();
            if(rawKey.isEmpty() && !url.equals(feed.prefs.getString("bridge","")))throw new IllegalArgumentException();
            String browser=ha.getText().toString().trim(), manual=path.getText().toString().trim();
            if(!manual.isEmpty()&&!manual.matches("/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*"))throw new IllegalArgumentException();
            SharedPreferences.Editor edit=feed.prefs.edit().putString("bridge",url).putString("manual",manual)
                .putString("browser",browser.isEmpty()?"":origin(browser,false));
            if(!rawKey.isEmpty()) edit.putString("secret",Vault.seal(rawKey));
            if(!url.equals(feed.prefs.getString("bridge",""))){
                edit.remove("rows").remove("stamp").remove("pins").remove("path");
                feed.rows=new JSONArray();feed.stamp=0;feed.dashboard="";
            }
            edit.apply();feed.restart();feed.notifyListeners();dialog.dismiss();
        }catch(Exception e){toast("Проверьте адрес, ключ и путь");}});
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void dashboard(boolean kiosk){
        String base=feed.prefs.getString("browser",""), path=feed.prefs.getString("manual","");
        if(path.isEmpty()&&!feed.dashboard.isEmpty())path="/"+feed.dashboard;
        if(base.isEmpty()||path.isEmpty()){toast("Проверьте адрес HA и путь дашборда в подключении");return;}
        Uri uri=Uri.parse(base+path);
        if(kiosk && !"https".equals(uri.getScheme())){toast("Для kiosk необходим HTTPS");return;}
        new AlertDialog.Builder(this).setTitle(kiosk?"Проверка Android WebView":"Открыть в браузере")
            .setMessage(("http".equals(uri.getScheme())?"HTTP передаёт данные без шифрования. ":"")
              +"Откроется веб-сессия с правами вошедшего в HA пользователя. Проверьте загрузку карточек и управление пультом. Back вернёт к панели.")
            .setPositiveButton("Открыть",(d,n)->{try{startActivity(kiosk?new Intent(this,Kiosk.class).setData(uri):new Intent(Intent.ACTION_VIEW,uri));}
                catch(ActivityNotFoundException e){toast("Браузер не установлен");}})
            .setNegativeButton("Назад",null).show();
    }
    public void onBackPressed(){if(!section.isEmpty()){int index=Arrays.asList(MENU).indexOf(section);section="";changed();list.setSelection(index);}else super.onBackPressed();}
}
