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
