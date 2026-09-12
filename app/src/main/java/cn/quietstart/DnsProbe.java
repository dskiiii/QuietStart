package cn.quietstart;

import java.io.*;
import java.net.*;
import java.util.Arrays;

/** Explicit queries to the virtual server, not a test of the target app's DNS behavior. */
public final class DnsProbe {
    public static byte[] query(String name,int id) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(id); out.writeShort(0x100); out.writeShort(1); out.writeShort(0); out.writeShort(0); out.writeShort(0);
        for(String label:name.split("\\.")) {out.writeByte(label.length());out.writeBytes(label);}
        out.writeByte(0); out.writeShort(1); out.writeShort(1); return bytes.toByteArray();
    }
    public static String check(String name,int expectedRcode) {
        try(DatagramSocket socket=new DatagramSocket()) {
            byte[] query=query(name,new java.security.SecureRandom().nextInt(65536));
            socket.connect(InetAddress.getByName("10.77.0.2"),53);socket.setSoTimeout(6000);
            socket.send(new DatagramPacket(query,query.length));
            byte[] buffer=new byte[8192]; DatagramPacket packet=new DatagramPacket(buffer,buffer.length);socket.receive(packet);
            byte[] reply=Arrays.copyOf(buffer,packet.getLength());
            if(!DnsPacket.validReply(query,reply)) return "失败：收到不匹配的 DNS 响应";
            int code=reply[3]&15;
            if(code!=expectedRcode) return "失败：DNS 返回码 "+code;
            if(expectedRcode==0 && DnsPacket.u16(reply,6)==0) return "失败：上游未返回答案";
            return "通过";
        } catch(IOException e) {return "失败："+e.getClass().getSimpleName()+"（无响应时检查 VPN / 私人 DNS / 网络）";}
    }
}
