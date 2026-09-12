package cn.quietstart;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Strict single-question DNS parser and IPv4/UDP packet encoder. */
public final class DnsPacket {
    public record Question(String name, int type, int end) {}
    public static int u16(byte[] b, int p) { return ((b[p]&255)<<8)|(b[p+1]&255); }
    public static void put16(byte[] b, int p, int n) { b[p]=(byte)(n>>>8); b[p+1]=(byte)n; }
    public static Question question(byte[] b) throws IOException {
        if (b.length < 17 || (b[2]&0xf8)!=0 || u16(b,4)!=1) throw new IOException("Unsupported DNS query");
        StringBuilder name = new StringBuilder();
        int p=12;
        while (true) {
            if (p>=b.length) throw new IOException("Truncated name");
            int n=b[p++]&255;
            if (n==0) break;
            if (n>63 || p+n>b.length) throw new IOException("Invalid label");
            if (name.length()>0) name.append('.');
            for (int i=0;i<n;i++) {
                int c=b[p+i]&255;
                if (c<=32 || c>=127 || c=='.') throw new IOException("Invalid label character");
            }
            name.append(new String(b,p,n,StandardCharsets.US_ASCII)); p+=n;
            if (name.length()>253) throw new IOException("Name too long");
        }
        if (p+4>b.length || u16(b,p+2)!=1) throw new IOException("Invalid question");
        return new Question(name.toString().toLowerCase(java.util.Locale.ROOT),u16(b,p),p+4);
    }
    public static byte[] failure(byte[] query, int rcode) throws IOException {
        Question q=question(query);
        byte[] reply=Arrays.copyOf(query,q.end());
        reply[2]=(byte)(0x80|(query[2]&1)); reply[3]=(byte)(0x80|rcode);
        for(int i=6;i<12;i++) reply[i]=0;
        return reply;
    }
    public static boolean validReply(byte[] query, byte[] reply) {
        try {
            if (reply.length<12 || u16(query,0)!=u16(reply,0) || (reply[2]&0x80)==0) return false;
            byte[] copy=reply.clone(); copy[2]&=1;
            Question a=question(query), b=question(copy);
            return a.name().equals(b.name()) && a.type()==b.type();
        } catch(IOException e) { return false; }
    }
    public static byte[] udpQuery(byte[] ip) throws IOException {
        if(ip.length<28 || (ip[0]>>>4 &15)!=4) throw new IOException("Not IPv4");
        int h=(ip[0]&15)*4, total=u16(ip,2);
        if(h<20 || total>ip.length || total<h+8 || (ip[9]&255)!=17 || (u16(ip,6)&0x3fff)!=0) throw new IOException("Invalid IP");
        if(ip[16]!=(byte)10 || ip[17]!=(byte)77 || ip[18]!=0 || ip[19]!=2 || u16(ip,h+2)!=53) throw new IOException("Not virtual DNS");
        int len=u16(ip,h+4);
        if(len<20 || h+len>total) throw new IOException("Invalid UDP length");
        return Arrays.copyOfRange(ip,h+8,h+len);
    }
    public static byte[] udpReply(byte[] request, byte[] dns) {
        byte[] reply=new byte[28+dns.length];
        reply[0]=0x45; put16(reply,2,reply.length); reply[8]=64; reply[9]=17;
        System.arraycopy(request,16,reply,12,4); System.arraycopy(request,12,reply,16,4);
        int h=(request[0]&15)*4;
        put16(reply,20,53); put16(reply,22,u16(request,h)); put16(reply,24,8+dns.length);
        System.arraycopy(dns,0,reply,28,dns.length);
        put16(reply,10,checksum(reply,0,20)); // UDP zero checksum permitted for IPv4.
        return reply;
    }
    public static int checksum(byte[] b,int off,int len) {
        long sum=0;
        for(int i=0;i<len;i+=2) sum+=((b[off+i]&255)<<8)|(i+1<len ? b[off+i+1]&255:0);
        while((sum>>>16)!=0) sum=(sum&65535)+(sum>>>16);
        return (int)(~sum)&65535;
    }
}
