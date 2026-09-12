package cn.quietstart;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** HTTPS download, format validation, atomic commit; failure never replaces the previous list. */
public final class RuleRepository {
    public static final java.util.concurrent.atomic.AtomicBoolean updating=new java.util.concurrent.atomic.AtomicBoolean();
    public static final String URL="https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt";
    private static final int LIMIT=10*1024*1024;
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"anti-ad.txt"));}
    public static synchronized Subscription.Parsed current(Context c) throws IOException {
        AtomicFile disk=file(c);
        if(disk.getBaseFile().exists()) {
            try(InputStream in=disk.openRead()){return Subscription.parse(Subscription.readBounded(in,LIMIT));}
            catch(IOException|IllegalArgumentException e){/* Corrupt cache: use packaged, validated snapshot. */}
        }
        try(InputStream in=c.getAssets().open("anti-ad-full.txt")){return Subscription.parse(Subscription.readBounded(in,LIMIT));}
    }
    public static Subscription.Parsed update(Context c) throws IOException {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(URL).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        Subscription.Parsed parsed;
        try {
            if(connection.getResponseCode()!=200)throw new IOException("规则源响应 "+connection.getResponseCode()+"，原规则未改变");
            if(connection.getContentLengthLong()>LIMIT)throw new IOException("规则文件过大，原规则未改变");
            try(InputStream in=connection.getInputStream()){parsed=Subscription.parse(Subscription.readBounded(in,LIMIT));}
        } finally {connection.disconnect();}
        synchronized(RuleRepository.class) {
            AtomicFile disk=file(c);FileOutputStream out=null;
            try {out=disk.startWrite();out.write(parsed.text().getBytes(StandardCharsets.UTF_8));disk.finishWrite(out);}
            catch(IOException e){if(out!=null)disk.failWrite(out);throw e;}
        }
        return parsed;
    }
}
