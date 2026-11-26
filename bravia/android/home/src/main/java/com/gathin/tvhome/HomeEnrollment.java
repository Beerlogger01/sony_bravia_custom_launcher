package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Explicit shell-only receiver, protected by DUMP. Changes only our own HOME alias. */
public final class HomeEnrollment extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        int state;
        if("house.bravia.ENROLL".equals(intent.getAction()))state=PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        else if("house.bravia.RETIRE".equals(intent.getAction()))state=PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        else return;
        c.getPackageManager().setComponentEnabledSetting(new ComponentName(c,"com.gathin.tvhome.HomeChoice"),
                state,PackageManager.DONT_KILL_APP);
        setResultCode(-1);
    }
}
