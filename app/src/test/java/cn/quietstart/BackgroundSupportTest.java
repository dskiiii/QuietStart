package cn.quietstart;

import android.app.AlertDialog;
import android.content.*;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.lang.reflect.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BackgroundSupportTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        FilterVpnService.prefs(context).edit().clear().putBoolean("skip_ads",true).commit();
        AdSkipService.connected=false;
        Settings.Secure.putString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");
        Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(context.getPackageName(),false);
        controller=Robolectric.buildActivity(MainActivity.class).create();activity=controller.get();
    }
    @After public void cleanup(){controller.destroy();AdSkipService.connected=false;}
    private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private void page(String value)throws Exception{Method m=MainActivity.class.getDeclaredMethod("showPage",String.class);m.setAccessible(true);m.invoke(activity,value);}
    private Button button(String field)throws Exception{Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);return (Button)f.get(activity);}
    private void configured(){Settings.Secure.putString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,new ComponentName(context,AdSkipService.class).flattenToString());}
    @Test public void openingHomeDoesNotRequestExtraSettings(){assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertNull(ShadowAlertDialog.getLatestAlertDialog());}
    @Test public void openingBackgroundOptionsDoesNotRequestPermission()throws Exception{page("background");assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertEquals(BackgroundSupport.BatteryState.OPTIMIZED,BackgroundSupport.batteryState(activity));}
    @Test public void disconnectedServiceDoesNotRequestAuthorization()throws Exception{
        configured();call("showAccessHelp");AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("授权还在，正在等待连接",Shadows.shadowOf(d).getTitle());
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(activity));
    }
    @Test public void disabledServiceRequiresExplicitSettingsClick()throws Exception{
        call("showAccessHelp");assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS,Shadows.shadowOf(activity).getNextStartedActivity().getAction());
    }
    @Test public void dismissingHelpDoesNotChangePermission()throws Exception{configured();call("showAccessHelp");ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(AccessibilityStatus.State.DISCONNECTED,AccessibilityStatus.read(activity));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void connectedServiceHidesRepairButton()throws Exception{AdSkipService.connected=true;call("refresh");assertEquals(View.GONE,button("accessHelp").getVisibility());}
    @Test public void pausedFeatureDoesNotNag()throws Exception{FilterVpnService.prefs(context).edit().putBoolean("skip_ads",false).commit();call("refresh");assertEquals(View.GONE,button("accessHelp").getVisibility());}
    @Test public void lostConnectionShowsRepairButton()throws Exception{configured();call("refresh");assertEquals(View.VISIBLE,button("accessHelp").getVisibility());assertNull(ShadowAlertDialog.getLatestAlertDialog());}
    @Test public void batteryRequestIsScopedToOurPackage()throws Exception{
        call("requestBatteryExemption");Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,intent.getAction());assertEquals("package:"+activity.getPackageName(),intent.getDataString());
        assertEquals(BackgroundSupport.BatteryState.OPTIMIZED,BackgroundSupport.batteryState(activity));
    }
    @Test public void deniedRequestIsNotRecordedAsGranted()throws Exception{call("requestBatteryExemption");Shadows.shadowOf(activity).getNextStartedActivity();page("background");assertTrue(button("batteryRequest").isEnabled());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void exemptionIsReadAgainAfterReturning()throws Exception{page("background");Shadows.shadowOf(activity.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(activity.getPackageName(),true);call("refresh");assertEquals(BackgroundSupport.BatteryState.EXEMPT,BackgroundSupport.batteryState(activity));assertFalse(button("batteryRequest").isEnabled());}
    @Test public void existingExemptionDoesNotLaunchAnotherRequest()throws Exception{Shadows.shadowOf(activity.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(activity.getPackageName(),true);call("requestBatteryExemption");assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void unavailableBatteryStateIsNotClaimedAsExempt(){Context c=mock(Context.class);when(c.getSystemService(PowerManager.class)).thenThrow(new SecurityException());assertEquals(BackgroundSupport.BatteryState.UNKNOWN,BackgroundSupport.batteryState(c));}
    @Test public void missingSystemDialogProvidesOptionalFallback()throws Exception{
        activity=spy(activity);doThrow(new ActivityNotFoundException()).when(activity).startActivity(org.mockito.ArgumentMatchers.argThat(i->Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.equals(i.getAction())));
        call("requestBatteryExemption");assertEquals("系统未提供确认弹窗",Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());assertEquals(BackgroundSupport.BatteryState.OPTIMIZED,BackgroundSupport.batteryState(activity));
    }
    @Test public void blockedSystemDialogDoesNotCrashOrClaimSuccess()throws Exception{
        activity=spy(activity);doThrow(new SecurityException()).when(activity).startActivity(org.mockito.ArgumentMatchers.argThat(i->Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.equals(i.getAction())));
        call("requestBatteryExemption");assertEquals("系统未提供确认弹窗",Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());assertEquals(BackgroundSupport.BatteryState.OPTIMIZED,BackgroundSupport.batteryState(activity));
    }
}
