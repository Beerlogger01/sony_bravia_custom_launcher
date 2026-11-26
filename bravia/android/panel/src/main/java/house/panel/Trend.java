package house.panel;
import android.app.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import org.json.*;

public final class Trend extends Activity {
    private volatile HttpURLConnection pending;
    protected void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,24,40,32);
        TextView title=new TextView(this);title.setTextSize(24);title.setText(getIntent().getStringExtra("title")+"   ·   24 часа");layout.addView(title);
        TextView info=new TextView(this);info.setText("Загрузка истории…");layout.addView(info);setContentView(layout);
        Feed feed=Feed.get(this);
        String endpoint=feed.prefs.getString("bridge",""),sealed=feed.prefs.getString("secret","");
        new Thread(()->{
            HttpURLConnection c=null;
            try {
                c=TvApi.open(endpoint+"/v1/history?key="+Uri.encode(getIntent().getStringExtra("key")));
                pending=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(12000);
                c.setRequestProperty("Authorization","Bearer "+Vault.open(sealed));
                StringBuilder raw=new StringBuilder();
                try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){
                    String line;while((line=in.readLine())!=null)raw.append(line);
                }
                JSONArray points=new JSONArray(raw.toString());
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;
                    if(points.length()==0){info.setText("За эти сутки ещё нет измерений");return;}
                    float[] values=new float[points.length()];long[] times=new long[values.length];
                    float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                    for(int i=0;i<values.length;i++){JSONArray p=points.optJSONArray(i);times[i]=p.optLong(0);values[i]=(float)p.optDouble(1);min=Math.min(min,values[i]);max=Math.max(max,values[i]);}
                    final float low=min,span=Math.max(.1f,max-min);
                    info.setText("Мин. "+min+"    Макс. "+max+" "+getIntent().getStringExtra("unit")+"     ·     "+points.length()+" наблюдений");
                    layout.addView(new View(this){
                        final Paint ink=new Paint(1);
                        protected void onDraw(Canvas canvas){
                            ink.setColor(0xff9de3c2);ink.setStrokeWidth(3);
                            long now=System.currentTimeMillis()/1000;
                            float px=0,py=0;
                            for(int i=0;i<values.length;i++){
                                float x=8+(getWidth()-16)*(times[i]-now+86400)/86400f;
                                float y=getHeight()-16-(getHeight()-32)*(values[i]-low)/span;
                                if(i>0 && times[i]-times[i-1]<=600)canvas.drawLine(px,py,x,y,ink);
                                canvas.drawCircle(x,y,3,ink);px=x;py=y;
                            }
                        }
                    },-1,-1);
                });
            }catch(Exception e){runOnUiThread(()->info.setText("История недоступна"));}
            finally{if(c!=null)c.disconnect();pending=null;}
        },"bravia-history").start();
    }
    protected void onDestroy(){HttpURLConnection c=pending;if(c!=null)new Thread(c::disconnect).start();super.onDestroy();}
}
