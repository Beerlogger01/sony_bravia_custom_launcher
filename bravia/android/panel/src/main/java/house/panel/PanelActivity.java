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
    private ListView list, navigation;
    private TextView heading, count;
    private ArrayAdapter<String> navAdapter;
    private static final int PAPER=0xfffaf7ee, MUTED=0xffb5bac3, GOLD=0xffffc080;
    private float scale;
    private boolean pendingDashboard;
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> keys=new ArrayList<>();
    private final ArrayList<String> labels=new ArrayList<>();
    private final HashMap<String,JSONObject> catalog=new HashMap<>();
    private String section="";
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final Set<String> pending=new HashSet<>();
    private static final String[] MENU={"Избранное","Климат","Техника","Система","Свет","Датчики","Проблемы",
        "Каталог · добавить на главную","Открыть Home Assistant","Kiosk · эксперимент","Подключение","Сейчас активно","Сценарии","Недоступные"};
    public void onCreate(Bundle state) {
        super.onCreate(state);
        feed=Feed.get(this);
        scale=Math.min(getResources().getDisplayMetrics().widthPixels/1200f,getResources().getDisplayMetrics().heightPixels/675f);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setBackgroundColor(0xff12161d);setContentView(box);
        LinearLayout side=column();side.setBackgroundColor(0xff1c212a);side.setPadding(px(28),px(29),px(22),px(20));box.addView(side,px(275),-1);
        TextView brand=type(getString(R.string.house_name),21,GOLD,true);brand.setLetterSpacing(.12f);side.addView(brand);
        TextView sub=type("Home Assistant",17,MUTED,false);side.addView(sub);
        navigation=new ListView(this);configureList(navigation);LinearLayout.LayoutParams navSize=new LinearLayout.LayoutParams(-1,0,1);navSize.topMargin=px(20);side.addView(navigation,navSize);
        navAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,MENU){
            public View getView(int i,View old,ViewGroup parent){
                TextView t=old instanceof TextView?(TextView)old:type("",18,PAPER,false);
                String label=i==7?"Каталог сущностей":MENU[i];t.setText(label);t.setTextColor(MENU[i].equals(section)?GOLD:PAPER);
                t.setPadding(px(12),0,0,0);t.setHeight(px(43));return t;
            }
        };
        navigation.setAdapter(navAdapter);navigation.setOnItemClickListener((p0,v,i,id)->openSection(i));
        LinearLayout main=column();main.setPadding(px(35),px(28),px(37),px(24));box.addView(main,new LinearLayout.LayoutParams(0,-1,1));
        status=type("",16,GOLD,true);main.addView(status);
        heading=type("",40,PAPER,true);main.addView(heading);
        count=type("",17,MUTED,false);main.addView(count);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>()){
            @Override public View getView(int i,View recycled,ViewGroup parent){
                LinearLayout row=recycled instanceof LinearLayout?(LinearLayout)recycled:entityRow();
                bindRow(row,i);return row;
            }
        };
        list=new ListView(this);configureList(list);list.setDivider(new android.graphics.drawable.ColorDrawable(0xff12161d));list.setDividerHeight(px(7));
        list.setAdapter(adapter);LinearLayout.LayoutParams dataSize=new LinearLayout.LayoutParams(-1,0,1);dataSize.topMargin=px(18);main.addView(list,dataSize);
        list.setOnItemClickListener((p0,v,i,id)->select(i));
        String requested=getIntent().getStringExtra("screen");
        section=state!=null?state.getString("section",MENU[0]):requested;
        if(section==null || (!section.startsWith("card:")&&!Arrays.asList(MENU).contains(section)))section=MENU[0];
        navigation.setSelection(Math.max(0,Arrays.asList(MENU).indexOf(section)));
        if(requested!=null && !requested.isEmpty())list.requestFocus();else navigation.requestFocus();
        if("retry".equals(requested))feed.restart();
        pendingDashboard=state==null && "dashboard".equals(requested);
    }
    private int px(float n){return Math.round(n*scale);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView type(String label,int size,int color,boolean medium){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));
        t.setTypeface(android.graphics.Typeface.create(medium?"sans-serif-medium":"sans-serif",android.graphics.Typeface.NORMAL));
        t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private android.graphics.drawable.GradientDrawable surface(int fill){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(fill);bg.setCornerRadius(px(11));return bg;
    }
    private void configureList(ListView v){
        v.setDivider(null);v.setVerticalScrollBarEnabled(false);v.setDrawSelectorOnTop(true);
        android.graphics.drawable.GradientDrawable focus=surface(0x16ffffff);focus.setStroke(px(2),GOLD);
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},surface(0));v.setSelector(states);
    }
    private LinearLayout entityRow(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(px(75));row.setPadding(px(18),px(7),px(18),px(7));row.setBackground(surface(0xff242b35));
        LinearLayout name=column();row.addView(name,new LinearLayout.LayoutParams(0,-2,1));name.addView(type("",21,PAPER,true));name.addView(type("",15,MUTED,false));
        TextView val=type("",23,GOLD,true);val.setMaxWidth(px(200));row.addView(val);return row;
    }
    private void bindRow(LinearLayout row,int i){
        String key=keys.get(i);JSONObject r=catalog.get(key);LinearLayout name=(LinearLayout)row.getChildAt(0);
        TextView title=(TextView)name.getChildAt(0),detail=(TextView)name.getChildAt(1),value=(TextView)row.getChildAt(1);
        title.setSingleLine(false);title.setMaxLines(2);title.setText(r==null?labels.get(i):(r.optString("icon")+" "+r.optString("title")).trim());
        if(r==null){detail.setText(key.isEmpty()?"Выберите раздел или откройте каталог":"Проверьте запись в реестре");value.setText("");return;}
        String area=r.optString("area"),device=r.optString("device");
        String location=area.isEmpty()?device:area+(device.isEmpty()?"":"  ·  "+device);
        detail.setText((feed.prefs.getStringSet("new",Collections.emptySet()).contains(key)?"Новое   ·   ":"")+(feed.pins().contains(key)?"★   ":"")+(location.isEmpty()?r.optString("eid"):location));
        value.setText(Feed.reading(r).trim());
    }
    private void openSection(int i){
        if(i==10){settings();return;}if(i==8||i==9){dashboard(i==9);return;}
        if(!section.equals(MENU[i]))history.push(section);section=MENU[i];changed();navAdapter.notifyDataSetChanged();list.requestFocus();list.setSelection(0);
    }
    public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("section",section);}
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    public void onStart(){super.onStart();feed.add(this);if(pendingDashboard){pendingDashboard=false;list.post(()->dashboard(false));}}
    public void onStop(){feed.remove(this);super.onStop();}
    public void changed(){
        status.setText("HOME ASSISTANT    /    "+feed.ageLabel());
        heading.setText(section.startsWith("card:")?"Карточка":section.equals(MENU[7])?"Каталог сущностей":section);
        int pos=list.getSelectedItemPosition();
        String selected=pos>=0 && pos<keys.size()?keys.get(pos):"";
        Set<String> designed=new HashSet<>();
        catalog.clear();
        for(int i=0;i<feed.rows.length();i++){
            JSONObject row=feed.rows.optJSONObject(i); catalog.put(row.optString("key"),row);
            if(row.has("template"))designed.add(row.optString("eid"));
        }
        ArrayList<String> next=new ArrayList<>(), ids=new ArrayList<>();
        {
            List<String> pins=feed.pins();
            Iterable<String> source=section.equals(MENU[0])?pins:orderedKeys();
            for(String key:source){
                JSONObject r=catalog.get(key);
                if(r==null){ ids.add(key);next.add("Привязка отсутствует · "+key);continue;}
                boolean problem=Feed.problem(r) && (!"safety".equals(r.optString("health")) || feed.layout.optBoolean("safety",true));
                if(section.startsWith("card:") && !section.equals(key))continue;
                if(!key.startsWith("card:") && !section.equals(MENU[7]) && !section.equals(MENU[0]) && designed.contains(r.optString("eid")))continue;
                if(section.equals("Сейчас активно") && !r.optBoolean("active"))continue;
                if(section.equals("Недоступные") && !"unavailable".equals(r.optString("value")))continue;
                if(section.equals("Свет") && feed.layout.optBoolean("hide_lights") && !r.optBoolean("available"))continue;
                if(section.equals(MENU[6]) && !problem)continue;
                if(!(section.startsWith("card:")||section.equals("Сейчас активно")||section.equals("Недоступные")||section.equals(MENU[7])||section.equals(MENU[0])||section.equals(MENU[6]))
                    && (!section.equals(feed.prefs.getString("section."+key,r.optString("section")))))continue;
                ids.add(key);next.add((feed.prefs.getStringSet("new",Collections.emptySet()).contains(key)?"Новое   ":"")+(pins.contains(key)?"★   ":"")+r.optString("title")+"     "+Feed.reading(r)
                    +"     "+r.optString("area")+(section.equals(MENU[7])?"   ·   "+r.optString("device"):""));
            }
        }
        int fresh=feed.prefs.getStringSet("new",Collections.emptySet()).size();
        count.setText("Карточек  ·  "+next.size()+(section.equals(MENU[7])?"     Новых  ·  "+fresh:""));
        if(next.isEmpty()){next.add("Пока нет данных");ids.add("");}
        if(keys.equals(ids) && !next.equals(labels)){
            // List identity and focus stay stable while individual readings change.
            int first=list.getFirstVisiblePosition();
            for(int i=0;i<next.size();i++){
                if(next.get(i).equals(labels.get(i)))continue;
                View child=list.getChildAt(i-first);
                if(child instanceof LinearLayout)bindRow((LinearLayout)child,i);
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
            int order=Integer.compare(left.optInt("order",100000),right.optInt("order",100000));
            if(order!=0)return order;
            for(String field:new String[]{"area","device","title"}){
                order=collator.compare(left.optString(field),right.optString(field));
                if(order!=0)return order;
            }
            return a.compareTo(b);
        });
        return result;
    }
    private void select(int i){
        String key=keys.get(i); if(key.isEmpty())return;
        feed.acknowledge(key);
        ArrayList<String> pins=feed.pins(); int index=pins.indexOf(key);
        String[] choices=index<0?new String[]{"Закрепить"}:new String[]{"Открепить","Переместить выше","Переместить ниже"};
        JSONObject row=catalog.get(key);
        if(row!=null && ("link".equals(row.optString("template"))||"active".equals(row.optString("template")))){
            navigate("active".equals(row.optString("template"))?"Сейчас активно":row.optString("link"));return;
        }
        if(row!=null && !row.optString("operation").isEmpty() && !"toggle".equals(row.optString("operation"))){
            Runnable send=()->command(row,row.optString("operation"));
            if(row.optBoolean("confirm",true))new AlertDialog.Builder(this).setMessage(row.optString("title")+"?")
                .setNegativeButton("Назад",null).setPositiveButton("Выполнить",(d,n)->send.run()).show();
            else send.run();
            return;
        }
        if(row!=null && "light".equals(row.optString("domain")) && (section.equals("Свет")||"toggle".equals(row.optString("operation")))){
            if(!row.optBoolean("controllable")){toast("Только просмотр");return;}
            command(row,"on".equals(row.optString("value"))?"turn_off":"turn_on");return;
        }
        ArrayList<String> actions=new ArrayList<>(Arrays.asList(choices));
        if(row!=null && Arrays.asList("temperature","humidity","illuminance").contains(row.optString("dc"))) actions.add("История за 24 часа");
        if(row!=null)actions.add("Выбрать раздел");
        String toggle=row!=null && "on".equals(row.optString("value"))?"Выключить":"Включить";
        if(row!=null && row.optBoolean("controllable") && "В сети".equals(feed.status))actions.add(toggle);
        new AlertDialog.Builder(this).setTitle(row==null?key:row.optString("eid")).setItems(actions.toArray(new String[0]),(d,n)->{
            if(actions.get(n).equals(toggle)){
                Runnable send=()->command(row,toggle.equals("Выключить")?"turn_off":"turn_on");
                if("switch".equals(row.optString("domain")))new AlertDialog.Builder(this).setMessage(toggle+" "+row.optString("title")+"?")
                    .setNegativeButton("Назад",null).setPositiveButton("Выполнить",(x,y)->send.run()).show();
                else send.run();
                return;
            }
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
    private void command(JSONObject row,String action){
        String key=row.optString("key");
        if(!row.optBoolean("available") || !"В сети".equals(feed.status)){toast("Устройство недоступно");return;}
        if(!pending.add(key))return;
        String endpoint=feed.prefs.getString("bridge",""), sealed=feed.prefs.getString("secret","");
        new Thread(()->{
            try{TvApi.post(endpoint,"action",new JSONObject().put("key",key).put("action",action),Vault.open(sealed));}
            catch(Exception ex){runOnUiThread(()->toast("Команда не подтверждена"));}
            finally{runOnUiThread(()->pending.remove(key));}
        },"bravia-action").start();
    }
    private void navigate(String dest){
        if(dest.matches("HDMI [1-4]")){
            setResult(RESULT_OK,new Intent().putExtra("port",dest.charAt(5)-'0'));finish();return;
        }
        if("dashboard".equals(dest)){dashboard(false);return;}
        if(!dest.startsWith("card:")&&!Arrays.asList(MENU).contains(dest))return;
        history.push(section);section=dest;changed();list.requestFocus();list.setSelection(0);
    }
    public boolean dispatchKeyEvent(KeyEvent e){
        int i=e.getKeyCode()-KeyEvent.KEYCODE_PROG_RED;
        if(i>=0 && i<4){
            String dest=feed.layout.optString(new String[]{"red","green","yellow","blue"}[i],"—");
            if(!dest.equals("—")){if(e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0)navigate(dest);return true;}
        }
        return super.dispatchKeyEvent(e);
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
    private static String connectionOrigin(String text, boolean lan){
        if(lan && !TvApi.LAN.equals(expectedOrigin(text)))throw new IllegalArgumentException();
        return lan?TvApi.LAN:origin(text,true);
    }
    private static String expectedOrigin(String text){return text.trim().replaceFirst("/$","");}
    private void settings(){
        LinearLayout fields=new LinearLayout(this);fields.setPadding(dp(20),0,dp(20),0);fields.setOrientation(LinearLayout.VERTICAL);
        String current=feed.prefs.getString("bridge","");
        boolean integrated=current.endsWith("/api/bravia_launcher");
        EditText bridge=field(fields,"Адрес HA или HTTPS bridge",integrated?current.substring(0,current.length()-"/api/bravia_launcher".length()):current,false);
        android.widget.CheckBox lan=new android.widget.CheckBox(this);lan.setText("Локальная сеть (HTTP) · код, ключ и данные без шифрования");lan.setChecked(current.startsWith(TvApi.LAN+"/"));fields.addView(lan);
        EditText code=field(fields,"Одноразовый код из Bravia Launcher в HA","",true);
        Button pair=new Button(this);pair.setText("Привязать телевизор");fields.addView(pair);
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
        pair.setOnClickListener(v->{
            final String url, digits=code.getText().toString().trim();
            try {url=connectionOrigin(bridge.getText().toString(),lan.isChecked());}
            catch(Exception ex){toast("Проверьте режим, адрес и порт HA");return;}
            if(!digits.matches("[0-9]{8}")){toast("Нужны восемь цифр");return;}
            pair.setEnabled(false);dialog.setCancelable(false);
            for(int button:new int[]{-1,-2,-3})dialog.getButton(button).setEnabled(false);
            new Thread(()->{
                String endpoint=url+"/api/bravia_launcher";
                try{
                    String token=TvApi.post(endpoint,"pair",new JSONObject().put("code",digits),null).getString("token");
                    String sealed=Vault.seal(token);
                    runOnUiThread(()->{
                        feed.stop();
                        SharedPreferences.Editor edit=feed.prefs.edit();
                        if(!endpoint.equals(current)){
                            edit.remove("rows").remove("stamp").remove("path").remove("remotePins").remove("new");
                            feed.rows=new JSONArray();feed.stamp=0;feed.dashboard="";
                        }
                        edit.putString("secret",sealed).putString("bridge",endpoint).putString("browser",url).apply();
                        feed.restart();feed.notifyListeners();dialog.dismiss();toast("Телевизор привязан");
                    });
                }catch(Exception ex){runOnUiThread(()->{
                    pair.setEnabled(true);dialog.setCancelable(true);
                    for(int button:new int[]{-1,-2,-3})dialog.getButton(button).setEnabled(true);
                    toast(ex instanceof java.io.IOException && !(ex instanceof javax.net.ssl.SSLException) &&
                        ex.getClass()==java.io.IOException.class?ex.getMessage():"Проверьте сеть, имя сервера и сертификат TLS");
                });}
            },"bravia-pair").start();
        });
        dialog.getButton(-1).setOnClickListener(v->{try{
            String url=connectionOrigin(bridge.getText().toString(),lan.isChecked()), rawKey=key.getText().toString();
            if(lan.isChecked() && (!rawKey.isEmpty() || !current.equals(url+"/api/bravia_launcher")))throw new IllegalArgumentException();
            if(integrated && current.equals(url+"/api/bravia_launcher") && rawKey.isEmpty())url=current;
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
    public void onBackPressed(){if(!history.isEmpty()){section=history.pop();changed();navAdapter.notifyDataSetChanged();list.requestFocus();}else super.onBackPressed();}
}
