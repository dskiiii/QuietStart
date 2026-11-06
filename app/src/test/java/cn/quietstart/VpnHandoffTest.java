package cn.quietstart;

import android.app.Activity;
import android.app.Service;
import android.content.*;
import android.net.*;
import android.os.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the real service handoff, callbacks and debounce. VPN authorization and
 * tunnel creation are simulated, so these checks do not replace a phone trial. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class VpnHandoffTest {
    private FilterVpnService service;
    private SharedPreferences prefs;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Network external;
    private NetworkCapabilities foreignCaps;
    private MockedStatic<VpnService> vpn;

    @Before public void setup(){
        service=Robolectric.buildService(FilterVpnService.class).get();
        service.onCreate();
        prefs=FilterVpnService.prefs(service);prefs.edit().clear().putBoolean("user_paused",false).commit();
        FilterVpnService.running=false;FilterVpnService.starting=false;FilterVpnService.stopping=false;
        FilterVpnService.waitingForVpn=false;FilterVpnService.needsConsent=false;
        cm=mock(ConnectivityManager.class);
        Context base=service.getBaseContext();
        while(base instanceof ContextWrapper)base=((ContextWrapper)base).getBaseContext();
        org.robolectric.shadows.ShadowContextImpl contextShadow=org.robolectric.shadow.api.Shadow.extract(base);
        contextShadow.setSystemService(Context.CONNECTIVITY_SERVICE,cm);
        doAnswer(call->{callback=call.getArgument(1);return null;}).when(cm).registerNetworkCallback(any(NetworkRequest.class),any(ConnectivityManager.NetworkCallback.class),any(Handler.class));
        external=mock(Network.class);foreignCaps=mock(NetworkCapabilities.class);
        when(foreignCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(foreignCaps.getOwnerUid()).thenReturn(-1);
        when(cm.getAllNetworks()).thenReturn(new Network[]{external});
        when(cm.getNetworkCapabilities(external)).thenReturn(foreignCaps);
        when(cm.getLinkProperties(external)).thenReturn(new LinkProperties());
        vpn=mockStatic(VpnService.class);
        vpn.when(()->VpnService.prepare(service)).thenReturn(new Intent("test-consent"));
    }
    @After public void cleanup(){service.onDestroy();vpn.close();prefs.edit().clear().commit();}
    private void begin(){assertEquals(Service.START_STICKY,service.onStartCommand(new Intent(),0,1));}
    private void disconnect(){when(cm.getAllNetworks()).thenReturn(new Network[0]);callback.onLost(external);}
    private void advance(long milliseconds){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));}

    @Test public void existingOtherVpnWaitsWithoutPreparingOrChangingAdSettings(){
        prefs.edit().putBoolean("skip_ads",true).putString("allow","example.org").commit();
        begin();
        vpn.verifyNoInteractions();assertTrue(FilterVpnService.waitingForVpn);
        assertFalse(FilterVpnService.running);assertFalse(FilterVpnService.starting);
        assertFalse(prefs.getBoolean("user_paused",true));
        assertTrue(AdSkipService.enabled(service));assertEquals("example.org",prefs.getString("allow",""));
        assertTrue(prefs.getBoolean("handoff_seen",false));
    }
    @Test public void observedDisconnectWaitsTwoSecondsThenPreparesOnce(){
        begin();disconnect();advance(1999);vpn.verifyNoInteractions();
        advance(1);vpn.verify(()->VpnService.prepare(service),times(1));
        assertTrue(FilterVpnService.needsConsent);
        advance(60000);vpn.verifyNoMoreInteractions();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }
    @Test public void priorConsentAttemptsTunnelWithoutRequestingConsent(){
        vpn.when(()->VpnService.prepare(service)).thenReturn(null);
        try(MockedConstruction<VpnService.Builder> builders=mockConstruction(VpnService.Builder.class,withSettings().defaultAnswer(Answers.RETURNS_SELF))){
            begin();disconnect();advance(2000);
            vpn.verify(()->VpnService.prepare(service),times(1));
            assertEquals(1,builders.constructed().size());
            verify(builders.constructed().get(0)).establish();
            // Mock OS cannot create a TUN: verify an attempt, not actual filtering.
            assertFalse(FilterVpnService.needsConsent);
            assertNull(Shadows.shadowOf(service).getNextStartedActivity());
        }
    }
    @Test public void switchingBetweenClashClientsCancelsRecovery(){
        begin();disconnect();advance(1500);
        Network next=mock(Network.class);
        when(cm.getAllNetworks()).thenReturn(new Network[]{next});
        when(cm.getNetworkCapabilities(next)).thenReturn(foreignCaps);
        when(cm.getLinkProperties(next)).thenReturn(new LinkProperties());
        callback.onAvailable(next);callback.onCapabilitiesChanged(next,foreignCaps);callback.onLinkPropertiesChanged(next,new LinkProperties());
        advance(60000);vpn.verifyNoInteractions();assertTrue(FilterVpnService.waitingForVpn);
        when(cm.getAllNetworks()).thenReturn(new Network[0]);callback.onLost(next);advance(2000);
        vpn.verify(()->VpnService.prepare(service),times(1));
    }
    @Test public void freshNetworkCheckStopsTakeoverBeforeCallbackArrives(){
        begin();disconnect();
        when(cm.getAllNetworks()).thenReturn(new Network[]{external});
        advance(2000);vpn.verifyNoInteractions();
    }
    @Test public void manualPauseCancelsPendingRecoveryAndOldCallbacks(){
        begin();disconnect();advance(1000);FilterVpnService.pause(service);
        callback.onAvailable(external);callback.onCapabilitiesChanged(external,foreignCaps);callback.onLost(external);
        advance(60000);vpn.verifyNoInteractions();
        assertTrue(prefs.getBoolean("user_paused",false));assertFalse(FilterVpnService.waitingForVpn);
        verify(cm).unregisterNetworkCallback(callback);
        assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,2));
    }
    @Test public void revokeKeepsObserverAndCanResumeAfterOtherVpnEnds(){
        begin();service.onRevoke();advance(0);
        verify(cm,never()).unregisterNetworkCallback(any(ConnectivityManager.NetworkCallback.class));
        disconnect();advance(2000);vpn.verify(()->VpnService.prepare(service),times(1));
    }
    @Test public void revokeWithoutEvidenceOfOtherVpnDoesNotRestartItself(){
        when(cm.getAllNetworks()).thenReturn(new Network[0]);
        prefs.edit().putBoolean("handoff_waiting",true).putBoolean("handoff_seen",false).commit();
        begin();service.onRevoke();advance(60000);
        vpn.verifyNoInteractions();assertTrue(FilterVpnService.waitingForVpn);
    }
    @Test public void reloadWhileWaitingDoesNotStopObserver(){
        begin();assertEquals(Service.START_STICKY,service.onStartCommand(new Intent().setAction(FilterVpnService.RELOAD),0,2));
        disconnect();advance(2000);vpn.verify(()->VpnService.prepare(service),times(1));
    }
    @Test public void pendingNetworkMetadataBlocksRecoveryUntilClassified(){
        begin();disconnect();callback.onAvailable(external);advance(5000);vpn.verifyNoInteractions();
        callback.onCapabilitiesChanged(external,foreignCaps);advance(5000);vpn.verifyNoInteractions();
        callback.onLinkPropertiesChanged(external,new LinkProperties());advance(5000);vpn.verifyNoInteractions();
        callback.onLost(external);advance(2000);vpn.verify(()->VpnService.prepare(service),times(1));
    }
    @Test public void persistedObservedHandoffResumesAfterProcessRecreation(){
        when(cm.getAllNetworks()).thenReturn(new Network[0]);
        prefs.edit().putBoolean("handoff_waiting",true).putBoolean("handoff_seen",true).commit();
        begin();advance(1999);vpn.verifyNoInteractions();advance(1);
        vpn.verify(()->VpnService.prepare(service),times(1));
    }
    @Test public void serviceDestructionCancelsResume(){
        begin();disconnect();service.onDestroy();advance(60000);vpn.verifyNoInteractions();
    }
    @Test @Config(sdk=34) public void waitingWorksWithAndroid14ForegroundType(){
        begin();assertTrue(FilterVpnService.waitingForVpn);vpn.verifyNoInteractions();
    }
    @Test public void staleActivityPermissionResultsCannotStartFiltering(){
        FilterVpnService.pause(service);
        var activity=Robolectric.buildActivity(MainActivity.class).create();
        try{
            activity.get().onRequestPermissionsResult(11,new String[0],new int[0]);
            activity.get().onActivityResult(10,Activity.RESULT_OK,null);
            vpn.verifyNoInteractions();assertTrue(prefs.getBoolean("user_paused",false));
        }finally{activity.destroy();}
    }
    @Test public void manualPauseInvalidatesAnOutstandingPermissionRequest()throws Exception{
        var activity=Robolectric.buildActivity(MainActivity.class).create();
        try{
            java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("vpnRequestPending");pending.setAccessible(true);pending.setBoolean(activity.get(),true);
            FilterVpnService.pause(service);
            activity.get().onRequestPermissionsResult(11,new String[0],new int[0]);
            activity.get().onActivityResult(10,Activity.RESULT_OK,null);
            vpn.verifyNoInteractions();assertTrue(prefs.getBoolean("user_paused",false));
        }finally{activity.destroy();}
    }
}
