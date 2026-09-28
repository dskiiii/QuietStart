package cn.quietstart;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public final class ImportRepository {
    public static final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"imported-domains.txt"));}
    public static synchronized String current(Context c) throws IOException {
        AtomicFile disk=file(c);
        if(!disk.getBaseFile().exists())return "";
        // The caller validates once while separating blocking and exception rules.
        try(InputStream in=disk.openRead()){return Subscription.readBounded(in,ImportedRules.LIMIT);}
    }
    public static synchronized void save(Context c,String text) throws IOException {
        AtomicFile disk=file(c);FileOutputStream out=null;
        try {out=disk.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));disk.finishWrite(out);}
        catch(IOException e){if(out!=null)disk.failWrite(out);throw e;}
    }
    public static ImportedRules.Parsed download(String address) throws IOException {
        URL url=new URL(address);
        if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null)throw new IOException("请填写不含账号密码的 HTTPS 规则直链");
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        try {
            if(connection.getResponseCode()!=200)throw new IOException("下载响应 "+connection.getResponseCode()+"，请使用最终 HTTPS 直链");
            if(connection.getContentLengthLong()>ImportedRules.LIMIT)throw new IOException("规则文件超过 10 MB 上限");
            try(InputStream in=connection.getInputStream()){return ImportedRules.parse(Subscription.readBounded(in,ImportedRules.LIMIT));}
        }finally{connection.disconnect();}
    }
}
