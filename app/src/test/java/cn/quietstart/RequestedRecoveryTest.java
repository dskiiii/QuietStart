package cn.quietstart;
import android.content.*;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
@LooperMode(LooperMode.Mode.PAUSED)
public class RequestedRecoveryTest {
    private Context app;private SharedPreferences prefs;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();prefs=FilterVpnService.prefs(app);prefs.edit().clear().commit();
        java.lang.reflect.Field f=FilterVpnService.class.getDeclaredField("instance");f.setAccessible(true);f.set(null,null);
        FilterVpnService.starting=false;FilterVpnService.running=false;FilterVpnService.waitingForVpn=false;FilterVpnService.needsConsent=false;
    }
    @After public void cleanup(){FilterVpnService.pause(app);prefs.edit().clear().commit();}
    private Intent next(){return Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();}
    @Test public void freshInstallDoesNotStartVpn(){FilterVpnService.restoreRequestedFiltering(app);assertNull(next());}
    @Test public void earlierExplicitStartRestoresAfterProcessLoss(){
        prefs.edit().putBoolean("filter_requested",true).putBoolean("user_paused",false).commit();
        FilterVpnService.restoreRequestedFiltering(app);assertEquals(FilterVpnService.class.getName(),next().getComponent().getClassName());assertTrue(FilterVpnService.starting);
        FilterVpnService.restoreRequestedFiltering(app);assertNull(next());
    }
    @Test public void legacyExplicitRunningPreferenceMigrates(){
        prefs.edit().putBoolean("user_paused",false).commit();FilterVpnService.restoreRequestedFiltering(app);
        assertNotNull(next());assertTrue(prefs.getBoolean("filter_requested",false));
    }
    @Test public void pausedIntentNeverRestarts(){
        prefs.edit().putBoolean("filter_requested",true).putBoolean("user_paused",true).commit();FilterVpnService.restoreRequestedFiltering(app);assertNull(next());
    }
    @Test public void restoringDoesNotEraseObservedClashHandoff(){
        prefs.edit().putBoolean("filter_requested",true).putBoolean("handoff_waiting",true).putBoolean("handoff_seen",true).commit();
        FilterVpnService.restoreRequestedFiltering(app);assertNotNull(next());assertTrue(prefs.getBoolean("handoff_waiting",false));assertTrue(prefs.getBoolean("handoff_seen",false));
    }
    @Test public void backgroundStartDenialIsNotReportedAsRunning(){
        prefs.edit().putBoolean("filter_requested",true).commit();Context denied=spy(app);
        doThrow(new IllegalStateException("background start denied")).when(denied).startForegroundService(org.mockito.ArgumentMatchers.any());
        FilterVpnService.restoreRequestedFiltering(denied);assertFalse(FilterVpnService.starting);assertFalse(FilterVpnService.running);assertTrue(FilterVpnService.healthProblem);
        assertTrue(prefs.getBoolean("filter_requested",false));assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }
    @Test public void foregroundOpenCanRetryPreviouslyDeniedRestoration(){
        prefs.edit().putBoolean("filter_requested",true).commit();Context denied=spy(app);
        doThrow(new IllegalStateException()).when(denied).startForegroundService(org.mockito.ArgumentMatchers.any());
        FilterVpnService.restoreRequestedFiltering(denied);FilterVpnService.restoreRequestedFiltering(app);assertNotNull(next());
    }
    @Test public void restorationTimeoutClearsStartingState(){
        prefs.edit().putBoolean("filter_requested",true).commit();FilterVpnService.restoreRequestedFiltering(app);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(21));assertFalse(FilterVpnService.starting);assertFalse(FilterVpnService.running);
    }
}
