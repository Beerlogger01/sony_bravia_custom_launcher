package com.gathin.tvhome;

import android.content.*;
import android.media.tv.*;
import android.os.Build;
import java.util.*;
import java.util.regex.*;

/** Never infers a connector number from the suffix of a TIF id or from list position. */
final class HdmiPorts {
    private static final Pattern NUMBER=Pattern.compile("(?i)^HDMI[ \\-]*([1-4])$");
    static int number(CharSequence label){
        if(label==null)return 0;
        Matcher m=NUMBER.matcher(label.toString().trim());return m.matches()?Integer.parseInt(m.group(1)):0;
    }
    static String input(Context c,int port){
        TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if(tm==null)return null;
        SharedPreferences map=c.getSharedPreferences("hardware",0);
        String saved=Build.FINGERPRINT.equals(map.getString("firmware",""))?map.getString("p"+port,""):"";
        String match=null;int matches=0;
        for(TvInputInfo i:tm.getTvInputList()){
            if(i.getType()!=TvInputInfo.TYPE_HDMI||!i.isPassthroughInput()||i.getParentId()!=null||i.isHidden(c))continue;
            if(i.getId().equals(saved))return saved;
            if(number(i.loadLabel(c))==port){match=i.getId();matches++;}
        }
        return matches==1?match:null;
    }
    static Intent channel(String id){
        return new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),"vnd.android.cursor.item/channel");
    }
    private HdmiPorts(){}
}
