package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class BraviaHome extends Activity {
    private static final String PANEL="house.bravia.panel";
    private static final int MINT=0xff9de3c2, WHITE=0xffedf5f1, SURFACE=0xff203639;
    private final TextView[] readings=new TextView[6];
    private boolean bound;
    private LinearLayout page;
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(), msg->{
        String[] values=msg.getData().getStringArray("summary");
        if(bound && values!=null && values.length==6) render(values);
        return true;
    }));
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            Message m=Message.obtain();m.replyTo=receiver;
            try{new Messenger(binder).send(m);}catch(RemoteException e){offline();}
        }
        public void onServiceDisconnected(ComponentName name){offline();}
    };
    private void render(String[] values){
        for(int i=0;i<6;i++){
            String text=values[i];
            if(i==0)text="HA   ·   "+text;
            if(i==4 && text.isEmpty())text="Закрепите показатели в каталоге";
            if(i==5 && text.isEmpty())text="—";
            if(!text.contentEquals(readings[i].getText())) readings[i].setText(text);
        }
    }
    private void offline(){render(new String[]{"Нет связи","—","—","—","",""});}
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private TextView text(int size,int color){
        TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;
    }
    private GradientDrawable shape(int color,boolean focus){
        GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(10));
        if(focus)s.setStroke(dp(3),MINT);return s;
    }
    private LinearLayout row(int height){
        LinearLayout r=new LinearLayout(this);page.addView(r,-1,dp(height));return r;
    }
    private void weighted(LinearLayout row,View v){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,dp(3),dp(7),dp(3));row.addView(v,p);
    }
    private void tile(LinearLayout row,String label,Runnable launch){
        TextView t=text(18,WHITE);t.setGravity(Gravity.CENTER);t.setText(label);t.setFocusable(true);t.setClickable(true);
        StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(0xff30564e,true));s.addState(new int[]{},shape(SURFACE,false));t.setBackground(s);
        t.setOnClickListener(v->launch.run());weighted(row,t);
    }
    private void metric(LinearLayout metrics,String title,int index){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12),0,dp(10),0);card.setBackground(shape(0xff182b30,false));
        TextView label=text(14,MINT);label.setText(title);card.addView(label);
        readings[index]=text(index==5?16:23,WHITE);card.addView(readings[index]);weighted(metrics,card);
    }
    public void onCreate(Bundle b){
        super.onCreate(b);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(32),dp(8),dp(24),dp(8));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff10262c,0xff101c20}));setContentView(page);
        LinearLayout header=row(48);TextClock clock=new TextClock(this);clock.setTextSize(23);clock.setTextColor(WHITE);
        clock.setFormat12Hour("HH:mm   ·   EEE, d MMM");clock.setFormat24Hour("HH:mm   ·   EEE, d MMM");weighted(header,clock);
        TextView network=text(14,MINT);NetworkInfo n=((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        network.setText(n!=null && n.isConnected()?n.getTypeName():"Сеть отключена");header.addView(network);
        readings[0]=text(14,MINT);readings[0].setPadding(dp(16),0,dp(8),0);header.addView(readings[0]);
        TextView title=text(34,WHITE);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setText("Дом");page.addView(title);
        readings[4]=text(18,MINT);page.addView(readings[4],-1,dp(46));
        LinearLayout metrics=row(80);metric(metrics,"Свет включён",1);metric(metrics,"Пылесос",2);metric(metrics,"Проблемы",3);metric(metrics,"Сейчас активно",5);
        View space=new View(this);page.addView(space,-1,dp(16));
        LinearLayout first=row(56);
        tile(first,"Home Assistant",()->open(new Intent().setClassName(PANEL,"house.panel.PanelActivity")));
        tile(first,"HDMI / источники",()->open(new Intent(this,LibraryScreen.class).setAction(MainActivity.ACTION_VIEW_INPUTS)));
        tile(first,"YouTube",()->app("com.google.android.youtube.tv"));tile(first,"Spotify",()->app("com.spotify.tv.android"));
        LinearLayout second=row(56);tile(second,"Эфирное ТВ",()->app("com.sony.dtv.tvx"));
        tile(second,"Screen Mirroring",this::mirroring);
        tile(second,"Приложения",()->open(new Intent(this,LibraryScreen.class)));
        tile(second,"Настройки Sony",()->open(new Intent(Settings.ACTION_SETTINGS)));
        first.getChildAt(0).requestFocus();offline();
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
        if(getPackageManager().checkSignatures(getPackageName(),PANEL)==PackageManager.SIGNATURE_MATCH){
            try{bound=bindService(new Intent().setClassName(PANEL,"house.panel.Summary"),connection,BIND_AUTO_CREATE);}
            catch(SecurityException ignored){offline();}
        }
    }
    public void onStop(){if(bound){unbindService(connection);bound=false;}offline();super.onStop();}
    public void onBackPressed(){ /* HOME is the root of the remote navigation stack. */ }
}
