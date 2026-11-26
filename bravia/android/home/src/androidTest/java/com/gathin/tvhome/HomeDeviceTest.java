package com.gathin.tvhome;

import android.content.Intent;
import android.os.Build;
import android.test.InstrumentationTestCase;
import android.view.*;
import java.util.*;

public final class HomeDeviceTest extends InstrumentationTestCase {
    public void testDirectionalNavigationAndHdmiEditor() throws Throwable {
        assertEquals(24,Build.VERSION.SDK_INT);
        Intent start=new Intent(getInstrumentation().getTargetContext(),BraviaHome.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BraviaHome home=(BraviaHome)getInstrumentation().startActivitySync(start);
        try{
            // Enter D-pad mode even after a preceding touch-based setup dialog.
            getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                View root=home.getWindow().getDecorView();
                View first=home.getCurrentFocus();assertNotNull(first);
                ArrayList<View> controls=new ArrayList<>();
                root.addFocusables(controls,View.FOCUS_FORWARD);
                Set<View> reached=new HashSet<>();Queue<View> todo=new ArrayDeque<>();
                reached.add(first);todo.add(first);
                while(!todo.isEmpty()){
                    View item=todo.remove();
                    for(int dir:new int[]{View.FOCUS_UP,View.FOCUS_DOWN,View.FOCUS_LEFT,View.FOCUS_RIGHT}){
                        View next=item.focusSearch(dir);
                        if(next!=null && next.isClickable() && reached.add(next))todo.add(next);
                    }
                }
                for(View v:controls)if(v.isClickable())assertTrue("Unreachable "+v.getContentDescription(),reached.contains(v));
                assertTrue("Expected HOME controls after removing duplicate catalog shortcuts",reached.size()>=18);
                for(int i=1;i<=4;i++){
                    ArrayList<View> labels=new ArrayList<>();
                    root.findViewsWithText(labels,"HDMI "+i,View.FIND_VIEWS_WITH_TEXT);
                    assertFalse("Missing HDMI "+i,labels.isEmpty());
                    assertTrue(reached.contains(labels.get(0).getParent()));
                }
                first.requestFocus();
            });
            for(int key:new int[]{KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP}){
                getInstrumentation().sendKeyDownUpSync(key);
                getInstrumentation().waitForIdleSync();
                assertNotNull(home.getCurrentFocus());
            }
        }finally{runTestOnUiThread(home::finish);}
    }
    public void testPortNumbersAndChannelUri(){
        assertEquals(4,HdmiPorts.number("HDMI 4"));assertEquals(2,HdmiPorts.number("hdmi-2"));
        assertEquals(0,HdmiPorts.number("ExternalTvInputService/HW9"));assertEquals(0,HdmiPorts.number("HDMI Switch 2"));
        String id="test.input/.ExternalTvInputService/HW77";
        assertEquals(id,HdmiPorts.channel(id).getData().getLastPathSegment());
    }
}
