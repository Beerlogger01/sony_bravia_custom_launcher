package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class BraviaHome extends Activity {
    private static final String PANEL="house.bravia.panel";
    private static final int INK=0xff12161d, TILE=0xff232933, WHITE=0xfffaf7ee, MUTED=0xffb2b8c1,
        GOLD=0xffffc080, GREEN=0xffa4daba, LILAC=0xffc8c0f1;
    private final TextView[] metrics=new TextView[4], pinNames=new TextView[3], pinValues=new TextView[3];
    private final LinearLayout[] pinCells=new LinearLayout[3];
    private TextView status, heroCaption, registry, network, date;
    private LinearLayout page, appRail, portRail;
    private int accent, surface, theme;
    private static final String[] THEMES={"Aurora Glass","Midnight Minimal","Warm Ambient"};
    // Static gradients are drawn with the view; no frame loop, blur or wallpaper process.
    private static final int[][] PALETTES={
        {0xff071525,0xff113849,0xff090e20,0xd9234254,0xff8cf3dd},
        {0xff08090b,0xff191b20,0xff050607,0xf02c2d32,0xffffffff},
        {0xff1d1418,0xff453126,0xff151018,0xdf453737,0xffffc185}
    };
    private float scale;
    private boolean bound;
    private org.json.JSONObject layout=new org.json.JSONObject();
    private TextView event;
    private String endpoint="";
    private long lastData;
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),msg->{
        Bundle b=msg.getData();
        try{layout=new org.json.JSONObject(b.getString("layout","{}"));}catch(org.json.JSONException ignored){}
        endpoint=b.getString("endpoint","");lastData=b.getLong("stamp");
        String liveEvent=b.getString("event","");
        set(event,liveEvent);event.setVisibility(liveEvent.isEmpty()?View.GONE:View.VISIBLE);
        String remoteTheme=b.getString("theme","TV");
        int desired=Arrays.asList(THEMES).indexOf(remoteTheme);
        if(bound && desired>=0 && !remoteTheme.equals(getPreferences(0).getString("haTheme","TV"))){
            getPreferences(0).edit().putString("haTheme",remoteTheme).putInt("theme",desired).remove("accent").apply();
            recreate();return true;
        }
        if(bound && desired<0 && getPreferences(0).contains("haTheme"))getPreferences(0).edit().remove("haTheme").apply();
        String[] values=b.getStringArray("summary");
        if(bound && values!=null && values.length==6) render(values,b.getStringArray("featured"),b.getInt("new"));
        return true;
    }));
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){
            Message m=Message.obtain();m.replyTo=receiver;
            try{new Messenger(binder).send(m);}catch(RemoteException e){offline();}
        }
        public void onServiceDisconnected(ComponentName n){offline();}
    };
    private void set(TextView v,String value){if(v!=null && !value.contentEquals(v.getText()))v.setText(value);}
    private void render(String[] v,String[] pins,int fresh){
        boolean live="В сети".equals(v[0]);
        set(status,live?"●   HA в сети":"●   "+v[0]);status.setTextColor(live?GREEN:GOLD);
        set(heroCaption,live?"HOME ASSISTANT":v[0].contains("устарело")?"СОХРАНЁННЫЕ ПОКАЗАНИЯ":"HOME ASSISTANT");
        for(int i=0;i<3;i++){
            boolean present=pins!=null && i*2+1<pins.length;
            pinCells[i].setVisibility(present?View.VISIBLE:View.GONE);
            if(present){set(pinNames[i],pins[2*i]);set(pinValues[i],pins[2*i+1]);}
        }
        boolean empty=pins==null||pins.length==0;
        if(empty){pinCells[0].setVisibility(View.VISIBLE);set(pinNames[0],"Избранные показатели");set(pinValues[0],v[4].isEmpty()?"Открыть панель":v[4]);}
        set(metrics[0],v[1]);set(metrics[1],v[2].trim());set(metrics[2],v[5].isEmpty()?"—":v[5]);
        set(metrics[3],v[3]);set(registry,fresh>0?"Каталог   +"+fresh:"Каталог");
    }
    private void offline(){if(event!=null)event.setVisibility(View.GONE);render(new String[]{"Нет связи","—","—","—","",""},null,0);}
    private int u(float v){return Math.round(v*scale);}
    private TextView text(String label,int size,int color,boolean medium){
        TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(size));
        t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);return t;
    }
    private LinearLayout line(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setClipChildren(false);r.setClipToPadding(false);return r;}
    private LinearLayout column(){LinearLayout r=line();r.setOrientation(LinearLayout.VERTICAL);r.setGravity(Gravity.TOP);return r;}
    private void pad(View v,int x,int y){v.setPadding(u(x),u(y),u(x),u(y));}
    private GradientDrawable box(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(u(radius));return d;
    }
    private void gap(LinearLayout parent,int amount){
        boolean horizontal=parent.getOrientation()==LinearLayout.HORIZONTAL;
        parent.addView(new View(this),horizontal?u(amount):1,horizontal?1:u(amount));
    }
    private void grow(LinearLayout row,View child,float weight){
        boolean horizontal=row.getOrientation()==LinearLayout.HORIZONTAL;
        row.addView(child,new LinearLayout.LayoutParams(horizontal?0:-1,horizontal?-1:0,weight));
    }
    private void styleAction(View v,Runnable action){
        v.setFocusable(true);v.setClickable(true);v.setOnClickListener(view->action.run());
        v.setOnFocusChangeListener((view,on)->{
            GradientDrawable border=box(0x00000000,14);
            border.setStroke(u(on?4:1),on?accent:0x28ffffff);view.setForeground(border);
            view.animate().scaleX(on?1.016f:1f).scaleY(on?1.016f:1f).setDuration(95).start();
        });
        GradientDrawable idle=box(0,14);idle.setStroke(u(1),0x28ffffff);v.setForeground(idle);
    }
    private ImageView icon(int res,int tint){ImageView i=new ImageView(this);i.setImageResource(res);i.setColorFilter(tint);i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return i;}
    private void addIcon(LinearLayout parent,int res,int color,int size){parent.addView(icon(res,color),u(size),u(size));}
    private void panel(String destination){
        try{startActivityForResult(new Intent().setClassName(PANEL,"house.panel.PanelActivity").putExtra("screen",destination),42);}
        catch(ActivityNotFoundException e){Toast.makeText(this,"Панель недоступна",Toast.LENGTH_SHORT).show();}
    }
    protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==42 && result==RESULT_OK && data!=null){int port=data.getIntExtra("port",0);if(port>=1 && port<=4)switchPort(port);}
    }
    public boolean dispatchKeyEvent(KeyEvent e){
        int i=e.getKeyCode()-KeyEvent.KEYCODE_PROG_RED;
        if(i>=0 && i<4){
            String dest=layout.optString(new String[]{"red","green","yellow","blue"}[i],"—");
            if(!dest.equals("—")){if(e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0)panel(dest);return true;}
        }
        return super.dispatchKeyEvent(e);
    }
    private void network(){
        NetworkInfo n=((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        String label=n==null||!n.isConnected()?"Без сети":n.getTypeName();
        if(n!=null && n.isConnected() && n.getType()==ConnectivityManager.TYPE_WIFI){
            int rssi=rssi();label="◔ Wi-Fi "+(rssi>-127?android.net.wifi.WifiManager.calculateSignalLevel(rssi,5)+"/4":"");
        }
        set(network,label);
        java.text.DateFormat format=new java.text.SimpleDateFormat("EEEE, d MMMM",new Locale("ru"));
        set(date,format.format(new Date()));
    }
    private int rssi(){
        try{android.net.wifi.WifiManager w=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            return w.getConnectionInfo().getRssi();}catch(RuntimeException ex){return -127;}
    }
    private void diagnostics(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        LinkProperties link=cm.getLinkProperties(cm.getActiveNetwork());
        String address=link==null?"—":link.getLinkAddresses().toString();
        String detail=network.getText()+" · "+address;
        if(network.getText().toString().contains("Wi-Fi") && rssi()>-127)detail+=" · "+rssi()+" dBm";
        detail+="\nЛокальный интерфейс: "+(link!=null?"подключён":"нет маршрута")+"\nАвторизация: "+(status.getText().toString().contains("HA в сети")?"подтверждена":status.getText().toString().contains("отклонён")?"доступ отклонён":"не подтверждена")+"\nHA: "+endpoint+"\nSSE: "+status.getText()+"\nПоследние данные: "+(lastData>0?new java.util.Date(lastData*1000):"—");
        new AlertDialog.Builder(this).setTitle("Диагностика сети").setMessage(detail).setNegativeButton("Назад",null)
            .setPositiveButton("Повторить соединение",(d,n)->panel("retry")).setNeutralButton("Пульт",(d,n)->keyMonitor()).show();
    }
    private void keyMonitor(){
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Диагностика пульта").setMessage("Нажмите цветную кнопку").setNegativeButton("Назад",null).create();
        dlg.setOnKeyListener((dialog,code,e)->{
            if(code==KeyEvent.KEYCODE_BACK)return false;
            dlg.setMessage(KeyEvent.keyCodeToString(code)+" ("+code+") · action "+e.getAction()+" · repeat "+e.getRepeatCount()+" · source "+e.getSource());
            return code>=KeyEvent.KEYCODE_PROG_RED && code<=KeyEvent.KEYCODE_PROG_BLUE;
        });dlg.show();
    }
    private final BroadcastReceiver systemChange=new BroadcastReceiver(){public void onReceive(Context c,Intent i){network();}};
    private void masthead(){
        LinearLayout r=line();page.addView(r,-1,u(50));
        FrameLayout mark=new FrameLayout(this);mark.setBackground(box(accent,11));pad(mark,7,7);
        mark.addView(icon(R.drawable.lh_house_plug,INK));r.addView(mark,u(38),u(38));gap(r,12);
        TextView brand=text(getString(R.string.house_name),24,WHITE,true);brand.setLetterSpacing(.10f);grow(r,brand,1);
        date=text("",18,MUTED,false);r.addView(date);gap(r,24);
        network=text("",15,MUTED,false);pad(network,8,4);styleAction(network,this::diagnostics);r.addView(network);gap(r,16);
        status=text("",16,GREEN,true);pad(status,13,4);status.setBackground(box(surface,18));r.addView(status);gap(r,18);
        TextClock clock=new TextClock(this);clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");
        clock.setTextColor(WHITE);clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(29));r.addView(clock);gap(r,14);
        FrameLayout gear=new FrameLayout(this);gear.setBackground(box(surface,14));pad(gear,9,9);gear.addView(icon(R.drawable.lh_settings,WHITE));r.addView(gear,u(43),u(43));
        gear.setContentDescription("Настройки Sony");styleAction(gear,this::sonySettings);
    }
    private FrameLayout hero(){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(box(surface,14));frame.setClipToOutline(true);
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setImageResource(R.drawable.alpine);
        if(theme!=1 && getPreferences(0).getBoolean("landscape",true))frame.addView(photo,-1,-1);
        View veil=new View(this);veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{PALETTES[theme][0],PALETTES[theme][1] & 0xbfffffff,PALETTES[theme][2] & 0x33ffffff}));frame.addView(veil,-1,-1);
        LinearLayout content=column();pad(content,27,20);frame.addView(content,-1,-1);
        heroCaption=text("HOME ASSISTANT",14,accent,true);heroCaption.setLetterSpacing(.12f);content.addView(heroCaption);
        content.addView(text("Мой дом",40,WHITE,true));grow(content,new View(this),1);
        LinearLayout pinRow=line();content.addView(pinRow,-1,u(64));
        for(int i=0;i<3;i++){
            LinearLayout cell=column();cell.setGravity(Gravity.BOTTOM);cell.setPadding(0,0,u(12),0);pinCells[i]=cell;grow(pinRow,cell,1);
            pinNames[i]=text("",16,0xffe0e8e5,false);cell.addView(pinNames[i]);
            pinValues[i]=text("",28,WHITE,true);cell.addView(pinValues[i]);
        }
        ImageView arrow=icon(R.drawable.lh_arrow_up_right,WHITE);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(u(25),u(25),Gravity.TOP|Gravity.RIGHT);p.setMargins(0,u(20),u(20),0);frame.addView(arrow,p);
        styleAction(frame,()->panel(layout.optString("home","Избранное")));return frame;
    }
    private void metric(LinearLayout row,int index,String label,int res,int accent,String section){
        LinearLayout card=column();card.setGravity(Gravity.CENTER_VERTICAL);pad(card,17,8);card.setBackground(box(surface,14));
        LinearLayout header=line();card.addView(header,-1,u(25));addIcon(header,res,accent,22);gap(header,8);header.addView(text(label,16,MUTED,false));
        gap(card,4);metrics[index]=text("—",index==2?19:27,WHITE,true);card.addView(metrics[index]);styleAction(card,()->panel(section));grow(row,card,1);
    }
    private void overview(){
        LinearLayout row=line();page.addView(row,-1,u(177));FrameLayout feature=hero();grow(row,feature,1.36f);
        String order=getPreferences(0).getString("widgets","0123");ArrayList<Integer> visible=new ArrayList<>();
        for(char digit:order.toCharArray()){int id=digit-'0';if(!getPreferences(0).getBoolean("hideWidget"+id,false))visible.add(id);}
        if(!visible.isEmpty()){
            gap(row,12);LinearLayout grid=column();grow(row,grid,1);
            String[] titles={"Свет","Пылесос","Активно","Проблемы"},sections={"Свет","Техника","Сейчас активно","Проблемы"};
            int[] icons={R.drawable.lh_lamp_floor,R.drawable.lh_disc_3,R.drawable.lh_activity,R.drawable.lh_circle_alert};
            int[] colors={GOLD,LILAC,GREEN,GOLD};
            LinearLayout pair=null;
            for(int i=0;i<visible.size();i++){
                if(i%2==0){if(i>0)gap(grid,10);pair=line();grow(grid,pair,1);}else gap(pair,10);
                int k=visible.get(i);metric(pair,k,titles[k],icons[k],colors[k],sections[k]);
            }
        }
        feature.requestFocus();
    }
    private void widgetMenu(int index,String label){
        new AlertDialog.Builder(this).setTitle(label).setItems(new String[]{"Переместить вперёд","Переместить назад","Скрыть карточку"},(d,n)->{
            SharedPreferences p=getPreferences(0);char[] order=p.getString("widgets","0123").toCharArray();
            if(n==2)p.edit().putBoolean("hideWidget"+index,true).apply();
            else{
                int at=new String(order).indexOf((char)('0'+index)),step=n==0?-1:1,next=at+step;
                while(next>=0&&next<order.length&&p.getBoolean("hideWidget"+order[next],false))next+=step;
                if(next<0||next>=order.length)return;
                order[at]=order[next];order[next]=(char)('0'+index);p.edit().putString("widgets",new String(order)).apply();
            }
            recreate();
        }).show();
    }
    private void appTile(String label,int image,int color,int back,Runnable action,String slot){
        if(appRail.getChildCount()>0)gap(appRail,10);
        LinearLayout card=column();card.setGravity(Gravity.CENTER_VERTICAL);pad(card,15,10);card.setBackground(box(back,14));
        addIcon(card,image,color,34);gap(card,9);card.addView(text(label,18,WHITE,true));styleAction(card,action);
        card.setTag(slot);grow(appRail,card,1);
    }
    private String favorite(String slot,String fallback){return getPreferences(0).getString(slot,fallback);}
    private void appSlot(String slot,String fallback,String name,int img,int tint,int bg){
        String pkg=favorite(slot,fallback);
        if(!pkg.equals(fallback)){
            try{name=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
            catch(PackageManager.NameNotFoundException ex){name="Выбрать приложение";}
            img=R.drawable.lh_layout_grid;
        }
        appTile(name,img,tint,bg,()->app(pkg),slot);
        if(!pkg.equals(fallback))try{
            LinearLayout card=(LinearLayout)appRail.getChildAt(appRail.getChildCount()-1);
            ImageView icon=(ImageView)card.getChildAt(0);icon.clearColorFilter();icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        }catch(PackageManager.NameNotFoundException ignored){}
    }
    private void refreshApps(){
        appRail.removeAllViews();
        for(String slot:getPreferences(0).getString("appOrder","video,music,tv,cast,all").split(",")){
            if(getPreferences(0).getBoolean("hideApp."+slot,false))continue;
            switch(slot){
                case "video":appSlot(slot,"com.google.android.youtube.tv","YouTube",R.drawable.lh_play,0xffffae9b,0xff422d32);break;
                case "music":appSlot(slot,"com.spotify.tv.android","Spotify",R.drawable.lh_headphones,GREEN,0xff263b35);break;
                case "tv":appTile("Эфир",R.drawable.lh_radio_tower,0xff9cd2ec,0xff293640,()->app("com.sony.dtv.tvx"),slot);break;
                case "cast":appTile("Трансляция",R.drawable.lh_cast,LILAC,0xff302f3e,this::mirroring,slot);break;
                case "all":appTile("Все приложения",R.drawable.lh_layout_grid,WHITE,TILE,()->open(new Intent(this,LibraryScreen.class)),slot);break;
            }
        }
    }
    private void editAppTile(String slot){
        SharedPreferences p=getPreferences(0);
        ArrayList<String> actions=new ArrayList<>(Arrays.asList("Влево","Вправо","Скрыть"));
        if(slot.equals("video")||slot.equals("music"))actions.add("Заменить приложение");
        new AlertDialog.Builder(this).setTitle("Плитка приложения").setItems(actions.toArray(new String[0]),(d,n)->{
            if(n==3){chooseApp(slot);return;}
            if(n==2)p.edit().putBoolean("hideApp."+slot,true).apply();
            else{
                List<String> order=Arrays.asList(p.getString("appOrder","video,music,tv,cast,all").split(","));
                int pos=order.indexOf(slot),step=n==0?-1:1,next=pos+step;
                while(next>=0 && next<order.size() && p.getBoolean("hideApp."+order.get(next),false))next+=step;
                if(next<0 || next>=order.size())return;
                Collections.swap(order,pos,next);p.edit().putString("appOrder",TextUtils.join(",",order)).apply();
            }
            refreshApps();View focused=appRail.findViewWithTag(slot);if(focused!=null)focused.requestFocus();
        }).show();
    }
    private void chooseApp(String slot){
        Intent filter=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(filter,0);apps.sort(new ResolveInfo.DisplayNameComparator(getPackageManager()));
        ArrayList<String> names=new ArrayList<>(),pkgs=new ArrayList<>();names.add("Вернуть стандартное приложение");
        for(ResolveInfo app:apps){names.add(app.loadLabel(getPackageManager()).toString());pkgs.add(app.activityInfo.packageName);}
        new AlertDialog.Builder(this).setTitle("Избранное приложение").setItems(names.toArray(new String[0]),(d,n)->{
            SharedPreferences.Editor e=getPreferences(0).edit();if(n==0)e.remove(slot);else e.putString(slot,pkgs.get(n-1));e.apply();
            refreshApps();View focused=appRail.findViewWithTag(slot);if(focused!=null)focused.requestFocus();
        }).show();
    }
    private TextView control(LinearLayout row,String name,int image,Runnable action){
        if(row.getChildCount()>0)gap(row,10);
        LinearLayout c=line();pad(c,15,0);c.setBackground(box(surface,14));addIcon(c,image,accent,21);gap(c,10);
        TextView t=text(name,17,WHITE,false);c.addView(t);styleAction(c,action);grow(row,c,1);return t;
    }
    public void onCreate(Bundle state){
        super.onCreate(state);
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        scale=Math.min(dm.widthPixels/1200f,dm.heightPixels/675f);
        page=column();page.setGravity(Gravity.CENTER_VERTICAL);pad(page,44,18);theme=getPreferences(0).getInt("theme",0);
        int[] colors=PALETTES[theme];
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors[0],colors[1],colors[2]}));
        surface=colors[3];accent=getPreferences(0).getInt("accent",colors[4]);setContentView(page);
        masthead();gap(page,13);overview();gap(page,12);
        page.addView(text("Источники",22,WHITE,true));gap(page,5);
        portRail=line();page.addView(portRail,-1,u(55));refreshPorts(0);gap(page,12);
        LinearLayout appsTitle=line();page.addView(appsTitle,-1,u(28));grow(appsTitle,text("Приложения",22,WHITE,true),1);
        appsTitle.addView(text("Избранные приложения",14,MUTED,false));gap(page,6);
        appRail=line();page.addView(appRail,-1,u(85));refreshApps();gap(page,13);
        LinearLayout controls=line();page.addView(controls,-1,u(48));
        control(controls,getString(R.string.house_dashboard),R.drawable.lh_panels_top_left,()->panel("dashboard"));
        event=text("",17,accent,true);grow(controls,event,1);event.setVisibility(View.GONE);

        control(controls,"Вид экрана",R.drawable.lh_settings,this::appearance);
        offline();network();
    }
    private void sonySettings(){
        Intent settings=new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo preferred=getPackageManager().resolveActivity(settings,PackageManager.MATCH_DEFAULT_ONLY);
        for(ResolveInfo ri:getPackageManager().queryIntentActivities(settings,PackageManager.MATCH_DEFAULT_ONLY)){
            if(isSystemTarget(ri) && ri.activityInfo.packageName.startsWith("com.sony.")){preferred=ri;break;}
        }
        open(isSystemTarget(preferred)?settings.setClassName(preferred.activityInfo.packageName,preferred.activityInfo.name):null);
    }
    private static boolean isSystemTarget(ResolveInfo ri){
        return ri!=null && ri.activityInfo!=null && ri.activityInfo.exported &&
            (ri.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0 &&
            !ri.activityInfo.name.endsWith("ResolverActivity");
    }
    private void selector(){
        Intent picker=new Intent(MainActivity.ACTION_VIEW_INPUTS);
        for(ResolveInfo ri:getPackageManager().queryIntentActivities(picker,PackageManager.MATCH_DEFAULT_ONLY)){
            if((ri.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0 || !ri.activityInfo.exported)continue;
            try{startActivity(new Intent(picker).setClassName(ri.activityInfo.packageName,ri.activityInfo.name));return;}
            catch(ActivityNotFoundException|SecurityException ignored){}
        }
        new AlertDialog.Builder(this).setTitle("Выбор источника")
            .setMessage("Прошивка не предоставила системный экран входов приложению. Нажмите INPUT на пульте или откройте список доступных входов.")
            .setNegativeButton("Назад",null)
            .setPositiveButton("Список входов",(d,n)->open(new Intent(this,LibraryScreen.class).setAction(MainActivity.ACTION_VIEW_INPUTS))).show();
    }
    private void switchPort(int port){
        try{
            String input=HdmiPorts.input(this,port);
            if(input!=null){
                Intent view=HdmiPorts.channel(input);
                ResolveInfo target=getPackageManager().resolveActivity(new Intent(view).setPackage("com.sony.dtv.tvx"),PackageManager.MATCH_DEFAULT_ONLY);
                if(!isSystemTarget(target))target=getPackageManager().resolveActivity(view,PackageManager.MATCH_DEFAULT_ONLY);
                if(isSystemTarget(target)){
                    startActivity(view.setClassName(target.activityInfo.packageName,target.activityInfo.name));return;
                }
            }
        }catch(RuntimeException ignored){}
        Toast.makeText(this,"HDMI "+port+" · прямой переход недоступен",Toast.LENGTH_SHORT).show();selector();
    }
    private void refreshPorts(int focused){
        portRail.removeAllViews();String order=getPreferences(0).getString("ports","1234");
        for(char ch:order.toCharArray()){
            int port=ch-'0';if(getPreferences(0).getBoolean("hidden"+port,false))continue;
            if(portRail.getChildCount()>0)gap(portRail,10);
            LinearLayout tile=line();pad(tile,13,0);tile.setBackground(box(surface,14));addIcon(tile,R.drawable.lh_hdmi,accent,27);gap(tile,10);
            String alias=getPreferences(0).getString("name"+port,"");
            tile.addView(text("HDMI "+port+(alias.isEmpty()?"":"  ·  "+alias),18,WHITE,true));
            styleAction(tile,()->switchPort(port));grow(portRail,tile,1);
            if(focused==port)tile.requestFocus();
        }
        if(portRail.getChildCount()==0)control(portRail,"Показать входы",R.drawable.lh_hdmi,this::restorePorts);
    }
    private void restorePorts(){
        SharedPreferences.Editor e=getPreferences(0).edit();for(int i=1;i<=4;i++)e.remove("hidden"+i);e.apply();refreshPorts(1);
    }
    private void editPort(int port){
        String[] options={"Переименовать","Переместить влево","Переместить вправо","Скрыть","Выбор источника"};
        new AlertDialog.Builder(this).setTitle("HDMI "+port).setItems(options,(d,n)->{
            SharedPreferences prefs=getPreferences(0);
            if(n==0){
                EditText name=new EditText(this);name.setSingleLine();name.setText(prefs.getString("name"+port,""));
                new AlertDialog.Builder(this).setTitle("Название HDMI "+port).setView(name)
                    .setPositiveButton("Сохранить",(dialog,which)->{prefs.edit().putString("name"+port,name.getText().toString().trim()).apply();refreshPorts(port);})
                    .setNegativeButton("Отмена",null).show();return;
            }
            if(n==4){selector();return;}
            if(n==3)prefs.edit().putBoolean("hidden"+port,true).apply();
            else{
                char[] order=prefs.getString("ports","1234").toCharArray();
                int index=new String(order).indexOf((char)('0'+port)),step=n==1?-1:1,to=index+step;
                while(to>=0 && to<order.length && prefs.getBoolean("hidden"+order[to],false))to+=step;
                if(to>=0 && to<order.length){order[index]=order[to];order[to]=(char)('0'+port);prefs.edit().putString("ports",new String(order)).apply();}
            }
            refreshPorts(n==3?0:port);
        }).show();
    }
    private void appearance(){
        String[] options={"Акцентный цвет","Пейзаж / градиент","Показать скрытые HDMI","Карточки Home Assistant","Настроить видеоприложение","Настроить музыку","Вернуть скрытые виджеты","Тема оформления","Вернуть скрытые приложения","Настроить HDMI","Настроить приложения","Настроить сводки","Каталог сущностей"};
        new AlertDialog.Builder(this).setTitle("Вид экрана").setItems(options,(d,n)->{
            if(n==0)new AlertDialog.Builder(this).setTitle("Цвет фокуса").setItems(new String[]{"Янтарь","Мята","Лаванда"},(dialog,i)->{
                getPreferences(0).edit().putInt("accent",new int[]{GOLD,GREEN,LILAC}[i]).apply();recreate();
            }).show();
            if(n==9)new AlertDialog.Builder(this).setTitle("HDMI").setItems(new String[]{"HDMI 1","HDMI 2","HDMI 3","HDMI 4"},(a,i)->editPort(i+1)).show();
            if(n==10)new AlertDialog.Builder(this).setTitle("Приложения").setItems(new String[]{"Видео","Музыка","ТВ","Screen Mirroring","Все приложения"},(a,i)->editAppTile(new String[]{"video","music","tv","cast","all"}[i])).show();
            if(n==11){String[] names={"Свет","Пылесос","Активно","Проблемы"};new AlertDialog.Builder(this).setTitle("Сводки").setItems(names,(a,i)->widgetMenu(i,names[i])).show();}
            if(n==12)panel("Каталог · добавить на главную");
            if(n==8){SharedPreferences.Editor e=getPreferences(0).edit();for(String slot:new String[]{"video","music","tv","cast","all"})e.remove("hideApp."+slot);e.apply();refreshApps();}
            if(n==7)new AlertDialog.Builder(this).setTitle("Тема оформления").setSingleChoiceItems(THEMES,theme,(a,i)->{getPreferences(0).edit().putInt("theme",i).remove("accent").apply();a.dismiss();recreate();}).show();
            if(n==1){getPreferences(0).edit().putBoolean("landscape",!getPreferences(0).getBoolean("landscape",true)).apply();recreate();}
            if(n==2)restorePorts();if(n==3)panel("Избранное");if(n==4)chooseApp("video");if(n==5)chooseApp("music");
            if(n==6){SharedPreferences.Editor e=getPreferences(0).edit();for(int i=0;i<4;i++)e.remove("hideWidget"+i);e.apply();recreate();}
        }).show();
    }
    private void app(String pkg){
        Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(i==null)i=getPackageManager().getLaunchIntentForPackage(pkg);open(i);
    }
    private void mirroring(){
        Intent i=new Intent().setClassName("com.sony.dtv.networkapp.wifidirect","com.sony.dtv.networkapp.wifidirect.ui.screens.ScreenMirroringHomeActivity");
        if(i.resolveActivity(getPackageManager())!=null)open(i);else app("screnmirroring.com");
    }
    private void open(Intent i){
        try{if(i==null)throw new ActivityNotFoundException();startActivity(i);}
        catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"Функция недоступна в этой прошивке",Toast.LENGTH_SHORT).show();}
    }
    public void onStart(){super.onStart();
        IntentFilter changes=new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        changes.addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION);changes.addAction(Intent.ACTION_DATE_CHANGED);changes.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(systemChange,changes);network();
        if(getPackageManager().checkSignatures(getPackageName(),PANEL)==PackageManager.SIGNATURE_MATCH){
            try{bound=bindService(new Intent().setClassName(PANEL,"house.panel.Summary"),connection,BIND_AUTO_CREATE);}
            catch(SecurityException ignored){offline();}
        }
    }
    public void onStop(){unregisterReceiver(systemChange);if(bound){unbindService(connection);bound=false;}offline();super.onStop();}
    public void onBackPressed(){ /* HOME is the root of the remote navigation stack. */ }
}
