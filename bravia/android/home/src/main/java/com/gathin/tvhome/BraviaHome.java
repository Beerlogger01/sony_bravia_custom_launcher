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
    private int accent;
    private float scale;
    private boolean bound;
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),msg->{
        Bundle b=msg.getData();
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
    private void offline(){render(new String[]{"Нет связи","—","—","—","",""},null,0);}
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
        open(new Intent().setClassName(PANEL,"house.panel.PanelActivity").putExtra("screen",destination));
    }
    private void network(){
        NetworkInfo n=((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        set(network,n==null||!n.isConnected()?"Без сети":n.getType()==ConnectivityManager.TYPE_WIFI?"Wi-Fi":n.getTypeName());
        java.text.DateFormat format=new java.text.SimpleDateFormat("EEEE, d MMMM",new Locale("ru"));
        set(date,format.format(new Date()));
    }
    private final BroadcastReceiver systemChange=new BroadcastReceiver(){public void onReceive(Context c,Intent i){network();}};
    private void masthead(){
        LinearLayout r=line();page.addView(r,-1,u(50));
        FrameLayout mark=new FrameLayout(this);mark.setBackground(box(accent,11));pad(mark,7,7);
        mark.addView(icon(R.drawable.lh_house_plug,INK));r.addView(mark,u(38),u(38));gap(r,12);
        TextView brand=text("BRAVIA",24,WHITE,true);brand.setLetterSpacing(.10f);grow(r,brand,1);
        date=text("",18,MUTED,false);r.addView(date);gap(r,24);
        network=text("",15,MUTED,false);r.addView(network);gap(r,16);
        status=text("",16,GREEN,true);pad(status,13,4);status.setBackground(box(TILE,18));r.addView(status);gap(r,18);
        TextClock clock=new TextClock(this);clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");
        clock.setTextColor(WHITE);clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(29));r.addView(clock);gap(r,14);
        FrameLayout gear=new FrameLayout(this);gear.setBackground(box(TILE,14));pad(gear,9,9);gear.addView(icon(R.drawable.lh_settings,WHITE));r.addView(gear,u(43),u(43));
        gear.setContentDescription("Настройки Sony");styleAction(gear,this::sonySettings);
    }
    private FrameLayout hero(){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(box(TILE,14));frame.setClipToOutline(true);
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setImageResource(R.drawable.alpine);
        if(getPreferences(0).getBoolean("landscape",true))frame.addView(photo,-1,-1);
        View veil=new View(this);veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xf3101d22,0xc1324246,0x151c2529}));frame.addView(veil,-1,-1);
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
        styleAction(frame,()->panel(""));return frame;
    }
    private void metric(LinearLayout row,int index,String label,int res,int accent,String section){
        LinearLayout card=column();card.setGravity(Gravity.CENTER_VERTICAL);pad(card,17,8);card.setBackground(box(TILE,14));
        LinearLayout header=line();card.addView(header,-1,u(25));addIcon(header,res,accent,22);gap(header,8);header.addView(text(label,16,MUTED,false));
        gap(card,4);metrics[index]=text("—",index==2?19:27,WHITE,true);card.addView(metrics[index]);styleAction(card,()->panel(section));card.setOnLongClickListener(v->{widgetMenu(index,label);return true;});grow(row,card,1);
    }
    private void overview(){
        LinearLayout row=line();page.addView(row,-1,u(177));FrameLayout feature=hero();grow(row,feature,1.36f);
        String order=getPreferences(0).getString("widgets","0123");ArrayList<Integer> visible=new ArrayList<>();
        for(char digit:order.toCharArray()){int id=digit-'0';if(!getPreferences(0).getBoolean("hideWidget"+id,false))visible.add(id);}
        if(!visible.isEmpty()){
            gap(row,12);LinearLayout grid=column();grow(row,grid,1);
            String[] titles={"Свет","Пылесос","Активно","Проблемы"},sections={"Свет","Техника","Техника","Проблемы"};
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
        if(slot!=null)card.setOnLongClickListener(v->{chooseApp(slot);return true;});grow(appRail,card,1);
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
        appSlot("video","com.google.android.youtube.tv","YouTube",R.drawable.lh_play,0xffffae9b,0xff422d32);
        appSlot("music","com.spotify.tv.android","Spotify",R.drawable.lh_headphones,GREEN,0xff263b35);
        appTile("Эфир",R.drawable.lh_radio_tower,0xff9cd2ec,0xff293640,()->app("com.sony.dtv.tvx"),null);
        appTile("Трансляция",R.drawable.lh_cast,LILAC,0xff302f3e,this::mirroring,null);
        appTile("Все приложения",R.drawable.lh_layout_grid,WHITE,TILE,()->open(new Intent(this,LibraryScreen.class)),null);
    }
    private void chooseApp(String slot){
        Intent filter=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(filter,0);apps.sort(new ResolveInfo.DisplayNameComparator(getPackageManager()));
        ArrayList<String> names=new ArrayList<>(),pkgs=new ArrayList<>();names.add("Вернуть стандартное приложение");
        for(ResolveInfo app:apps){names.add(app.loadLabel(getPackageManager()).toString());pkgs.add(app.activityInfo.packageName);}
        new AlertDialog.Builder(this).setTitle("Избранное приложение").setItems(names.toArray(new String[0]),(d,n)->{
            SharedPreferences.Editor e=getPreferences(0).edit();if(n==0)e.remove(slot);else e.putString(slot,pkgs.get(n-1));e.apply();
            refreshApps();appRail.getChildAt(slot.equals("video")?0:2).requestFocus();
        }).show();
    }
    private TextView control(LinearLayout row,String name,int image,Runnable action){
        if(row.getChildCount()>0)gap(row,10);
        LinearLayout c=line();pad(c,15,0);c.setBackground(box(0xff1b2029,14));addIcon(c,image,accent,21);gap(c,10);
        TextView t=text(name,17,WHITE,false);c.addView(t);styleAction(c,action);grow(row,c,1);return t;
    }
    public void onCreate(Bundle state){
        super.onCreate(state);
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        scale=Math.min(dm.widthPixels/1200f,dm.heightPixels/675f);
        page=column();page.setGravity(Gravity.CENTER_VERTICAL);pad(page,44,18);page.setBackgroundColor(INK);setContentView(page);
        accent=getPreferences(0).getInt("accent",GOLD);
        masthead();gap(page,13);overview();gap(page,12);
        page.addView(text("Источники",22,WHITE,true));gap(page,5);
        portRail=line();page.addView(portRail,-1,u(55));refreshPorts(0);gap(page,12);
        LinearLayout appsTitle=line();page.addView(appsTitle,-1,u(28));grow(appsTitle,text("Приложения",22,WHITE,true),1);
        appsTitle.addView(text("Избранные приложения  ·  удерживайте OK для замены",14,MUTED,false));gap(page,6);
        appRail=line();page.addView(appRail,-1,u(85));refreshApps();gap(page,13);
        LinearLayout controls=line();page.addView(controls,-1,u(48));
        control(controls,"Домашний дашборд",R.drawable.lh_panels_top_left,()->panel("dashboard"));
        registry=control(controls,"Каталог",R.drawable.lh_notebook_tabs,()->panel("Каталог · добавить на главную"));
        control(controls,"Мои карточки",R.drawable.lh_sliders_horizontal,()->panel("Избранное"));
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
            LinearLayout tile=line();pad(tile,13,0);tile.setBackground(box(0xff302e40,14));addIcon(tile,R.drawable.lh_hdmi,LILAC,27);gap(tile,10);
            String alias=getPreferences(0).getString("name"+port,"");
            tile.addView(text("HDMI "+port+(alias.isEmpty()?"":"  ·  "+alias),18,WHITE,true));
            styleAction(tile,()->switchPort(port));tile.setOnLongClickListener(v->{editPort(port);return true;});grow(portRail,tile,1);
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
        String[] options={"Акцентный цвет","Пейзаж / градиент","Показать скрытые HDMI","Карточки Home Assistant","Настроить видеоприложение","Настроить музыку","Вернуть скрытые виджеты"};
        new AlertDialog.Builder(this).setTitle("Вид экрана").setItems(options,(d,n)->{
            if(n==0)new AlertDialog.Builder(this).setTitle("Цвет фокуса").setItems(new String[]{"Янтарь","Мята","Лаванда"},(dialog,i)->{
                getPreferences(0).edit().putInt("accent",new int[]{GOLD,GREEN,LILAC}[i]).apply();recreate();
            }).show();
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
        changes.addAction(Intent.ACTION_DATE_CHANGED);changes.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(systemChange,changes);network();
        if(getPackageManager().checkSignatures(getPackageName(),PANEL)==PackageManager.SIGNATURE_MATCH){
            try{bound=bindService(new Intent().setClassName(PANEL,"house.panel.Summary"),connection,BIND_AUTO_CREATE);}
            catch(SecurityException ignored){offline();}
        }
    }
    public void onStop(){unregisterReceiver(systemChange);if(bound){unbindService(connection);bound=false;}offline();super.onStop();}
    public void onBackPressed(){ /* HOME is the root of the remote navigation stack. */ }
}
