package com.gathin.tvhome;
import android.content.*;
import android.os.Build;
import java.util.HashSet;

/** DUMP-protected import of the shell's actual deviceId -> inputId join. */
public final class PortMapReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        if(!"house.bravia.PORT_MAP".equals(intent.getAction()))return;
        HashSet<String> used=new HashSet<>();
        SharedPreferences.Editor e=c.getSharedPreferences("hardware",0).edit().clear().putString("firmware",Build.FINGERPRINT).putInt("boot",HdmiPorts.boot(c));
        for(int i=1;i<=4;i++){
            String id=intent.getStringExtra("p"+i);if(id==null)continue;
            if(!id.matches("[A-Za-z0-9_./]+") || !used.add(id))return;
            e.putString("p"+i,id);
        }
        if(e.commit())setResultCode(-1);
    }
}
