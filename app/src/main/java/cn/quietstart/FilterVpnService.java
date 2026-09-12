package cn.quietstart;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import android.system.OsConstants;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class FilterVpnService extends VpnService {
    public static final String STOP="cn.quietstart.STOP", RELOAD="cn.quietstart.RELOAD";
    public static final String STATE_CHANGED="cn.quietstart.STATE_CHANGED";
    public static volatile boolean stopping=false;
    private static FilterVpnService instance;
    private volatile int generation;
    @Override public void onCreate(){super.onCreate();instance=this;}
    public static void notifyState(Context c){c.sendBroadcast(new Intent(STATE_CHANGED).setPackage(c.getPackageName()));}
    public static void pause(Context c){
        prefs(c).edit().putBoolean("user_paused",true).apply();
        running=false;starting=false;stopping=true;status="正在暂停…";notifyState(c);
        // stopService alone cannot destroy a service still bound by the VPN system.
        FilterVpnService service=instance;
        if(service!=null){service.stopFiltering();service.stopSelf();}
        else {c.stopService(new Intent(c,FilterVpnService.class));stopping=false;status="已暂停";notifyState(c);}
    }
    public static final String[] PACKAGES={"com.xingin.xhs","com.netease.cloudmusic","com.baidu.BaiduMap"};
    public static final String[] NAMES={"小红书","网易云音乐","百度地图"};
    public static volatile boolean running=false;
    public static volatile boolean starting=false;
    public static volatile String status="尚未开启", scope="";
    public static volatile String ruleInfo="开启后显示当前规则版本";
    private final ExecutorService ruleWorker=new ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    public static final AtomicLong blocked=new AtomicLong(), passed=new AtomicLong(), failed=new AtomicLong();
    public static final AtomicLong unsupported=new AtomicLong();
    public static final String SELF_TEST="quietstart-selftest.invalid";
    private static final ArrayDeque<Event> diagnosticEvents=new ArrayDeque<>();
    private static long diagnosticDeadline, startBlocked, startPassed, startFailed, startUnsupported;
    private static String diagnosticApp="", diagnosticTime="";
    private static int diagnosticDropped;
    public record Event(String time,String domain,String action) {}
    private static final ArrayDeque<Event> events=new ArrayDeque<>();
    private volatile Rules rules;
    private volatile boolean closed;
    private ParcelFileDescriptor tun;
    private Thread reader;
    private TunWaiter waiter;
    private ThreadPoolExecutor pool;
    private final Set<Closeable> sockets=ConcurrentHashMap.newKeySet();
    private final Object writeLock=new Object();

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings",MODE_PRIVATE); }
    public static String defaults(Context c) throws IOException {
        try(InputStream in=c.getAssets().open("default-domains.txt")) { return new String(in.readAllBytes(),StandardCharsets.UTF_8); }
    }
    public static synchronized List<Event> events() { return new ArrayList<>(events); }
    public static synchronized void clearEvents() { events.clear(); }
    public static synchronized void beginDiagnostic(String app) {
        diagnosticEvents.clear(); diagnosticDropped=0; diagnosticApp=app;
        diagnosticTime=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date());
        startBlocked=blocked.get(); startPassed=passed.get(); startFailed=failed.get(); startUnsupported=unsupported.get();
        diagnosticDeadline=SystemClock.elapsedRealtime()+60_000;
    }
    public static synchronized boolean diagnosing() {return diagnosticDeadline>SystemClock.elapsedRealtime();}
    public static synchronized String finishDiagnostic() {
        if(diagnosticApp.isEmpty()) return "尚未开始复现记录。";
        diagnosticDeadline=0;
        StringBuilder result=new StringBuilder("静启诊断 0.3.2\n开始时间：").append(diagnosticTime)
            .append("\n复现应用：").append(diagnosticApp).append("\n设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("\nAndroid：").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
            .append("\n服务：").append(status).append("\n过滤范围：").append(scope).append("\n规则：").append(ruleInfo)
            .append("\n计数变化（开始到生成报告）：拦截 ").append(blocked.get()-startBlocked)
            .append(" / 放行 ").append(passed.get()-startPassed).append(" / 失败 ").append(failed.get()-startFailed)
            .append(" / 不支持报文 ").append(unsupported.get()-startUnsupported)
            .append("\n采样：最多 60 秒、600 条；超限丢弃 ").append(diagnosticDropped)
            .append("\n注意：域名可能来自范围中的其他应用，无法归属具体页面。\n");
        for(Event event:diagnosticEvents) result.append(event.time()).append('\t').append(event.action()).append('\t').append(event.domain()).append('\n');
        return result.toString();
    }
    private static synchronized void log(String domain,String action) {
        if(events.size()>=120) events.removeLast();
        Event event=new Event(new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date()),domain,action);
        events.addFirst(event);
        if(diagnosing()) {
            if(diagnosticEvents.size()<600) diagnosticEvents.addLast(event); else diagnosticDropped++;
        }
    }
    private void loadRules() throws IOException {
        int session=generation;
        SharedPreferences p=prefs(this);
        String base=defaults(this), info="精简兼容规则";
        if(p.getBoolean("full_rules",true)) {
            Subscription.Parsed full=RuleRepository.current(this);
            base=full.text()+base;
            info="anti-AD 完整库 · "+full.version()+" · 跳过无效行 "+full.rejected();
        }
        Rules next=new Rules(base,p.getString("block",""),p.getString("allow",""));
        if(!closed&&session==generation){rules=next;ruleInfo=info+" · "+next.size()+" 条";}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null ? "" : intent.getAction();
        if(STOP.equals(action)) {pause(this);return START_NOT_STICKY;}
        if(prefs(this).getBoolean("user_paused",false)){
            stopFiltering();stopSelf();return START_NOT_STICKY;
        }
        if(RELOAD.equals(action)) {
            if(tun==null) { stopSelf(); return START_NOT_STICKY; }
            ruleWorker.execute(()->{try {loadRules();}catch(Exception e){log("规则","加载失败，继续使用原规则");}});
            return START_STICKY;
        }
        if(tun!=null) return START_STICKY;
        try {
            if(VpnService.prepare(this)!=null) throw new IOException("需要重新授权 VPN");
            Builder builder=new Builder().setSession("静启 · 开屏广告过滤")
                .setMtu(32767).addAddress("10.77.0.1",32).addDnsServer("10.77.0.2")
                .addRoute("10.77.0.2",32).allowFamily(OsConstants.AF_INET6).setBlocking(true)
                .setConfigureIntent(openIntent());
            builder.setMetered(false);
            boolean all=prefs(this).getBoolean("all_apps",false);
            int count=0; StringJoiner names=new StringJoiner("、");
            for(int i=0;!all&&i<PACKAGES.length;i++) {
                if(!prefs(this).getBoolean(PACKAGES[i],true)) continue;
                try { builder.addAllowedApplication(PACKAGES[i]); count++; names.add(NAMES[i]); }
                catch(android.content.pm.PackageManager.NameNotFoundException ignored) {}
            }
            if(!all&&count==0) throw new IOException("请先安装并勾选至少一个目标应用");
            // Route this app's explicit virtual-DNS self-test; normal traffic remains outside the VPN.
            if(!all)builder.addAllowedApplication(getPackageName());
            tun=builder.establish();
            if(tun==null) throw new IOException("系统没有建立 VPN，请重新授权");
            waiter=new TunWaiter(tun.getFileDescriptor());
            showForeground();
            scope=all?"所有应用":names.toString(); closed=false; starting=true; status="正在载入广告规则…";
            stopping=false;notifyState(this);
            pool=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128));
            pool.allowCoreThreadTimeOut(true);
            final int session=++generation;
            final ParcelFileDescriptor sessionTun=tun;
            final TunWaiter sessionWaiter=waiter;
            final ThreadPoolExecutor sessionPool=pool;
            ruleWorker.execute(()->{
                try {
                    loadRules();if(closed)return;
                    new Handler(Looper.getMainLooper()).post(()->{
                        if(closed||session!=generation||prefs(this).getBoolean("user_paused",false))return;
                        starting=false;running=true;status="过滤运行中";
                        notifyState(this);
                        reader=new Thread(()->readLoop(session,sessionTun,sessionWaiter,sessionPool),"QuietStart-DNS");reader.start();
                    });
                }catch(Exception e){new Handler(Looper.getMainLooper()).post(()->{if(!closed&&session==generation){status="规则加载失败："+e.getMessage();stopFiltering();stopSelf();}});}
            });
            return START_STICKY;
        } catch(Exception e) {
            status="启动失败："+e.getMessage();stopFiltering();stopSelf();return START_NOT_STICKY;
        }
    }
    private PendingIntent openIntent() {
        return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void showForeground() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("filter","广告过滤运行状态",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,"filter").setSmallIcon(cn.quietstart.R.drawable.ic_shield)
            .setContentTitle("静启正在过滤广告域名").setContentText("点击打开静启 · 暂停请在软件内操作")
            .setContentIntent(openIntent()).setOngoing(true).setOnlyAlertOnce(true).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        else startForeground(1,n);
    }
    private void readLoop(int session,ParcelFileDescriptor tun,TunWaiter waiter,ThreadPoolExecutor pool) {
        try(FileInputStream input=new FileInputStream(tun.getFileDescriptor());
            FileOutputStream output=new FileOutputStream(tun.getFileDescriptor())) {
            byte[] buffer=new byte[32767];
            while(!closed&&session==generation) {
                if(!waiter.awaitPacket()) break;
                if(closed||session!=generation) break;
                int length=input.read(buffer);
                if(length<0) break;
                if(length==0) continue;
                byte[] packet=Arrays.copyOf(buffer,length), query;
                try { query=DnsPacket.udpQuery(packet); DnsPacket.question(query); }
                catch(IOException invalid) { unsupported.incrementAndGet(); continue; }
                try { pool.execute(()->{if(session==generation)process(packet,query,output);}); }
                catch(RejectedExecutionException overloaded) {
                    if(!closed) { failed.incrementAndGet(); writeReply(output,packet,DnsPacket.failure(query,2)); }
                }
            }
            readerStopped(session,"VPN 接口已关闭，请重新开启");
        } catch(Exception e) {
            readerStopped(session,"过滤中断，请重新开启");
        } finally {if(waiter!=null)waiter.close();}
    }
    private void readerStopped(int session,String message){
        new Handler(Looper.getMainLooper()).post(()->{
            if(!closed&&session==generation){status=message;stopFiltering();stopSelf();}
        });
    }
    private void process(byte[] packet,byte[] query,FileOutputStream output) {
        if(closed) return;
        try {
            String name=DnsPacket.question(query).name();
            byte[] response;
            if(SELF_TEST.equals(name) || rules.blocks(name)) {
                response=DnsPacket.failure(query,3); blocked.incrementAndGet();
                if(diagnosing() || prefs(this).getBoolean("diagnostics",false))log(name,"已拦截");
            } else {
                try { response=resolve(query); passed.incrementAndGet(); if(diagnosing() || prefs(this).getBoolean("diagnostics",false)) log(name,"已放行"); }
                catch(IOException e) { response=DnsPacket.failure(query,2); failed.incrementAndGet(); log(name,"解析失败"); }
            }
            writeReply(output,packet,response);
        } catch(IOException ignored) { failed.incrementAndGet(); }
    }
    private void writeReply(FileOutputStream out,byte[] packet,byte[] dns) throws IOException {
        synchronized(writeLock) { if(!closed) out.write(DnsPacket.udpReply(packet,dns)); }
    }
    private byte[] resolve(byte[] query) throws IOException {
        IOException last=new IOException("No upstream");
        for(String server:new String[]{"223.5.5.5","119.29.29.29"}) {
            if(closed) throw new IOException("Stopped");
            try(DatagramSocket socket=new DatagramSocket()) {
                sockets.add(socket);
                try {
                    if(closed || !protect(socket)) throw new IOException("Protect failed");
                    socket.connect(InetAddress.getByName(server),53); socket.setSoTimeout(1800);
                    socket.send(new DatagramPacket(query,query.length));
                    byte[] buffer=new byte[8192]; DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                    socket.receive(reply);
                    byte[] bytes=Arrays.copyOf(buffer,reply.getLength());
                    if(!DnsPacket.validReply(query,bytes)) throw new IOException("Invalid upstream reply");
                    if((bytes[2]&2)!=0) return resolveTcp(server,query);
                    return bytes;
                } finally { sockets.remove(socket); }
            } catch(IOException e) { last=e; }
        }
        throw last;
    }
    private byte[] resolveTcp(String server,byte[] query) throws IOException {
        try(Socket socket=new Socket()) {
            sockets.add(socket);
            try {
                if(closed || !protect(socket)) throw new IOException("Protect failed");
                socket.connect(new InetSocketAddress(server,53),1800); socket.setSoTimeout(1800);
                DataOutputStream out=new DataOutputStream(socket.getOutputStream()); out.writeShort(query.length); out.write(query); out.flush();
                DataInputStream in=new DataInputStream(socket.getInputStream()); int length=in.readUnsignedShort();
                if(length>8192 || length<12) throw new IOException("DNS response too large");
                byte[] reply=new byte[length]; in.readFully(reply);
                if(!DnsPacket.validReply(query,reply)) throw new IOException("Invalid TCP DNS");
                return reply;
            } finally { sockets.remove(socket); }
        }
    }
    @Override public void onRevoke() {new Handler(Looper.getMainLooper()).post(()->{status="VPN 授权已撤销或被其他 VPN 替换";stopFiltering();stopSelf();});}
    private void stopFiltering() {
        generation++;
        closed=true; running=false; starting=false;
        if(waiter!=null) {if(reader==null)waiter.close();else waiter.signalStop();}
        if(pool!=null) pool.shutdownNow();
        for(Closeable socket:sockets) try { socket.close(); } catch(IOException ignored) {}
        sockets.clear();
        if(tun!=null) try { tun.close(); } catch(IOException ignored) {}
        if(reader!=null) reader.interrupt();
        tun=null;reader=null;waiter=null;pool=null; stopForeground(STOP_FOREGROUND_REMOVE);
        stopping=false;
        if(prefs(this).getBoolean("user_paused",false))status="已暂停";
        else if("过滤运行中".equals(status)||"正在载入广告规则…".equals(status)) status="已停止";
        notifyState(this);
    }
    @Override public void onDestroy() {
        stopFiltering();
        ruleWorker.shutdownNow();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
