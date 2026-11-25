package house.panel;

import android.app.Service;
import android.content.Intent;
import android.os.*;

public final class Summary extends Service implements Feed.Listener {
    private Messenger client;
    private Feed feed;
    private String[] last, lastFeatured;
    private int lastNew;
    private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),msg -> {
        client=msg.replyTo; last=null;
        if(client!=null) feed.add(this);
        return true;
    }));
    public IBinder onBind(Intent i){ feed=Feed.get(this); return endpoint.getBinder(); }
    public boolean onUnbind(Intent i){ client=null; feed.remove(this); return false; }
    public void changed(){
        if(client==null)return;
        String[] values=feed.summary();
        String[] featured=feed.featured();
        int fresh=feed.prefs.getStringSet("new",java.util.Collections.emptySet()).size();
        if(java.util.Arrays.equals(values,last) && java.util.Arrays.equals(featured,lastFeatured) && fresh==lastNew)return;
        lastFeatured=featured;lastNew=fresh;
        last=values;
        Message m=Message.obtain(); Bundle b=new Bundle(); b.putStringArray("summary",values); b.putStringArray("featured",featured); b.putInt("new",fresh); m.setData(b);
        try {client.send(m);} catch(RemoteException e){client=null;feed.remove(this);}
    }
}
