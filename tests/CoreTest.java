package cn.quietstart;
import java.io.*;
import java.util.*;

public class CoreTest {
    private static int checks;
    static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    static byte[] query(String name,int type) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(0x1234);out.writeShort(0x0100);out.writeShort(1);out.writeShort(0);out.writeShort(0);out.writeShort(0);
        for(String part:name.split("\\.")){out.writeByte(part.length());out.writeBytes(part);}out.writeByte(0);out.writeShort(type);out.writeShort(1);
        return bytes.toByteArray();
    }
    static byte[] ip(byte[] dns) {
        byte[] b=new byte[28+dns.length]; b[0]=0x45;b[8]=64;b[9]=17;DnsPacket.put16(b,2,b.length);
        b[12]=10;b[13]=77;b[15]=1;b[16]=10;b[17]=77;b[19]=2;
        DnsPacket.put16(b,20,41234);DnsPacket.put16(b,22,53);DnsPacket.put16(b,24,dns.length+8);
        System.arraycopy(dns,0,b,28,dns.length);DnsPacket.put16(b,10,DnsPacket.checksum(b,0,20));return b;
    }
    public static void main(String[] args) throws Exception {
        long subscriptionStart=System.nanoTime();
        String fullSource=java.nio.file.Files.readString(java.nio.file.Path.of("app/src/main/assets/anti-ad-full.txt"));
        Subscription.Parsed subscription=Subscription.parse(fullSource);
        Rules universal=new Rules(subscription.text(),"","");
        check(subscription.count()>100000,"full subscription is packaged");
        for(String domain:new String[]{"ef-dongfeng.tanx.com","df.tanx.com","sdk.beizi.biz","sdk.1rtb.net","afd.baidu.com","ads-img-al.xhscdn.com"})
            check(universal.blocks(domain),"universal list covers observed platform: "+domain);
        for(String domain:new String[]{"interface3.music.163.com.163jiasu.com","api2.music.163.com","p1.music.126.net","p6.music.126.net","d1.music.126.net","route.map.baidu.com","cnloc.map.baidu.com","wappass.baidu.com"})
            check(!universal.blocks(domain),"retain observed content service: "+domain);
        check(!new Rules(subscription.text(),"","df.tanx.com").blocks("df.tanx.com"),"subscription whitelist override");
        for(String invalid:new String[]{"<html>Service unavailable</html>","example.com", "bad line\n".repeat(2000)}) {
            try{Subscription.parse(invalid);throw new AssertionError("accepted broken subscription");}catch(IOException expected){checks++;}
        }
        Subscription.Parsed tolerated=Subscription.parse(subscription.text()+"invalid entry\n");
        check(tolerated.count()==subscription.count()&&tolerated.rejected()==1,"isolated invalid row skipped");
        check(Subscription.parse(subscription.text()).text().equals(subscription.text()),"normalized subscription round trip");
        try{Subscription.readBounded(new ByteArrayInputStream(new byte[11]),10);throw new AssertionError("oversize download accepted");}catch(IOException expected){checks++;}
        check(Subscription.readBounded(new ByteArrayInputStream(new byte[10]),10).length()==10,"download exact limit");
        System.out.println("Subscription: "+subscription.count()+" domains, rejected="+subscription.rejected()+", version="+subscription.version()+", test elapsed ms="+(System.nanoTime()-subscriptionStart)/1000000);
        String bundled=java.nio.file.Files.readString(java.nio.file.Path.of("app/src/main/assets/default-domains.txt"));
        Rules actual=new Rules(bundled,"","");
        for(String host:new String[]{"sdk.1rtb.net","s.1rtb.net","sdk.beizi.biz","api-htp.beizi.biz","adx-cfg-u1.ubixioe.com","entry-su1.ubixioe.com","adx-data-u1.ubixioe.com","sdk.adx.adwangmai.com","sdk.zhangyuyidong.cn","oss.cdn.adintl.cn","afd.baidu.com","afdconf.baidu.com"}) {
            check(actual.blocks(host),"observed ad endpoint: "+host);
            check(!actual.blocks(host+".example.org"),"observed endpoint boundary: "+host);
        }
        for(String host:new String[]{"newclient.map.baidu.com","client.map.baidu.com","route.map.baidu.com","cnloc.map.baidu.com","ofloc.map.baidu.com","offnavi.map.baidu.com","wappass.baidu.com","tts.baidu.com","www.baidu.com","ecom.map.baidu.com","bdloc.qchannel03.cn","app.duxiaoman.com"})
            check(!actual.blocks(host),"preserve observed non-target endpoint: "+host);
        check(!new Rules(bundled,"","sdk.beizi.biz").blocks("sdk.beizi.biz"),"observed endpoint can be allowed");
        Rules rules=new Rules("ads.example.com\n# comment\n", "custom.example.org", "safe.ads.example.com");
        check(rules.blocks("ads.example.com"),"exact block");check(rules.blocks("a.ads.example.com"),"subdomain block");
        check(!rules.blocks("badads.example.com"),"label boundary");check(!rules.blocks("ads.example.com.evil.org"),"suffix boundary");
        check(!rules.blocks("safe.ads.example.com"),"allow precedence");check(!rules.blocks("x.safe.ads.example.com"),"allow subdomains");
        check(rules.blocks("ADS.EXAMPLE.COM."),"case trailing dot");check(rules.blocks("custom.example.org"),"custom rule");
        for(String bad:new String[]{"https://example.com/path","*.example.com","com","x..com","bad host.com"}) {
            try {Rules.parse(bad);throw new AssertionError("accepted "+bad);}catch(IllegalArgumentException expected){checks++;}
        }
        byte[] q=query("ads.example.com",1), packet=ip(q);
        check(Arrays.equals(q,DnsProbe.query("ads.example.com",0x1234)),"self-test query wire format");
        check(DnsPacket.question(q).name().equals("ads.example.com"),"query parse");
        check(Arrays.equals(DnsPacket.udpQuery(packet),q),"packet decode");
        byte[] deny=DnsPacket.failure(q,3);
        check((deny[3]&15)==3 && (deny[2]&128)!=0,"NXDOMAIN flags");
        check(DnsPacket.u16(deny,4)==1 && DnsPacket.u16(deny,6)==0,"counts");
        check(DnsPacket.validReply(q,deny),"matching response");
        byte[] wrong=deny.clone();wrong[0]++;check(!DnsPacket.validReply(q,wrong),"transaction validation");
        check(!DnsPacket.validReply(query("other.example.com",1),deny),"question validation");
        byte[] answer=DnsPacket.udpReply(packet,deny);
        check(DnsPacket.checksum(answer,0,20)==0,"IP checksum");
        check(answer[15]==2 && answer[19]==1,"reverse IPs");
        check(DnsPacket.u16(answer,20)==53 && DnsPacket.u16(answer,22)==41234,"reverse ports");
        check(DnsPacket.u16(answer,2)==answer.length && DnsPacket.u16(answer,24)==deny.length+8,"lengths");
        byte[] ipv6Query=query("ads.example.com",28);check(DnsPacket.question(ipv6Query).type()==28,"AAAA query");
        check(DnsPacket.validReply(ipv6Query,DnsPacket.failure(ipv6Query,3)),"AAAA block");
        for(int n=0;n<q.length;n++) {try{DnsPacket.question(Arrays.copyOf(q,n));throw new AssertionError("accepted truncated DNS "+n);}catch(IOException expected){checks++;}}
        byte[] compressed=q.clone();compressed[12]=(byte)0xc0;try{DnsPacket.question(compressed);throw new AssertionError();}catch(IOException expected){checks++;}
        byte[] fragmented=packet.clone();fragmented[6]=0x20;try{DnsPacket.udpQuery(fragmented);throw new AssertionError();}catch(IOException expected){checks++;}
        Random random=new Random(8841);
        for(int n=0;n<10000;n++) {
            byte[] fuzz=new byte[random.nextInt(300)];random.nextBytes(fuzz);
            try{DnsPacket.question(fuzz);}catch(IOException expected){} // must not leak bounds exceptions
            try{DnsPacket.udpQuery(fuzz);}catch(IOException expected){}
            checks++;
        }
        System.out.println("PASS: "+checks+" checks (rules, DNS A/AAAA, NXDOMAIN, IP/UDP framing, malformed input fuzz)");
    }
}
