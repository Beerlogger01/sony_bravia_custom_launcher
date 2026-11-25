package house.panel;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;

public final class Kiosk extends Activity {
    WebView web;
    public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        web=new WebView(this);setContentView(web);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        String host=getIntent().getData().getEncodedAuthority();
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                return !("https".equals(req.getUrl().getScheme()) && host.equals(req.getUrl().getEncodedAuthority()));
            }
        });
        // The HA legacy frontend requires Chromium 67+ (BigInt). Probe before opening a login session.
        web.evaluateJavascript("typeof BigInt === 'function' && typeof customElements === 'object'", supported->{
            if("true".equals(supported))web.loadUrl(getIntent().getDataString());
            else {
                android.widget.TextView error=new android.widget.TextView(this);error.setTextSize(24);
                error.setGravity(android.view.Gravity.CENTER);
                error.setText("Этот WebView не поддерживает функции интерфейса HA.\nНажмите Back для нативной панели.");
                setContentView(error);
            }
        });
    }
    public void onDestroy(){web.stopLoading();web.clearHistory();web.clearCache(true);
        WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(null);
        web.destroy();super.onDestroy();}
}
