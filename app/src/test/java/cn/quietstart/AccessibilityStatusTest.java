package cn.quietstart;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
public class AccessibilityStatusTest {
    private Context context;
    private String component;

    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        component=new android.content.ComponentName(context,AdSkipService.class).flattenToString();
        FilterVpnService.prefs(context).edit().clear().putBoolean("skip_ads",true).commit();
        AdSkipService.connected=false;
        configure(null);
    }
    @After public void cleanup(){AdSkipService.connected=false;configure(null);}
    private void configure(String value){Settings.Secure.putString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,value);}

    @Test public void configuredServiceWithoutConnectionDoesNotRequestAuthorization(){
        configure("other.app/.Service:"+component);
        assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(context));
    }
    @Test public void abbreviatedComponentIsRecognized(){
        configure(new android.content.ComponentName(context,AdSkipService.class).flattenToShortString());
        assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(context));
    }
    @Test public void unrelatedOrMalformedEntriesDoNotCountAsEnabled(){
        configure("bad:"+component+"Extra:other.app/.Service");
        assertEquals(AccessibilityStatus.State.NOT_ENABLED,AccessibilityStatus.read(context));
    }
    @Test public void emptySystemListRequiresEnablement(){
        assertEquals(AccessibilityStatus.State.NOT_ENABLED,AccessibilityStatus.read(context));
    }
    @Test public void staleConnectedFlagCannotOverrideRemovedAuthorization(){
        AdSkipService.connected=true;
        configure(null);
        assertEquals(AccessibilityStatus.State.NOT_ENABLED,AccessibilityStatus.read(context));
    }
    @Test public void twoDaysWithoutEventsIsNotTreatedAsFailure(){
        configure(component);AdSkipService.connected=true;
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofDays(2));
        assertEquals(AccessibilityStatus.State.CONNECTED,AccessibilityStatus.read(context));
    }
    @Test public void stuckScanIsDifferentFromMissingPermission()throws Exception{
        configure(component);AdSkipService service=Robolectric.buildService(AdSkipService.class).create().get();
        try{
            AdSkipService.connected=true;
            java.lang.reflect.Field busy=AdSkipService.class.getDeclaredField("busy");busy.setAccessible(true);
            java.lang.reflect.Field since=AdSkipService.class.getDeclaredField("scanStartedUptime");since.setAccessible(true);since.setLong(service,android.os.SystemClock.uptimeMillis());
            ((java.util.concurrent.atomic.AtomicBoolean)busy.get(service)).set(true);
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(16));
            assertEquals(AccessibilityStatus.State.UNRESPONSIVE,AccessibilityStatus.read(context));
            assertTrue(BackgroundSupport.needsAccessHelp(AccessibilityStatus.read(context)));
            ((java.util.concurrent.atomic.AtomicBoolean)busy.get(service)).set(false);
            assertEquals(AccessibilityStatus.State.CONNECTED,AccessibilityStatus.read(context));
        }finally{service.onDestroy();}
    }
    @Test public void appPausePreservesSystemSettingAndCanResume(){
        configure(component);
        FilterVpnService.prefs(context).edit().putBoolean("skip_ads",false).commit();
        assertEquals(AccessibilityStatus.State.OFF,AccessibilityStatus.read(context));
        assertEquals(component,Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        FilterVpnService.prefs(context).edit().putBoolean("skip_ads",true).commit();
        assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(context));
    }
    @Test public void unbindClearsLiveStateButRetainsSystemConfiguration(){
        configure(component);
        AdSkipService service=Robolectric.buildService(AdSkipService.class).create().get();
        AdSkipService.connected=true;
        assertEquals(AccessibilityStatus.State.CONNECTED,AccessibilityStatus.read(context));
        service.onUnbind(new Intent());
        assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(context));
        service.onDestroy();
    }
}
