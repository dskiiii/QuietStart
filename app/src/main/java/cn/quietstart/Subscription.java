package cn.quietstart;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Plain-domain subscriptions only; never interpret downloaded content as executable rules. */
public final class Subscription {
    public record Parsed(String text,int count,int rejected,String version) {}
    public static Parsed parse(String source) throws IOException {
        Set<String> domains=new LinkedHashSet<>(); int rejected=0; String version="未知版本";
        try(BufferedReader reader=new BufferedReader(new StringReader(source))) {
            String line;
            while((line=reader.readLine())!=null) {
                line=line.trim();
                if(line.startsWith("#VER=")) version=line.substring(5).replaceAll("[^0-9A-Za-z._-]","");
                if(line.isEmpty()||line.startsWith("#"))continue;
                try {domains.add(Rules.normalize(line));} catch(IllegalArgumentException e) {rejected++;}
                if(domains.size()>500000)throw new IOException("规则数量超过上限");
            }
        }
        if(domains.size()<1000 || rejected>Math.max(10,domains.size()/100)) throw new IOException("订阅格式或规则数量异常，保留原规则");
        return new Parsed("#VER="+version+"\n"+String.join("\n",domains)+"\n",domains.size(),rejected,version);
    }
    public static String readBounded(InputStream in,int maximum) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1) {if(out.size()+count>maximum)throw new IOException("订阅文件超过大小上限");out.write(buffer,0,count);}
        return out.toString(StandardCharsets.UTF_8);
    }
}
