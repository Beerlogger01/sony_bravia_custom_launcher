package house.panel;

import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

final class TvApi {
    static JSONObject post(String endpoint, String method, JSONObject data, String token) throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(endpoint+"/v1/"+method).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000); c.setReadTimeout(10000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
            byte[] bytes=data.toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            if(status!=200)throw new IOException(status==429?"Слишком много попыток. Подождите три минуты":
                status==401?"Код истёк или доступ отозван":status==403?"Действие запрещено":"Ошибка сервера "+status);
            try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){
                return new JSONObject(in.readLine());
            }
        } finally { c.disconnect(); }
    }
    private TvApi(){}
}
