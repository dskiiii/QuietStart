package cn.quietstart;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;

/** Opt-in device checks. Requires prior VPN consent and an installed target app. */
final class LifecycleChecks {
    static String run(Instrumentation test) throws Exception {
        android.content.Context activity=test.getTargetContext();
        Runnable launch=()->{
            FilterVpnService.prefs(activity).edit().putBoolean("user_paused",false).apply();
            FilterVpnService.starting=true;FilterVpnService.status="正在启动…";
            activity.startForegroundService(new Intent(activity,FilterVpnService.class));
        };
        StringBuilder result=new StringBuilder();
        for(int i=0;i<4;i++){
            test.runOnMainSync(()->FilterVpnService.pause(activity));
            if(FilterVpnService.starting||FilterVpnService.running||FilterVpnService.stopping)throw new AssertionError("Pause did not settle");
            test.runOnMainSync(launch);
            awaitRunning();
            String probe=DnsProbe.check(FilterVpnService.SELF_TEST,3);
            result.append("cycle ").append(i+1).append(": ").append(probe).append('\n');
            if(!probe.startsWith("通过"))throw new AssertionError("DNS probe failed: "+probe);
        }
        test.runOnMainSync(launch);
        SystemClock.sleep(1500);
        if(FilterVpnService.starting||!FilterVpnService.running||!"过滤运行中".equals(FilterVpnService.status))
            throw new AssertionError("Duplicate start stuck: running="+FilterVpnService.running+", starting="+FilterVpnService.starting+", status="+FilterVpnService.status+"; "+result);
        result.append("duplicate start: PASS\n");
        java.lang.reflect.Field instanceField=FilterVpnService.class.getDeclaredField("instance");instanceField.setAccessible(true);
        FilterVpnService service=(FilterVpnService)instanceField.get(null);
        java.lang.reflect.Field workerField=FilterVpnService.class.getDeclaredField("ruleWorker");workerField.setAccessible(true);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        ((java.util.concurrent.ExecutorService)workerField.get(service)).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        if(!entered.await(2,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Worker fixture failed");
        // Keep the service instance bound so its blocked worker survives pause/restart.
        android.content.ServiceConnection binding=new android.content.ServiceConnection(){
            public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){}
            public void onServiceDisconnected(android.content.ComponentName name){}
        };
        boolean bound=activity.bindService(new Intent(activity,FilterVpnService.class).setAction(android.net.VpnService.SERVICE_INTERFACE),binding,android.content.Context.BIND_AUTO_CREATE);
        if(!bound)throw new AssertionError("Could not bind fixture");
        try{
            test.runOnMainSync(()->{FilterVpnService.pause(activity);FilterVpnService.start(activity);});
            SystemClock.sleep(22000);
            if(FilterVpnService.starting||FilterVpnService.running||!FilterVpnService.status.contains("超时"))throw new AssertionError("Timeout did not settle: "+FilterVpnService.status);
            result.append("blocked rule worker: timeout settled\n");
        }finally{release.countDown();activity.unbindService(binding);}
        test.runOnMainSync(()->FilterVpnService.start(activity));awaitRunning();
        String probe=DnsProbe.check(FilterVpnService.SELF_TEST,3);
        if(!probe.equals("通过"))throw new AssertionError("Recovery failed: "+probe);
        return result+"timeout recovery: PASS\n";
    }
    private static void awaitRunning(){
        long end=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<end){
            if(FilterVpnService.running&&!FilterVpnService.starting)return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Start timed out: "+FilterVpnService.status);
    }
}
