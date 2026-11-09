package cn.quietstart;

import android.os.Bundle;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainNavigationTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void setup(){
        FilterVpnService.prefs(RuntimeEnvironment.getApplication()).edit().clear()
            .putBoolean("skip_ads",true).putBoolean("popup_ads",false).putBoolean("ctrip_tap",false)
            .putBoolean("full_rules",false).putString("allow","example.com").commit();
        controller=Robolectric.buildActivity(MainActivity.class).create();activity=controller.get();
    }
    @After public void cleanup(){controller.destroy();}
    private void page(String value)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("showPage",String.class);m.setAccessible(true);m.invoke(activity,value);
    }
    private String current()throws Exception{
        Field f=MainActivity.class.getDeclaredField("page");f.setAccessible(true);return (String)f.get(activity);
    }
    @Test public void advancedPagesReturnToTheirOwnParent()throws Exception{
        for(String child:new String[]{"rules","diagnostics","compatibility"}){
            page(child);activity.onBackPressed();assertEquals("advanced",current());
            activity.onBackPressed();assertEquals("settings",current());
            activity.onBackPressed();assertEquals("home",current());
        }
        page("clash");activity.onBackPressed();assertEquals("compatibility",current());
    }
    @Test public void navigationPreservesExistingUserChoicesAndDoesNotRequestPermissions()throws Exception{
        for(String destination:new String[]{"home","settings","advanced","rules","scope","background","diagnostics","about","compatibility"})page(destination);
        var p=FilterVpnService.prefs(activity);
        assertTrue(p.getBoolean("skip_ads",false));assertFalse(p.getBoolean("popup_ads",true));
        assertFalse(p.getBoolean("ctrip_tap",true));assertFalse(p.getBoolean("full_rules",true));
        assertEquals("example.com",p.getString("allow",""));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void savedLegacySkipPageOpensHomeWithoutChangingPermission()throws Exception{
        controller.destroy();Bundle state=new Bundle();state.putString("page","skip");
        controller=Robolectric.buildActivity(MainActivity.class).create(state);activity=controller.get();
        assertEquals("home",current());assertTrue(FilterVpnService.prefs(activity).getBoolean("skip_ads",false));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void restoredAdvancedPageKeepsCorrectBackNavigation()throws Exception{
        controller.destroy();Bundle state=new Bundle();state.putString("page","rules");
        controller=Robolectric.buildActivity(MainActivity.class).create(state);activity=controller.get();
        assertEquals("rules",current());activity.onBackPressed();assertEquals("advanced",current());
    }
}
