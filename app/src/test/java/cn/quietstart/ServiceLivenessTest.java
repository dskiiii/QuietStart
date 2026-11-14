package cn.quietstart;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class ServiceLivenessTest {
    private FilterVpnService service;
    private MockedStatic<VpnMonitor> networks;
    private MockedStatic<VpnService> vpn;
    private CountDownLatch release;
    private Thread reader;
    @Before public void setup()throws Exception{
        service=spy(Robolectric.buildService(FilterVpnService.class).get());service.onCreate();
        FilterVpnService.prefs(service).edit().clear().putBoolean("user_paused",false).commit();
        FilterVpnService.running=true;FilterVpnService.starting=false;FilterVpnService.stopping=false;
        FilterVpnService.waitingForVpn=false;FilterVpnService.needsConsent=false;FilterVpnService.status="过滤运行中";
        release=new CountDownLatch(1);reader=new Thread(()->{try{release.await();}catch(InterruptedException ignored){}});reader.start();
        set("reader",reader);set("tun",mock(ParcelFileDescriptor.class));set("rules",new Rules("example.invalid","",""));
        doReturn("通过").when(service).probeLocalDns();
        networks=mockStatic(VpnMonitor.class);networks.when(()->VpnMonitor.otherVpnPresent(service)).thenReturn(false);
        networks.when(()->VpnMonitor.ownVpnPresent(service)).thenReturn(false);
        vpn=mockStatic(VpnService.class);vpn.when(()->VpnService.prepare(service)).thenReturn(new Intent("consent"));
    }
    @After public void cleanup()throws Exception{service.onDestroy();release.countDown();reader.join(1000);networks.close();vpn.close();}
    private void set(String name,Object value)throws Exception{Field f=FilterVpnService.class.getDeclaredField(name);f.setAccessible(true);f.set(service,value);}
    private Object get(String name)throws Exception{Field f=FilterVpnService.class.getDeclaredField(name);f.setAccessible(true);return f.get(service);}
    private void call(String name)throws Exception{Method m=FilterVpnService.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(service);}
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void complete(int generation,String result)throws Exception{
        Method m=FilterVpnService.class.getDeclaredMethod("completeHealthCheck",int.class,String.class);m.setAccessible(true);m.invoke(service,generation,result);idle();
    }
    private void awaitCheck()throws Exception{
        ThreadPoolExecutor worker=(ThreadPoolExecutor)get("healthWorker");long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(FilterVpnService.healthChecking||worker.getActiveCount()>0||!worker.getQueue().isEmpty()){
            if(System.nanoTime()>deadline)fail("health worker did not complete");idle();Thread.sleep(2);
        }
        idle();
    }
    @Test public void twoDaysIdleThenUnlockRunsActualLocalProbe()throws Exception{
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(2));verify(service,never()).probeLocalDns();
        service.sendBroadcast(new Intent(Intent.ACTION_USER_PRESENT));idle();awaitCheck();
        verify(service).probeLocalDns();assertTrue(FilterVpnService.running);assertFalse(FilterVpnService.healthChecking);
        assertEquals("通过",FilterVpnService.healthResult);assertTrue(FilterVpnService.lastHealthCheck>0);
    }
    @Test public void repeatedWakeEventsAreCoalesced()throws Exception{
        FilterVpnService.requestHealthCheck(service);idle();awaitCheck();
        FilterVpnService.requestHealthCheck(service);idle();awaitCheck();verify(service,times(1)).probeLocalDns();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31));
        FilterVpnService.requestHealthCheck(service);idle();awaitCheck();verify(service,times(2)).probeLocalDns();
    }
    @Test public void pausePreventsAnyWakeRestart()throws Exception{
        FilterVpnService.pause(service);service.sendBroadcast(new Intent(Intent.ACTION_USER_PRESENT));idle();
        verify(service,never()).probeLocalDns();vpn.verifyNoInteractions();assertFalse(FilterVpnService.running);
    }
    @Test public void waitingForOtherVpnDoesNotProbeOrTakeOver()throws Exception{
        FilterVpnService.waitingForVpn=true;FilterVpnService.requestHealthCheck(service);idle();verify(service,never()).probeLocalDns();vpn.verifyNoInteractions();
    }
    @Test public void failedCheckWithoutOwnTunnelStopsFalseRunningState()throws Exception{
        doReturn("失败：无响应").when(service).probeLocalDns();call("checkHealth");awaitCheck();
        assertFalse(FilterVpnService.running);assertTrue(FilterVpnService.healthProblem);assertFalse(FilterVpnService.waitingForVpn);vpn.verifyNoInteractions();
        assertTrue(FilterVpnService.status.contains("未通过"));
    }
    @Test public void deadReaderDoesNotClaimHealthyOrProbe()throws Exception{
        release.countDown();reader.join(1000);call("checkHealth");awaitCheck();
        verify(service,never()).probeLocalDns();assertFalse(FilterVpnService.running);assertTrue(FilterVpnService.healthProblem);
    }
    @Test public void lateFailedResultCannotStopNewSession()throws Exception{
        set("generation",2);FilterVpnService.healthChecking=true;complete(1,"失败：旧结果");
        assertTrue(FilterVpnService.running);assertTrue(FilterVpnService.healthChecking);assertFalse(FilterVpnService.healthProblem);
    }
    @Test public void lateSuccessCannotResurrectPausedService()throws Exception{
        FilterVpnService.pause(service);complete(0,"通过");assertFalse(FilterVpnService.running);assertEquals(0,FilterVpnService.lastHealthCheck);
    }
    @Test public void confirmedOwnTunnelAllowsOneRepairAttempt()throws Exception{
        networks.when(()->VpnMonitor.ownVpnPresent(service)).thenReturn(true);
        complete(0,"失败：无响应");vpn.verify(()->VpnService.prepare(service),times(1));
        assertTrue(FilterVpnService.needsConsent);assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }
    @Test public void repeatedFailureDoesNotEnterRepairLoop()throws Exception{
        networks.when(()->VpnMonitor.ownVpnPresent(service)).thenReturn(true);
        complete(0,"失败：无响应");
        set("closed",false);set("tun",mock(ParcelFileDescriptor.class));FilterVpnService.running=true;FilterVpnService.waitingForVpn=false;FilterVpnService.needsConsent=false;
        complete((Integer)get("generation"),"失败：仍无响应");
        vpn.verify(()->VpnService.prepare(service),times(1));assertFalse(FilterVpnService.running);
    }
    @Test public void otherVpnArrivingDuringProbePreventsRepair()throws Exception{
        networks.when(()->VpnMonitor.ownVpnPresent(service)).thenReturn(true);networks.when(()->VpnMonitor.otherVpnPresent(service)).thenReturn(true);
        complete(0,"失败：无响应");vpn.verifyNoInteractions();assertTrue(FilterVpnService.waitingForVpn);assertFalse(FilterVpnService.running);
    }
    @Test public void notificationSeparatesSkipStatusAndCheckTime()throws Exception{
        call("showForeground");complete(0,"通过");
        Notification n=Shadows.shadowOf(service.getSystemService(NotificationManager.class)).getNotification(1);
        assertNotNull(n);assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("自动跳过"));
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString().contains("最近自检"));assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
    }
    @Test public void destroyedServiceDoesNotReactToUnlock()throws Exception{
        service.onDestroy();service.sendBroadcast(new Intent(Intent.ACTION_USER_PRESENT));idle();verify(service,never()).probeLocalDns();
    }
    @Test public void liveReaderExitBecomesExplicitFailure()throws Exception{
        Method m=FilterVpnService.class.getDeclaredMethod("readerStopped",int.class,String.class);m.setAccessible(true);m.invoke(service,0,"VPN 接口已关闭");idle();
        assertFalse(FilterVpnService.running);assertTrue(FilterVpnService.healthProblem);assertFalse(FilterVpnService.waitingForVpn);
    }
    @Test public void selfProbeReplyDoesNotInflateBlockCounter()throws Exception{
        byte[] query=DnsProbe.query(FilterVpnService.SELF_TEST,123),packet=new byte[28+query.length];packet[0]=0x45;DnsPacket.put16(packet,20,41000);
        java.io.FileOutputStream output=mock(java.io.FileOutputStream.class);long before=FilterVpnService.blocked.get();
        Method m=FilterVpnService.class.getDeclaredMethod("process",byte[].class,byte[].class,java.io.FileOutputStream.class);m.setAccessible(true);m.invoke(service,packet,query,output);
        ArgumentCaptor<byte[]> reply=ArgumentCaptor.forClass(byte[].class);verify(output).write(reply.capture());
        byte[] dns=java.util.Arrays.copyOfRange(reply.getValue(),28,reply.getValue().length);
        assertTrue(DnsPacket.validReply(query,dns));assertEquals(3,dns[3]&15);assertEquals(before,FilterVpnService.blocked.get());
    }
    @Test public void exitingDozeChecksButEnteringDozeDoesNot()throws Exception{
        PowerManager power=service.getSystemService(PowerManager.class);Shadows.shadowOf(power).setIsDeviceIdleMode(true);
        service.sendBroadcast(new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));idle();verify(service,never()).probeLocalDns();
        Shadows.shadowOf(power).setIsDeviceIdleMode(false);service.sendBroadcast(new Intent(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));idle();awaitCheck();verify(service).probeLocalDns();
    }
    @Test public void repairWithExistingConsentAttemptsTunnelWithoutOpeningUi()throws Exception{
        networks.when(()->VpnMonitor.ownVpnPresent(service)).thenReturn(true);vpn.when(()->VpnService.prepare(service)).thenReturn(null);
        try(MockedConstruction<VpnService.Builder> builders=mockConstruction(VpnService.Builder.class,withSettings().defaultAnswer(Answers.RETURNS_SELF))){
            complete(0,"失败：无响应");assertEquals(1,builders.constructed().size());verify(builders.constructed().get(0)).establish();
            assertNull(Shadows.shadowOf(service).getNextStartedActivity());assertFalse(FilterVpnService.running);
        }
    }
}
