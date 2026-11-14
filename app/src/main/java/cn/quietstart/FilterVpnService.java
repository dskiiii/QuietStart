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
    private static volatile FilterVpnService instance;
    private volatile int generation;
    private static final Handler stateHandler=new Handler(Looper.getMainLooper());
    private static Runnable startTimeout;
    public static volatile boolean waitingForVpn,needsConsent;
    private VpnMonitor vpnMonitor;
    private boolean destroyed;
    public static volatile boolean healthChecking,healthProblem;
    public static volatile long lastHealthCheck;
    public static volatile String healthResult="尚未检查";
    private boolean foregroundActive;
    private long lastCheckElapsed=-1,lastRepairElapsed=-1;
    private final ThreadPoolExecutor healthWorker=new ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));
    private final BroadcastReceiver wakeReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            PowerManager power=getSystemService(PowerManager.class);
            if(power==null||!power.isDeviceIdleMode())requestHealthCheck(FilterVpnService.this);
        }
    };
    private final Handler handoffHandler=new Handler(Looper.getMainLooper());
    private final Runnable resumeAfterVpn=()->{
        if(destroyed||!waitingForVpn||needsConsent||prefs(this).getBoolean("user_paused",false))return;
        try {
            if(vpnMonitor==null||vpnMonitor.blocksStart()||VpnMonitor.otherVpnPresent(this))return;
            // Consume this observed disconnect once. Failures/consent do not retry in a loop.
            prefs(this).edit().putBoolean("handoff_seen",false).putBoolean("handoff_waiting",false).apply();
            waitingForVpn=false;
            startFiltering();
        }catch(RuntimeException error){handoffError(error);}
    };
    private static void cancelStartTimeout(){
        if(startTimeout!=null)stateHandler.removeCallbacks(startTimeout);
        startTimeout=null;
    }
    private static void watchStart(Context context){
        if(startTimeout!=null)return;
        Context app=context.getApplicationContext();
        startTimeout=()->{
            startTimeout=null;
            if(!starting)return;
            status="启动超时，过滤未开启，请重试";
            FilterVpnService service=instance;
            if(service!=null){service.stopFiltering();service.stopSelf();}
            else {starting=false;running=false;stopping=false;app.stopService(new Intent(app,FilterVpnService.class));notifyState(app);}
        };
        stateHandler.postDelayed(startTimeout,20000);
    }
    public static void start(Context context){
        prefs(context).edit().putBoolean("filter_requested",true).putBoolean("user_paused",false).putBoolean("handoff_waiting",false).putBoolean("handoff_seen",false).remove("external_vpn_mode").apply();
        needsConsent=false;waitingForVpn=false;
        starting=true;status="正在启动…";watchStart(context);
        try{context.startForegroundService(new Intent(context,FilterVpnService.class));}
        catch(RuntimeException e){cancelStartTimeout();starting=false;status="启动失败："+e.getClass().getSimpleName();notifyState(context);throw e;}
    }
    @Override public void onCreate(){
        super.onCreate();instance=this;healthChecking=false;healthProblem=false;lastHealthCheck=0;healthResult="尚未检查";
        IntentFilter wake=new IntentFilter(Intent.ACTION_USER_PRESENT);wake.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        registerReceiver(wakeReceiver,wake,Context.RECEIVER_NOT_EXPORTED);
    }
    public static void notifyState(Context c){
        c.sendBroadcast(new Intent(STATE_CHANGED).setPackage(c.getPackageName()));
        FilterVpnService current=instance;
        if(current!=null)current.handoffHandler.post(()->{if(instance==current&&!current.destroyed&&current.foregroundActive)current.showForeground();});
    }
    /** Event-triggered, coalesced local-TUN check; no repeating alarm or wake lock. */
    public static void requestHealthCheck(Context context){
        FilterVpnService current=instance;
        if(current!=null)current.handoffHandler.post(current::checkHealth);
        else if(running){running=false;healthProblem=true;status="过滤服务未运行，请重新开启";notifyState(context);}
    }
    /** Restore an explicit earlier start after process recreation, preserving VPN handoff state. */
    public static void restoreRequestedFiltering(Context context){
        SharedPreferences p=prefs(context);
        boolean requested=p.getBoolean("filter_requested",p.contains("user_paused")&&!p.getBoolean("user_paused",true));
        if(!requested||p.getBoolean("user_paused",false)||instance!=null||starting)return;
        p.edit().putBoolean("filter_requested",true).apply();
        running=false;starting=true;status="正在恢复此前开启的过滤";watchStart(context);
        try{context.startForegroundService(new Intent(context,FilterVpnService.class));}
        catch(RuntimeException denied){
            cancelStartTimeout();starting=false;healthProblem=true;status="系统未允许后台恢复，打开静启后重试";
            // Do not leave an old running notification after an unsuccessful restoration.
            NotificationManager nm=context.getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(1);
            notifyState(context);
        }
    }
    private void checkHealth(){
        if(destroyed||!running||starting||waitingForVpn||needsConsent||healthChecking||prefs(this).getBoolean("user_paused",false))return;
        long now=SystemClock.elapsedRealtime();
        if(lastCheckElapsed>=0&&now-lastCheckElapsed<30000)return;
        lastCheckElapsed=now;healthChecking=true;
        int session=generation;
        boolean alive=tun!=null&&!closed&&rules!=null&&reader!=null&&reader.isAlive();
        notifyState(this);
        try{healthWorker.execute(()->{
            String result;
            try{result=alive?probeLocalDns():"失败：过滤线程或接口已停止";}
            catch(RuntimeException failure){result="失败：本地链路检查中断";}
            String completed=result;handoffHandler.post(()->completeHealthCheck(session,completed));
        });}catch(RejectedExecutionException unavailable){healthChecking=false;healthResult="本次检查未执行，等待下次唤醒";notifyState(this);}
    }
    String probeLocalDns(){return DnsProbe.check(SELF_TEST,3);}
    private void completeHealthCheck(int session,String result){
        if(destroyed||closed||session!=generation||!running||waitingForVpn||prefs(this).getBoolean("user_paused",false))return;
        healthChecking=false;lastHealthCheck=System.currentTimeMillis();healthResult=result;
        if("通过".equals(result)){healthProblem=false;notifyState(this);return;}
        healthProblem=true;
        try{
            if((vpnMonitor!=null&&vpnMonitor.blocksStart())||VpnMonitor.otherVpnPresent(this)){
                yieldToVpn(vpnMonitor!=null&&vpnMonitor.hasOther());networksChanged();return;
            }
            long now=SystemClock.elapsedRealtime();
            // Repair only an OS-confirmed tunnel belonging to us, never take over
            // an unknown/revoked VPN. One attempt per minute bounds repeated failures.
            boolean repair=VpnMonitor.ownVpnPresent(this)&&(lastRepairElapsed<0||now-lastRepairElapsed>=60000);
            stopFiltering(false);
            if(repair){lastRepairElapsed=now;status="本地链路无响应，正在恢复过滤";startFiltering();}
            else {status="过滤链路未通过检查，请重新开启";showForeground();notifyState(this);}
        }catch(RuntimeException error){status="过滤检查失败，请重新开启";stopFiltering(false);showForeground();notifyState(this);}
    }
    public static void pause(Context c){
        SharedPreferences p=prefs(c);
        p.edit().putBoolean("filter_requested",false).putBoolean("user_paused",true).putBoolean("handoff_waiting",false).putBoolean("handoff_seen",false)
                .putLong("pause_epoch",p.getLong("pause_epoch",0)+1).apply();
        waitingForVpn=false;needsConsent=false;
        healthProblem=false;
        running=false;starting=false;stopping=true;status="正在暂停…";notifyState(c);
        // stopService alone cannot destroy a service still bound by the VPN system.
        FilterVpnService service=instance;
        if(service!=null){service.stopFiltering();service.stopSelf();}
        else {c.stopService(new Intent(c,FilterVpnService.class));cancelStartTimeout();stopping=false;status="已暂停";notifyState(c);}
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

    public static synchronized SharedPreferences prefs(Context c) {
        SharedPreferences p=c.getSharedPreferences("settings",MODE_PRIVATE);
        if(!p.getBoolean("general_scope_v1",false))p.edit().putBoolean("all_apps",true).putBoolean("general_scope_v1",true).apply();
        return p;
    }
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
        StringBuilder result=new StringBuilder("静启诊断 0.4.0\n开始时间：").append(diagnosticTime)
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
        String importedAllow="";
        if(p.getBoolean("import_enabled",true)) {
            String imported=ImportRepository.current(this);
            if(!imported.isEmpty()){
                ImportedRules.Parsed parsed=ImportedRules.parse(imported);
                StringBuilder blocks=new StringBuilder();for(String line:parsed.text().split("\n"))if(!line.startsWith("@@"))blocks.append(line).append('\n');
                base=base+"\n"+blocks;importedAllow=parsed.allowed();info+=" · 含导入规则";
            }
        }
        Rules next=new Rules(base,p.getString("block",""),p.getString("allow","")+"\n"+importedAllow);
        if(!closed&&session==generation){rules=next;ruleInfo=info+" · "+next.size()+" 条";}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null ? "" : intent.getAction();
        if(STOP.equals(action)) {pause(this);return START_NOT_STICKY;}
        if(prefs(this).getBoolean("user_paused",false)){
            stopFiltering();stopSelf();return START_NOT_STICKY;
        }
        if(RELOAD.equals(action)) {
            if(tun==null&&waitingForVpn)return START_STICKY;
            if(tun==null) { stopSelf(); return START_NOT_STICKY; }
            ruleWorker.execute(()->{try {loadRules();}catch(Exception e){log("规则","加载失败，继续使用原规则");}});
            return START_STICKY;
        }
        try {
            showForeground();
            if(vpnMonitor==null){vpnMonitor=new VpnMonitor(this,()->{
                try{networksChanged();}catch(RuntimeException error){handoffError(error);}
            });vpnMonitor.start(handoffHandler);}
            if(vpnMonitor.blocksStart()||VpnMonitor.otherVpnPresent(this)){
                yieldToVpn(vpnMonitor.hasOther());return START_STICKY;
            }
            if(prefs(this).getBoolean("handoff_waiting",false)){
                waitingForVpn=true;cancelStartTimeout();starting=false;
                status="等待其他 VPN 状态；可在设置中重新尝试开启";showForeground();notifyState(this);
                networksChanged();return START_STICKY;
            }
            return startFiltering();
        }catch(RuntimeException error){handoffError(error);return START_NOT_STICKY;}
    }
    private int startFiltering(){
        if(prefs(this).getBoolean("user_paused",false)||destroyed)return START_NOT_STICKY;
        if((vpnMonitor!=null&&vpnMonitor.blocksStart())||VpnMonitor.otherVpnPresent(this)){
            yieldToVpn(vpnMonitor!=null&&vpnMonitor.hasOther());return START_STICKY;
        }
        if(tun!=null){
            // A repeated Activity/OS start is not a new filtering session.
            if(!closed&&rules!=null&&reader!=null&&reader.isAlive()){
                starting=false;running=true;stopping=false;status="过滤运行中";
                cancelStartTimeout();notifyState(this);return START_STICKY;
            }
            if(!closed&&reader==null&&starting){
                status="正在载入广告规则…";watchStart(this);notifyState(this);return START_STICKY;
            }
            stopFiltering(false);
        }
        try {
            starting=true;running=false;watchStart(this);
            if(VpnService.prepare(this)!=null){
                cancelStartTimeout();starting=false;waitingForVpn=true;needsConsent=true;
                status="需要 VPN 授权，点击开启过滤继续";
                showForeground();notifyState(this);return START_STICKY;
            }
            waitingForVpn=false;needsConsent=false;
            prefs(this).edit().putBoolean("handoff_waiting",false).putBoolean("handoff_seen",false).apply();
            Builder builder=new Builder().setSession("静启 · 开屏广告过滤")
                .setMtu(32767).addAddress("10.77.0.1",32).addDnsServer("10.77.0.2")
                .addRoute("10.77.0.2",32).allowFamily(OsConstants.AF_INET6).setBlocking(true)
                .setConfigureIntent(openIntent());
            builder.setMetered(false);
            boolean all=prefs(this).getBoolean("all_apps",true);
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
                        cancelStartTimeout();showForeground();
                        notifyState(this);
                        reader=new Thread(()->readLoop(session,sessionTun,sessionWaiter,sessionPool),"QuietStart-DNS");reader.start();
                        checkHealth();
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
            .setContentTitle(waitingForVpn?"静启 · 等待恢复过滤":"静启 · 广告过滤")
            .setContentText((healthChecking?"正在检查过滤链路":status)+" · 自动跳过："+AccessibilityStatus.label(AccessibilityStatus.read(this)))
            .setSubText(lastHealthCheck==0?"本次运行尚未完成链路检查":"最近自检 "+new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(lastHealthCheck))+" · "+("通过".equals(healthResult)?"通过":"未通过"))
            .setShowWhen(false)
            .setContentIntent(openIntent()).setOngoing(true).setOnlyAlertOnce(true).build();
        foregroundActive=true;
        if(Build.VERSION.SDK_INT>=34) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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
            if(!closed&&session==generation&&!destroyed)completeHealthCheck(session,"失败："+message);
        });
    }
    private void process(byte[] packet,byte[] query,FileOutputStream output) {
        if(closed) return;
        try {
            String name=DnsPacket.question(query).name();
            byte[] response;
            if(SELF_TEST.equals(name)) {
                response=DnsPacket.failure(query,3); // Health checks are not ad-block counts.
            } else if(rules.blocks(name)) {
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
    @Override public void onRevoke() {
        int session=generation;
        handoffHandler.post(()->{
            if(destroyed||prefs(this).getBoolean("user_paused",false))return;
            if(session!=generation&&!waitingForVpn)return;
            yieldToVpn(false);networksChanged();
        });
    }
    private void networksChanged(){
        if(destroyed||prefs(this).getBoolean("user_paused",false)||vpnMonitor==null)return;
        handoffHandler.removeCallbacks(resumeAfterVpn);
        if(vpnMonitor.hasOther()){yieldToVpn(true);return;}
        if(waitingForVpn&&!needsConsent&&!vpnMonitor.blocksStart()&&prefs(this).getBoolean("handoff_seen",false)){
            status="其他 VPN 已退出，即将恢复过滤";showForeground();notifyState(this);
            handoffHandler.postDelayed(resumeAfterVpn,2000);
        }
    }
    private void yieldToVpn(boolean observed){
        if(destroyed||prefs(this).getBoolean("user_paused",false))return;
        handoffHandler.removeCallbacks(resumeAfterVpn);
        if(!waitingForVpn||tun!=null||starting)stopFiltering(false);
        waitingForVpn=true;
        if(observed)needsConsent=false;
        prefs(this).edit().putBoolean("handoff_waiting",true)
                .putBoolean("handoff_seen",observed||prefs(this).getBoolean("handoff_seen",false)).apply();
        status=observed?"其他 VPN 使用中，退出后自动恢复过滤":"VPN 已断开，等待其他 VPN 状态；可手动重新开启";
        showForeground();notifyState(this);
    }
    private void handoffError(RuntimeException error){
        status="自动切换不可用："+error.getClass().getSimpleName();stopFiltering();stopSelf();
    }
    private void stopFiltering() {
        stopFiltering(true);
    }
    private void stopFiltering(boolean endSession) {
        cancelStartTimeout();
        healthChecking=false;lastCheckElapsed=-1;
        generation++;
        closed=true; running=false; starting=false;
        if(waiter!=null) {if(reader==null)waiter.close();else waiter.signalStop();}
        if(pool!=null) pool.shutdownNow();
        for(Closeable socket:sockets) try { socket.close(); } catch(IOException ignored) {}
        sockets.clear();
        if(tun!=null) try { tun.close(); } catch(IOException ignored) {}
        if(reader!=null) reader.interrupt();
        tun=null;reader=null;waiter=null;pool=null;
        if(endSession){
            foregroundActive=false;
            handoffHandler.removeCallbacksAndMessages(null);
            if(vpnMonitor!=null){vpnMonitor.close();vpnMonitor=null;}
            waitingForVpn=false;needsConsent=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        stopping=false;
        if(prefs(this).getBoolean("user_paused",false))status="已暂停";
        else if("过滤运行中".equals(status)||"正在载入广告规则…".equals(status)) status="已停止";
        notifyState(this);
    }
    @Override public void onDestroy() {
        if(destroyed)return;
        destroyed=true;
        unregisterReceiver(wakeReceiver);healthWorker.shutdownNow();
        stopFiltering();
        ruleWorker.shutdownNow();
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
