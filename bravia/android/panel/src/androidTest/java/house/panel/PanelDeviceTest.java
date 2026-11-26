package house.panel;

import android.test.InstrumentationTestCase;
import android.content.Context;
import java.util.*;
import org.json.*;

public final class PanelDeviceTest extends InstrumentationTestCase {
    public void testLanCleartextScope() throws Exception {
        android.security.NetworkSecurityPolicy policy=android.security.NetworkSecurityPolicy.getInstance();
        assertTrue(policy.isCleartextTrafficPermitted(BuildConfig.LAN_HOST));
        assertFalse(policy.isCleartextTrafficPermitted("example.org"));
        for(String url:new String[]{"http://example.org/v1/pair",TvApi.LAN+"0/api/bravia_launcher/v1/events",
            "http://10.0.0.2:80/api/bravia_launcher/v1/pair",TvApi.LAN+"/other"}) {
            try { TvApi.open(url);fail(url); } catch(java.io.IOException expected) { }
        }
        TvApi.open(TvApi.LAN+"/api/bravia_launcher/v1/events").disconnect();
    }
    private JSONObject row(String key,String name,String value) throws Exception{
        return new JSONObject().put("key",key).put("title",name).put("eid","sensor.fixture")
                .put("domain","sensor").put("section","Датчики").put("unit","°C").put("value",value);
    }
    public void testVacuumBatteryAndDoorWarning() throws Exception{
        JSONObject vacuum=new JSONObject().put("domain","vacuum").put("value","docked").put("battery",83);
        assertEquals("На базе · 83%",Feed.reading(vacuum));
        JSONObject door=new JSONObject().put("domain","binary_sensor").put("dc","door").put("value","on");
        assertTrue(Feed.problem(door));door.put("value","off");assertFalse(Feed.problem(door));
    }
    public void testPinsOrderRenameRemovalAndStaleOnApi24() throws Throwable{
        assertEquals(24,android.os.Build.VERSION.SDK_INT);
        runTestOnUiThread(()->{
            Feed f=Feed.get(getInstrumentation().getTargetContext());
            f.prefs.edit().clear().commit();
            try{
                JSONObject a=row("registry-1","Ванная","22"), b=row("registry-2","Спальня","18");
                f.accept(0,new JSONObject().put("live",true).put("stamp",1).put("rows",new JSONArray().put(a).put(b)));
                f.savePins(Arrays.asList("registry-2","registry-1"));
                assertTrue(f.summary()[4].startsWith("Спальня"));
                a.put("eid","sensor.renamed").put("title","Новый датчик").put("value","24");
                f.accept(0,new JSONObject().put("live",true).put("stamp",2).put("upsert",new JSONArray().put(a)).put("remove",new JSONArray().put("registry-2")));
                assertTrue(f.summary()[4].contains("Новый датчик"));
                assertFalse(f.summary()[4].contains("Спальня"));
                assertEquals("registry-2",f.pins().get(0));
                for(String value:Arrays.asList("unknown","unavailable")){
                    a.put("value",value);
                    assertTrue(Feed.problem(a));assertFalse(f.summary()[4].contains("Новый датчик"));
                }
                f.accept(0,new JSONObject().put("live",false));
                assertTrue(f.ageLabel().contains("устарело"));
                f.stop();
            }catch(Exception e){throw new AssertionError(e);}
        });
    }
    public void testKioskCapabilityOnLegacySystemWebView() throws Throwable {
        Context c=getInstrumentation().getTargetContext();
        java.util.regex.Matcher ua=java.util.regex.Pattern.compile("Chrome/(\\d+)").matcher(android.webkit.WebSettings.getDefaultUserAgent(c));
        if(!ua.find() || Integer.parseInt(ua.group(1))>=67)return;
        android.content.Intent intent=new android.content.Intent(c,Kiosk.class)
            .setData(android.net.Uri.parse("https://127.0.0.1/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=getInstrumentation().startActivitySync(intent);
        try{
            boolean[] fallback={false};
            for(int i=0;i<40&&!fallback[0];i++){
                runTestOnUiThread(()->{
                    android.view.ViewGroup root=(android.view.ViewGroup)activity.findViewById(android.R.id.content);
                    android.view.View child=root.getChildAt(0);
                    fallback[0]=child instanceof android.widget.TextView && ((android.widget.TextView)child).getText().toString().contains("WebView");
                });
                Thread.sleep(150);
            }
            assertTrue("Old WebView should return an explicit native fallback",fallback[0]);
        }finally{runTestOnUiThread(activity::finish);}
    }
    public void testProvisionEphemeralEmulatorFeed() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        java.io.File file=new java.io.File(context.getFilesDir(),"emulator.json");
        if(!file.isFile())return;
        assertEquals("1",android.os.Build.FINGERPRINT.contains("generic")?"1":"0");
        JSONObject setup;
        try(java.io.BufferedReader in=new java.io.BufferedReader(new java.io.FileReader(file))){
            setup=new JSONObject(in.readLine());
        }
        assertTrue(file.delete());
        assertEquals("https://10.0.2.2:18443",setup.getString("url"));
        context.getSharedPreferences("panel",0).edit().clear().putString("bridge",setup.getString("url"))
            .putString("secret",Vault.seal(setup.getString("key")))
            .putString("pins",new JSONArray(Arrays.asList("demo-0","demo-1")).toString()).commit();
    }
    public void testKeystoreRoundTripAndRevocation() throws Exception {
        String secret=UUID.randomUUID().toString(),blob=Vault.seal(secret);
        assertFalse(blob.contains(secret));assertEquals(secret,Vault.open(blob));Vault.clear();
        try {Vault.open(blob);fail("deleted key accepted");}catch(Exception expected){}
    }
}
