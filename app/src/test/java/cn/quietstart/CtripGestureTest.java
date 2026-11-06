package cn.quietstart;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Executes production queue/validation/dispatch logic on a desktop Android runtime.
 * The OS gesture endpoint is mocked: this is not evidence of real-app dismissal. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CtripGestureTest {
    private AdSkipService service;
    private AccessibilityNodeInfo node,active;
    private SharedPreferences prefs;
    private MockedStatic<FilterVpnService> vpn;
    private MockedStatic<AccessibilityNodeInfo> nodes;
    private Rect liveBounds;
    private Method queue;

    @Before public void setup() throws Exception {
        service=spy(Robolectric.buildService(AdSkipService.class).create().get());
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("gesture-test",0);
        prefs.edit().clear().putBoolean("skip_ads",true).putBoolean("ctrip_tap",true).commit();
        vpn=mockStatic(FilterVpnService.class);
        vpn.when(()->FilterVpnService.prefs(any())).thenReturn(prefs);
        service.getResources().getDisplayMetrics().widthPixels=1080;
        service.getResources().getDisplayMetrics().heightPixels=2340;
        node=mock(AccessibilityNodeInfo.class);active=mock(AccessibilityNodeInfo.class);
        when(node.getPackageName()).thenReturn("ctrip.android.view");
        when(node.getText()).thenReturn("3 跳过广告");
        when(node.getWindowId()).thenReturn(7);
        when(node.isVisibleToUser()).thenReturn(true);when(node.isEnabled()).thenReturn(true);
        when(node.refresh()).thenReturn(true);
        when(active.getPackageName()).thenReturn("ctrip.android.view");when(active.getWindowId()).thenReturn(7);
        liveBounds=new Rect(847,141,1003,193);
        doAnswer(i->{((Rect)i.getArgument(0)).set(liveBounds);return null;}).when(node).getBoundsInScreen(any());
        nodes=mockStatic(AccessibilityNodeInfo.class);
        nodes.when(()->AccessibilityNodeInfo.obtain(node)).thenReturn(node);
        doReturn(active).when(service).getRootInActiveWindow();
        doReturn(true).when(service).dispatchGesture(any(),any(),any());
        queue=AdSkipService.class.getDeclaredMethod("queueCtripTap",AccessibilityNodeInfo.class,String.class);queue.setAccessible(true);
    }
    @After public void cleanup(){nodes.close();vpn.close();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void mapSetup(){
        when(node.getPackageName()).thenReturn("com.baidu.BaiduMap");when(active.getPackageName()).thenReturn("com.baidu.BaiduMap");
        when(node.getViewIdResourceName()).thenReturn("com.baidu.BaiduMap:id/ms_skipView");
        when(node.isClickable()).thenReturn(true);when(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        when(active.findAccessibilityNodeInfosByViewId("com.baidu.BaiduMap:id/ms_skipView")).thenReturn(java.util.List.of(node));
    }
    private boolean mapScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findMapSkip",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,"com.baidu.BaiduMap");
    }
    @Test public void mapVerifiedSkipIdClicks() throws Exception {mapSetup();assertTrue(mapScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void mapChangedIdDoesNotClick() throws Exception {mapSetup();when(node.getViewIdResourceName()).thenReturn("com.baidu.BaiduMap:id/ms_skipView","com.baidu.BaiduMap:id/other");assertFalse(mapScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapDifferentWindowDoesNotClick() throws Exception {mapSetup();when(active.getWindowId()).thenReturn(8);assertFalse(mapScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapInvisibleDoesNotClick() throws Exception {mapSetup();when(node.isVisibleToUser()).thenReturn(false);assertFalse(mapScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapLargeAreaDoesNotClick() throws Exception {mapSetup();liveBounds.set(0,0,1080,2340);assertFalse(mapScan());verify(node,never()).performAction(anyInt());}
    private boolean enqueue() throws Exception {return (boolean)queue.invoke(service,node,"ctrip.android.view");}
    private long cooldown() throws Exception {Field f=AdSkipService.class.getDeclaredField("lastClick");f.setAccessible(true);return f.getLong(service);}
    private void drain(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void noGesture(){verify(service,never()).dispatchGesture(any(),any(),any());}

    private void window(String pkg,String cls) throws Exception {
        android.view.accessibility.AccessibilityEvent e=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);e.setClassName(cls);
        Method m=AdSkipService.class.getDeclaredMethod("trackWindow",android.view.accessibility.AccessibilityEvent.class,String.class);m.setAccessible(true);m.invoke(service,e,pkg);e.recycle();
    }
    private void jdSetup() throws Exception {
        when(node.getPackageName()).thenReturn(SkipRules.JD);when(active.getPackageName()).thenReturn(SkipRules.JD);
        when(node.getText()).thenReturn("跳过");when(node.isClickable()).thenReturn(true);when(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        when(active.findAccessibilityNodeInfosByText("跳过")).thenReturn(java.util.List.of(node));
        nodes.when(()->AccessibilityNodeInfo.obtain(active)).thenReturn(active);
        window(SkipRules.JD,"com.jingdong.app.mall.MainFrameActivity");
    }
    private boolean jdScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findEntrySkip",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,SkipRules.JD);
    }
    @Test public void jdBareSkipOnEntryClicks() throws Exception {jdSetup();assertTrue(jdScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void jdMissingTextIndexTraversesOverlay() throws Exception {jdSetup();when(active.findAccessibilityNodeInfosByText("跳过")).thenReturn(java.util.List.of());when(active.getChildCount()).thenReturn(1);when(active.getChild(0)).thenReturn(node);assertTrue(jdScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void jdSearchPageDoesNotClick() throws Exception {jdSetup();window(SkipRules.JD,"com.jd.lib.search.view.Activity.SearchActivity");assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdPaymentPageDoesNotClick() throws Exception {jdSetup();window(SkipRules.JD,"com.jd.PaymentActivity");assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdExpiredEntryDoesNotClick() throws Exception {jdSetup();org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(11));assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdRepeatedWindowDoesNotExtendEntry() throws Exception {jdSetup();org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(11));window(SkipRules.JD,"com.jingdong.app.mall.MainFrameActivity");assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdChangedLabelDoesNotClick() throws Exception {jdSetup();when(node.getText()).thenReturn("跳过","跳过登录");assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdWrongWindowDoesNotClick() throws Exception {jdSetup();when(active.getWindowId()).thenReturn(8);assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdLowerButtonDoesNotClick() throws Exception {jdSetup();liveBounds.set(850,1500,1000,1560);assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    @Test public void jdExcludedDoesNotClick() throws Exception {jdSetup();prefs.edit().putStringSet("skip_excluded",java.util.Set.of(SkipRules.JD)).commit();assertFalse(jdScan());verify(node,never()).performAction(anyInt());}
    private void mapEntrySetup() throws Exception {
        jdSetup();when(node.getPackageName()).thenReturn(SkipRules.BAIDU);when(active.getPackageName()).thenReturn(SkipRules.BAIDU);
        window(SkipRules.BAIDU,"com.baidu.baidumaps.MapsActivity");
    }
    private boolean mapEntryScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findEntrySkip",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,SkipRules.BAIDU);
    }
    @Test public void mapBareSkipOnEntryClicksWithoutFixedId() throws Exception {mapEntrySetup();assertTrue(mapEntryScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);assertTrue(prefs.getString("baidu_last_click","").contains("尚未确认"));noGesture();}
    @Test public void mapCountdownOnWelcomeClicks() throws Exception {mapEntrySetup();window(SkipRules.BAIDU,"com.baidu.baidumaps.WelcomeScreen");when(node.getText()).thenReturn("跳过 3");assertTrue(mapEntryScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void mapMissingTextIndexTraverses() throws Exception {mapEntrySetup();when(active.findAccessibilityNodeInfosByText("跳过")).thenReturn(java.util.List.of());when(active.getChildCount()).thenReturn(1);when(active.getChild(0)).thenReturn(node);assertTrue(mapEntryScan());}
    @Test public void mapExpiredEntryDoesNotClick() throws Exception {mapEntrySetup();org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(11));assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapRoutePageDoesNotClick() throws Exception {mapEntrySetup();window(SkipRules.BAIDU,"com.baidu.baidumaps.RouteActivity");assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapChangedTextDoesNotClick() throws Exception {mapEntrySetup();when(node.getText()).thenReturn("跳过","跳过登录");assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapChangedWindowDoesNotClick() throws Exception {mapEntrySetup();when(active.getWindowId()).thenReturn(8);assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapBodyButtonDoesNotClick() throws Exception {mapEntrySetup();liveBounds.set(850,1500,1000,1560);assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapExcludedDoesNotClick() throws Exception {mapEntrySetup();prefs.edit().putStringSet("skip_excluded",java.util.Set.of(SkipRules.BAIDU)).commit();assertFalse(mapEntryScan());verify(node,never()).performAction(anyInt());}
    @Test public void mapMissingCandidateDoesNotOverwriteLastClick() throws Exception {mapEntrySetup();assertTrue(mapEntryScan());String last=prefs.getString("baidu_last_click","");when(active.findAccessibilityNodeInfosByText("跳过")).thenReturn(java.util.List.of());assertFalse(mapEntryScan());assertEquals(last,prefs.getString("baidu_last_click",""));}
    private AccessibilityNodeInfo cmccNativeSetup(){
        String pkg=SkipRules.CMCC;
        when(node.getPackageName()).thenReturn(pkg);when(active.getPackageName()).thenReturn(pkg);
        when(node.getText()).thenReturn("3 跳过");when(node.getViewIdResourceName()).thenReturn(pkg+":id/video_time_skip");
        when(node.isClickable()).thenReturn(true);when(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        when(active.findAccessibilityNodeInfosByViewId(pkg+":id/video_time_skip")).thenReturn(java.util.List.of(node));
        AccessibilityNodeInfo parent=mock(AccessibilityNodeInfo.class),container=mock(AccessibilityNodeInfo.class);
        when(node.getParent()).thenReturn(parent);when(parent.getParent()).thenReturn(container);
        when(parent.getPackageName()).thenReturn(pkg);when(container.getPackageName()).thenReturn(pkg);
        when(parent.isVisibleToUser()).thenReturn(true);when(container.isVisibleToUser()).thenReturn(true);
        when(parent.getViewIdResourceName()).thenReturn(pkg+":id/ll_top_right");when(container.getViewIdResourceName()).thenReturn(pkg+":id/video_layout");
        return parent;
    }
    private boolean cmccNativeScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findCmccSkip",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,SkipRules.CMCC);
    }
    @Test public void cmccNativeSkipWorksWithoutTextAdMarker() throws Exception {cmccNativeSetup();assertTrue(cmccNativeScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void cmccNativeWrongContainerRejected() throws Exception {when(cmccNativeSetup().getViewIdResourceName()).thenReturn("other");assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeChangedIdRejected() throws Exception {cmccNativeSetup();when(node.getViewIdResourceName()).thenReturn(SkipRules.CMCC+":id/video_time_skip","other");assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeChangedTextRejected() throws Exception {cmccNativeSetup();when(node.getText()).thenReturn("3 跳过","跳过登录");assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeWrongWindowRejected() throws Exception {cmccNativeSetup();when(active.getWindowId()).thenReturn(8);assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeOversizedTargetRejected() throws Exception {cmccNativeSetup();liveBounds.set(0,0,1080,2340);assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeExcludedAppRejected() throws Exception {cmccNativeSetup();prefs.edit().putStringSet("skip_excluded",java.util.Set.of(SkipRules.CMCC)).commit();assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    @Test public void cmccNativeDisabledFeatureRejected() throws Exception {cmccNativeSetup();prefs.edit().putBoolean("skip_ads",false).commit();assertFalse(cmccNativeScan());verify(node,never()).performAction(anyInt());}
    private boolean enqueueCmcc(boolean markerVisible) throws Exception {
        when(node.getPackageName()).thenReturn(SkipRules.CMCC);
        when(node.getText()).thenReturn("2 跳过");
        when(active.getPackageName()).thenReturn(SkipRules.CMCC);
        AccessibilityNodeInfo marker=mock(AccessibilityNodeInfo.class);
        when(marker.getPackageName()).thenReturn(SkipRules.CMCC);
        when(marker.getText()).thenReturn("广告");when(marker.isVisibleToUser()).thenReturn(markerVisible);
        when(active.findAccessibilityNodeInfosByText("广告")).thenReturn(java.util.List.of(marker));
        Method method=AdSkipService.class.getDeclaredMethod("queueCmccTap",AccessibilityNodeInfo.class,String.class);method.setAccessible(true);
        return (boolean)method.invoke(service,node,SkipRules.CMCC);
    }
    @Test public void cmccVisibleAdAndCountdownDispatch() throws Exception {
        assertTrue(enqueueCmcc(true));drain();
        ArgumentCaptor<GestureDescription> gesture=ArgumentCaptor.forClass(GestureDescription.class);
        verify(service).dispatchGesture(gesture.capture(),any(),any());
        android.graphics.RectF point=new android.graphics.RectF();gesture.getValue().getStroke(0).getPath().computeBounds(point,true);
        assertEquals(liveBounds.left+liveBounds.width()*0.75f,point.left,0.1f);
        assertEquals(liveBounds.exactCenterY(),point.top,0.1f);
    }
    @Test public void cmccMissingAdMarkerNeverTouches() throws Exception {
        assertFalse(enqueueCmcc(false));drain();noGesture();
    }
    @Test public void cmccAdDisappearsBeforeDispatch() throws Exception {
        assertTrue(enqueueCmcc(true));when(active.findAccessibilityNodeInfosByText("广告")).thenReturn(java.util.List.of());drain();noGesture();
    }
    @Test public void cmccChangedCountdownLabelNeverTouches() throws Exception {
        assertTrue(enqueueCmcc(true));when(node.getText()).thenReturn("跳过登录");drain();noGesture();
    }

    private AccessibilityNodeInfo countdownMarker() {
        when(node.getPackageName()).thenReturn("com.netease.cloudmusic");when(active.getPackageName()).thenReturn("com.netease.cloudmusic");
        when(node.getText()).thenReturn("跳过 5");when(node.isClickable()).thenReturn(true);
        when(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        when(active.findAccessibilityNodeInfosByText("跳过")).thenReturn(java.util.List.of(node));
        AccessibilityNodeInfo marker=mock(AccessibilityNodeInfo.class);
        when(marker.getPackageName()).thenReturn("com.netease.cloudmusic");when(marker.getText()).thenReturn("广告");when(marker.isVisibleToUser()).thenReturn(true);
        doAnswer(i->{((Rect)i.getArgument(0)).set(780,141,835,193);return null;}).when(marker).getBoundsInScreen(any());
        when(active.findAccessibilityNodeInfosByText("广告")).thenReturn(java.util.List.of(marker));return marker;
    }
    private AccessibilityNodeInfo musicPopupSetup(boolean renewal) throws Exception {
        String pkg=SkipRules.MI_MUSIC;
        when(node.getPackageName()).thenReturn(pkg);when(active.getPackageName()).thenReturn(pkg);
        when(node.isClickable()).thenReturn(true);when(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        when(node.getContentDescription()).thenReturn(renewal?"关闭弹框按钮":"关闭");
        liveBounds.set(500,1700,600,1800);
        AccessibilityNodeInfo context=mock(AccessibilityNodeInfo.class);
        when(context.getPackageName()).thenReturn(pkg);when(context.getWindowId()).thenReturn(7);
        when(context.refresh()).thenReturn(true);when(context.isVisibleToUser()).thenReturn(true);when(context.isEnabled()).thenReturn(true);
        if(renewal){
            when(active.findAccessibilityNodeInfosByText("关闭弹框按钮")).thenReturn(java.util.List.of(node));
            when(active.findAccessibilityNodeInfosByText("你已失去豪华绿钻全部权益")).thenReturn(java.util.List.of(context));
            when(context.getContentDescription()).thenReturn("你已失去豪华绿钻全部权益");
        }else{
            when(node.getViewIdResourceName()).thenReturn(pkg+":id/iv_close_dialog_button");
            when(active.findAccessibilityNodeInfosByViewId(pkg+":id/iv_close_dialog_button")).thenReturn(java.util.List.of(node));
            when(active.findAccessibilityNodeInfosByViewId(pkg+":id/iv_background_dialog_content")).thenReturn(java.util.List.of(context));
            when(context.getViewIdResourceName()).thenReturn(pkg+":id/iv_background_dialog_content");when(context.getContentDescription()).thenReturn("弹窗");
            AccessibilityNodeInfo parent=mock(AccessibilityNodeInfo.class);when(context.getParent()).thenReturn(parent);
            when(parent.getPackageName()).thenReturn(pkg);when(parent.getViewIdResourceName()).thenReturn(pkg+":id/fl_dialog_container");
            when(parent.refresh()).thenReturn(true);when(parent.isVisibleToUser()).thenReturn(true);when(parent.getWindowId()).thenReturn(7);
        }
        window(pkg,"com.miui.player.ui.MusicBrowserActivity");return context;
    }
    private boolean musicPopupScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findMusicPopup",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,SkipRules.MI_MUSIC);
    }
    @Test public void musicLaunchPromotionCloses() throws Exception {musicPopupSetup(false);assertTrue(musicPopupScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void musicLaunchRenewalCloses() throws Exception {musicPopupSetup(true);assertTrue(musicPopupScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void musicPromotionNeedsImageContext() throws Exception {when(musicPopupSetup(false).isVisibleToUser()).thenReturn(false);assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPromotionNeedsContainer() throws Exception {when(musicPopupSetup(false).getParent()).thenReturn(null);assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicRenewalNeedsExactContext() throws Exception {when(musicPopupSetup(true).getContentDescription()).thenReturn("播放设置");assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupNeverClicksRenewalPurchase() throws Exception {musicPopupSetup(true);when(node.getContentDescription()).thenReturn("¥2.88立即续费豪华绿钻");assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupChangedButtonDoesNotClick() throws Exception {musicPopupSetup(true);when(node.getContentDescription()).thenReturn("关闭弹框按钮","立即续费");assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupWrongWindowDoesNotClick() throws Exception {musicPopupSetup(false);when(active.getWindowId()).thenReturn(8);assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupContextWrongWindowDoesNotClick() throws Exception {when(musicPopupSetup(true).getWindowId()).thenReturn(8);assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupAfterEntryDoesNotClick() throws Exception {musicPopupSetup(false);org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(16));assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupRepeatedWindowDoesNotExtendEntry() throws Exception {musicPopupSetup(false);org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(16));window(SkipRules.MI_MUSIC,"android.app.Dialog");assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupDisabledDoesNotClick() throws Exception {musicPopupSetup(false);prefs.edit().putBoolean("popup_ads",false).commit();assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupExcludedDoesNotClick() throws Exception {musicPopupSetup(false);prefs.edit().putStringSet("skip_excluded",java.util.Set.of(SkipRules.MI_MUSIC)).commit();assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicPopupOversizedDoesNotClick() throws Exception {musicPopupSetup(false);liveBounds.set(0,0,1080,2340);assertFalse(musicPopupScan());verify(node,never()).performAction(anyInt());}
    private AccessibilityNodeInfo musicSetup(){
        countdownMarker();String pkg=SkipRules.MI_MUSIC;
        when(node.getPackageName()).thenReturn(pkg);when(active.getPackageName()).thenReturn(pkg);when(node.getText()).thenReturn("5 跳过");
        when(active.findAccessibilityNodeInfosByText("广告")).thenReturn(java.util.List.of());
        AccessibilityNodeInfo context=mock(AccessibilityNodeInfo.class);
        when(context.getPackageName()).thenReturn(pkg);when(context.getViewIdResourceName()).thenReturn(pkg+":id/root");
        when(context.getContentDescription()).thenReturn("开屏广告");when(context.getWindowId()).thenReturn(7);
        when(context.refresh()).thenReturn(true);when(context.isVisibleToUser()).thenReturn(true);when(context.isEnabled()).thenReturn(true);
        doAnswer(i->{((Rect)i.getArgument(0)).set(0,0,1080,2340);return null;}).when(context).getBoundsInScreen(any());
        when(active.findAccessibilityNodeInfosByViewId(pkg+":id/root")).thenReturn(java.util.List.of(context));
        return context;
    }
    private boolean musicScan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findCountdownLabel",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,active,SkipRules.MI_MUSIC);
    }
    @Test public void musicIndependentSkipClicksWithSplashContext() throws Exception {musicSetup();assertTrue(musicScan());verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void musicVipEntryDoesNotClick() throws Exception {musicSetup();when(node.getText()).thenReturn("开VIP免广告｜");assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicCombinedVipAndSkipDoesNotClick() throws Exception {musicSetup();when(node.getText()).thenReturn("开VIP免广告｜5 跳过");assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicInvisibleContextDoesNotClick() throws Exception {when(musicSetup().isVisibleToUser()).thenReturn(false);assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicContextDisappearsBeforeClick() throws Exception {when(musicSetup().getContentDescription()).thenReturn("开屏广告","首页");assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicWrongContextIdDoesNotClick() throws Exception {when(musicSetup().getViewIdResourceName()).thenReturn(SkipRules.MI_MUSIC+":id/other");assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicOtherWindowContextDoesNotClick() throws Exception {when(musicSetup().getWindowId()).thenReturn(8);assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicExcludedDoesNotClick() throws Exception {musicSetup();prefs.edit().putStringSet("skip_excluded",java.util.Set.of(SkipRules.MI_MUSIC)).commit();assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    @Test public void musicOversizedTargetDoesNotClick() throws Exception {musicSetup();liveBounds.set(0,0,1080,2340);assertFalse(musicScan());verify(node,never()).performAction(anyInt());}
    private void countdownScan() throws Exception {
        Method method=AdSkipService.class.getDeclaredMethod("findCountdownLabel",AccessibilityNodeInfo.class,String.class);method.setAccessible(true);
        method.invoke(service,active,"com.netease.cloudmusic");
    }
    @Test public void adjacentAdCountdownClicks() throws Exception {countdownMarker();countdownScan();verify(node).performAction(AccessibilityNodeInfo.ACTION_CLICK);noGesture();}
    @Test public void countdownWithoutVisibleMarkerNeverClicks() throws Exception {when(countdownMarker().isVisibleToUser()).thenReturn(false);countdownScan();verify(node,never()).performAction(anyInt());}
    @Test public void unrelatedAdElsewhereNeverClicks() throws Exception {
        AccessibilityNodeInfo marker=countdownMarker();doAnswer(i->{((Rect)i.getArgument(0)).set(780,800,835,850);return null;}).when(marker).getBoundsInScreen(any());
        countdownScan();verify(node,never()).performAction(anyInt());
    }
    @Test public void countdownChangedToLoginNeverClicks() throws Exception {
        countdownMarker();when(node.getText()).thenReturn("跳过 5","跳过登录");countdownScan();verify(node,never()).performAction(anyInt());
    }
    @Test public void countdownOtherWindowNeverClicks() throws Exception {
        countdownMarker();when(active.getWindowId()).thenReturn(9);countdownScan();verify(node,never()).performAction(anyInt());
    }

    @Test public void touchOnlyLabelDispatches() throws Exception {
        assertFalse(node.isClickable());assertTrue(enqueue());assertEquals(0,cooldown());drain();
        verify(service).dispatchGesture(any(GestureDescription.class),any(),any(Handler.class));
        verify(node,never()).performAction(anyInt());assertTrue(cooldown()>0);
    }
    @Test public void failedRefreshDoesNotConsumeCooldown() throws Exception {
        assertTrue(enqueue());when(node.refresh()).thenReturn(false);drain();noGesture();assertEquals(0,cooldown());
        when(node.refresh()).thenReturn(true);assertTrue(enqueue());drain();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void changedWindowPreventsTouch() throws Exception {
        assertTrue(enqueue());when(active.getWindowId()).thenReturn(8);drain();noGesture();assertEquals(0,cooldown());
    }
    @Test public void refreshedSafePositionIsUsed() throws Exception {
        assertTrue(enqueue());liveBounds.offset(-20,10);drain();
        ArgumentCaptor<GestureDescription> gesture=ArgumentCaptor.forClass(GestureDescription.class);
        verify(service).dispatchGesture(gesture.capture(),any(),any());
        Rect actual=new Rect();android.graphics.RectF box=new android.graphics.RectF();
        gesture.getValue().getStroke(0).getPath().computeBounds(box,true);box.round(actual);
        assertEquals(liveBounds.centerX(),actual.left);assertEquals(liveBounds.centerY(),actual.top);
    }
    @Test public void movedOutOfSafeAreaPreventsTouch() throws Exception {
        assertTrue(enqueue());liveBounds.offset(0,1000);drain();noGesture();assertEquals(0,cooldown());
    }
    @Test public void disabledWhileQueuedPreventsTouch() throws Exception {
        assertTrue(enqueue());prefs.edit().putBoolean("skip_ads",false).commit();drain();noGesture();assertEquals(0,cooldown());
    }
    @Test public void dispatchRejectedDoesNotConsumeCooldown() throws Exception {
        doReturn(false).when(service).dispatchGesture(any(),any(),any());assertTrue(enqueue());drain();assertEquals(0,cooldown());
    }
    @Test public void duplicateQueueIsRejected() throws Exception {
        assertTrue(enqueue());assertFalse(enqueue());drain();verify(service,times(1)).dispatchGesture(any(),any(),any());
    }
    @Test public void cancelledGestureDoesNotCountAsSuccess() throws Exception {
        assertTrue(enqueue());drain();
        ArgumentCaptor<AccessibilityService.GestureResultCallback> callback=ArgumentCaptor.forClass(AccessibilityService.GestureResultCallback.class);
        verify(service).dispatchGesture(any(),callback.capture(),any());callback.getValue().onCancelled(null);
        assertEquals(0,cooldown());assertEquals(0,prefs.getLong("skip_clicks",0));
    }
    private void scan() throws Exception {
        Method scan=AdSkipService.class.getDeclaredMethod("scan",String.class);scan.setAccessible(true);
        scan.invoke(service,"ctrip.android.view");drain();
    }
    @Test public void scanFindsTouchOnlySkip() throws Exception {
        when(active.getChildCount()).thenReturn(1);when(active.getChild(0)).thenReturn(node);
        scan();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void skipBeyondTraversalBudgetMustStillBeFound() throws Exception {
        AccessibilityNodeInfo content=mock(AccessibilityNodeInfo.class);
        when(content.getPackageName()).thenReturn("ctrip.android.view");
        when(active.getChildCount()).thenReturn(351);
        when(active.getChild(anyInt())).thenReturn(content);when(active.getChild(350)).thenReturn(node);
        when(active.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(java.util.List.of(node));
        scan();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void searchSubstringMustNotClickRewardPrompt() throws Exception {
        when(active.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(java.util.List.of(node));
        when(node.getText()).thenReturn("跳过广告领取奖励");scan();noGesture();
    }
    @Test public void searchMustRejectOtherPackage() throws Exception {
        when(active.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(java.util.List.of(node));
        when(node.getPackageName()).thenReturn("com.example.other");scan();noGesture();
    }
    @Test public void textChangedBeforeTouchMustCancel() throws Exception {
        assertTrue(enqueue());when(node.getText()).thenReturn("立即购买");drain();noGesture();
    }
    @Test public void unavailableTextSearchFallsBackToTraversal() throws Exception {
        when(active.findAccessibilityNodeInfosByText("跳过广告")).thenThrow(new IllegalStateException("window refreshing"));
        when(active.getChildCount()).thenReturn(1);when(active.getChild(0)).thenReturn(node);
        scan();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void changedHierarchyStopsObsoleteTraversal() throws Exception {
        when(active.getChildCount()).thenReturn(2);
        doAnswer(i->{
            Field revision=AdSkipService.class.getDeclaredField("eventRevision");revision.setAccessible(true);
            revision.setLong(service,revision.getLong(service)+1);
            return node;
        }).when(active).getChild(0);
        scan();verify(active,never()).getChild(1);noGesture();
        // A fresh pass sees the new hierarchy and can dispatch normally.
        when(active.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(java.util.List.of(node));
        scan();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void ctripScanInvalidatesCacheBeforeReadingRoot() throws Exception {
        scan();InOrder order=inOrder(service);order.verify(service).clearCache();order.verify(service).getRootInActiveWindow();
    }
}
