package cn.quietstart;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class AdSkipCoverageTest {
    private AdSkipService service;
    private AccessibilityNodeInfo root,button,marker;
    private MockedStatic<FilterVpnService> vpn;
    private MockedStatic<AccessibilityNodeInfo> nodes;
    private SharedPreferences prefs;
    private String pkg=SkipRules.NETEASE;
    private Rect buttonBox,markerBox;
    @Before public void setup(){
        service=spy(Robolectric.buildService(AdSkipService.class).create().get());
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("coverage-test",0);
        prefs.edit().clear().putBoolean("skip_ads",true).commit();
        vpn=mockStatic(FilterVpnService.class);vpn.when(()->FilterVpnService.prefs(any())).thenReturn(prefs);
        nodes=mockStatic(AccessibilityNodeInfo.class);
        service.getResources().getDisplayMetrics().widthPixels=1200;
        service.getResources().getDisplayMetrics().heightPixels=2608;
        root=node(null);button=node("跳过 4");marker=node("广告");
        buttonBox=new Rect(950,150,1160,250);markerBox=new Rect(830,170,920,220);
        bounds(button,buttonBox);bounds(marker,markerBox);bounds(root,new Rect(0,0,1200,2608));
        when(button.isClickable()).thenReturn(true);
        when(root.findAccessibilityNodeInfosByText("跳过")).thenReturn(List.of(button));
        when(root.findAccessibilityNodeInfosByText("广告")).thenReturn(List.of(marker));
        doReturn(root).when(service).getRootInActiveWindow();
    }
    @After public void cleanup(){service.onDestroy();nodes.close();vpn.close();}
    private AccessibilityNodeInfo node(String text){
        AccessibilityNodeInfo n=mock(AccessibilityNodeInfo.class);
        when(n.getPackageName()).thenAnswer(i->pkg);when(n.getText()).thenReturn(text);
        when(n.getWindowId()).thenReturn(7);when(n.refresh()).thenReturn(true);
        when(n.isVisibleToUser()).thenReturn(true);when(n.isEnabled()).thenReturn(true);
        when(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);
        nodes.when(()->AccessibilityNodeInfo.obtain(n)).thenReturn(n);return n;
    }
    private void bounds(AccessibilityNodeInfo n,Rect box){doAnswer(i->{((Rect)i.getArgument(0)).set(box);return null;}).when(n).getBoundsInScreen(any());}
    private boolean countdown() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("findCountdownLabel",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);
        return (boolean)m.invoke(service,root,pkg);
    }
    private void scan() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("scan",String.class);m.setAccessible(true);m.invoke(service,pkg);
    }
    private void noClick(){verify(button,never()).performAction(anyInt());verify(service,never()).dispatchGesture(any(),any(),any());}
    private AccessibilityNodeInfo wrapper(){
        AccessibilityNodeInfo parent=node(null);bounds(parent,new Rect(buttonBox));
        when(parent.isClickable()).thenReturn(true);when(parent.getChildCount()).thenReturn(1);when(parent.getChild(0)).thenReturn(button);
        when(button.isClickable()).thenReturn(false);when(button.getParent()).thenReturn(parent);return parent;
    }
    @Test public void screenshotNetEaseTopRightStillClicks() throws Exception {assertTrue(countdown());verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void idlefishTopRightCountdownWithAdMarkerClicks() throws Exception {pkg="com.taobao.idlefish";assertTrue(countdown());verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void idlefishBottomLeftExplicitSkipClicks() throws Exception {pkg="com.taobao.idlefish";explicitLabel();buttonBox.set(35,2350,250,2450);assertTrue(explicitClick());}
    @Test public void idlefishBareSkipWithoutAdContextIsNotBlindlyClicked() throws Exception {pkg="com.taobao.idlefish";when(button.getText()).thenReturn("跳过");when(root.findAccessibilityNodeInfosByText("广告")).thenReturn(List.of());assertFalse(countdown());noClick();}
    private void enter(String app) throws Exception {
        android.view.accessibility.AccessibilityEvent e=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setClassName("android.widget.FrameLayout");
        Method m=AdSkipService.class.getDeclaredMethod("trackWindow",android.view.accessibility.AccessibilityEvent.class,String.class);m.setAccessible(true);m.invoke(service,e,app);e.recycle();
    }
    private void promotion(String app,String text) throws Exception {
        pkg=app;when(button.getText()).thenReturn(text);when(root.findAccessibilityNodeInfosByText("广告")).thenReturn(List.of());enter(app);
    }
    @Test public void netdiskScreenshotWithoutAdMarkerClicks() throws Exception {promotion(SkipRules.NETDISK,"跳过 3");assertTrue(countdown());}
    @Test public void idlefishScreenshotWithoutAdMarkerClicks() throws Exception {promotion(SkipRules.IDLEFISH,"跳过 3");assertTrue(countdown());}
    @Test public void netdiskBareSkipDuringEntryClicks() throws Exception {promotion(SkipRules.NETDISK,"跳过");assertTrue(countdown());}
    @Test public void idlefishBareSkipDuringEntryClicks() throws Exception {promotion(SkipRules.IDLEFISH,"跳过");assertTrue(countdown());}
    @Test public void ujingBareSkipWithoutAdMarkerClicks() throws Exception {promotion(SkipRules.UJING,"跳过");assertTrue(countdown());verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void ujingCountdownWithoutAdMarkerClicks() throws Exception {promotion(SkipRules.UJING,"5秒 跳过");assertTrue(countdown());}
    @Test public void ujingBottomLeftSkipClicks() throws Exception {promotion(SkipRules.UJING,"跳过 3");buttonBox.set(35,2350,250,2450);assertTrue(countdown());}
    @Test public void ujingBottomRightSkipClicks() throws Exception {promotion(SkipRules.UJING,"跳过");buttonBox.set(950,2350,1160,2450);assertTrue(countdown());}
    @Test public void ujingDescriptionSkipClicks() throws Exception {promotion(SkipRules.UJING,null);when(button.getContentDescription()).thenReturn("跳过 3");assertTrue(countdown());}
    @Test public void ujingUnclickableCountdownUsesLiveTouch() throws Exception {
        promotion(SkipRules.UJING,"跳过 5");when(button.isClickable()).thenReturn(false);
        doReturn(true).when(service).dispatchGesture(any(),any(),any());scan();idle();verify(service).dispatchGesture(any(),any(),any());
    }
    @Test public void ujingUpdatePromptNotClicked() throws Exception {promotion(SkipRules.UJING,"更新");assertFalse(countdown());assertFalse(touch());noClick();}
    @Test public void ujingLoginSkipNotClicked() throws Exception {promotion(SkipRules.UJING,"跳过登录");assertFalse(countdown());assertFalse(touch());noClick();}
    @Test public void ujingSkipOutsideEntryNotClicked() throws Exception {promotion(SkipRules.UJING,"跳过");org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10));assertFalse(countdown());assertFalse(touch());noClick();}
    @Test public void ujingExcludedSkipNotClicked() throws Exception {promotion(SkipRules.UJING,"跳过 3");prefs.edit().putStringSet("skip_excluded",java.util.Set.of(pkg)).commit();assertFalse(countdown());assertFalse(touch());noClick();}
    @Test public void ujingMiddlePageSkipNotClicked() throws Exception {promotion(SkipRules.UJING,"跳过");buttonBox.set(500,1200,700,1300);assertFalse(countdown());assertFalse(touch());noClick();}
    @Test public void promotionBottomLeftClicks() throws Exception {promotion(SkipRules.NETDISK,"跳过");buttonBox.set(35,2350,250,2450);assertTrue(countdown());}
    @Test public void promotionBottomRightClicks() throws Exception {promotion(SkipRules.IDLEFISH,"跳过 3");buttonBox.set(950,2350,1160,2450);assertTrue(countdown());}
    @Test public void promotionSmallParentClicks() throws Exception {promotion(SkipRules.NETDISK,"跳过 3");AccessibilityNodeInfo p=wrapper();assertTrue(countdown());verify(p).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    @Test public void promotionDescriptionOnlyClicks() throws Exception {promotion(SkipRules.IDLEFISH,null);when(button.getContentDescription()).thenReturn("跳过 3");assertTrue(countdown());}
    @Test public void promotionEntryExpires() throws Exception {promotion(SkipRules.NETDISK,"跳过");org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10));assertFalse(countdown());noClick();}
    @Test public void promotionRepeatedWindowEventDoesNotExtendEntry() throws Exception {promotion(SkipRules.IDLEFISH,"跳过");org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10));enter(pkg);assertFalse(countdown());noClick();}
    @Test public void promotionForegroundSwitchCancelsEntry() throws Exception {promotion(SkipRules.NETDISK,"跳过 3");enter("com.example.other");assertFalse(countdown());noClick();}
    @Test public void promotionWrongAppNeedsMarker() throws Exception {promotion("com.example.other","跳过 3");assertFalse(countdown());noClick();}
    @Test public void promotionSmallPopupRejected() throws Exception {promotion(SkipRules.IDLEFISH,"跳过");bounds(root,new Rect(800,100,1200,400));assertFalse(countdown());noClick();}
    @Test public void promotionCenterLabelRejected() throws Exception {promotion(SkipRules.NETDISK,"跳过");buttonBox.set(500,1200,700,1300);assertFalse(countdown());noClick();}
    @Test public void promotionLoginSkipRejected() throws Exception {promotion(SkipRules.IDLEFISH,"跳过登录");assertFalse(countdown());noClick();}
    @Test public void promotionPurchaseTextRejected() throws Exception {promotion(SkipRules.NETDISK,"跳过 3 立即购买");assertFalse(countdown());noClick();}
    @Test public void promotionMultiActionParentRejected() throws Exception {promotion(SkipRules.NETDISK,"跳过");AccessibilityNodeInfo p=wrapper();when(p.getChildCount()).thenReturn(2);assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void promotionOnlyOneUnmarkedClickPerEntry() throws Exception {promotion(SkipRules.IDLEFISH,"跳过");assertTrue(countdown());assertFalse(countdown());verify(button,times(1)).performAction(AccessibilityNodeInfo.ACTION_CLICK);}
    private AccessibilityNodeInfo splitCountdown() throws Exception {
        promotion(SkipRules.NETDISK,"跳过");buttonBox.set(1027,229,1099,277);when(button.isClickable()).thenReturn(false);
        AccessibilityNodeInfo parent=node(null),digit=node("5");bounds(parent,new Rect(997,211,1162,295));bounds(digit,new Rect(1111,229,1132,277));
        when(parent.getChildCount()).thenReturn(2);when(parent.getChild(0)).thenReturn(button);when(parent.getChild(1)).thenReturn(digit);when(button.getParent()).thenReturn(parent);return digit;
    }
    private boolean touch() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("queuePromotionTap",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);return (boolean)m.invoke(service,button,pkg);
    }
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    @Test public void observedSplitCountdownUsesTouch() throws Exception {splitCountdown();doReturn(true).when(service).dispatchGesture(any(),any(),any());assertTrue(touch());idle();verify(service).dispatchGesture(any(),any(),any());verify(button,never()).performAction(anyInt());assertEquals(0,prefs.getLong("skip_clicks",0));}
    @Test public void splitCountdownPurchaseSiblingRejected() throws Exception {AccessibilityNodeInfo digit=splitCountdown();when(digit.getText()).thenReturn("购买");assertFalse(touch());noClick();}
    @Test public void splitCountdownChangesBeforeTouchRejected() throws Exception {AccessibilityNodeInfo digit=splitCountdown();assertTrue(touch());when(digit.getText()).thenReturn("购买");idle();noClick();}
    @Test public void promotionMovesBeforeTouchRejected() throws Exception {splitCountdown();assertTrue(touch());buttonBox.offset(0,100);idle();noClick();}
    @Test public void promotionDisabledBeforeTouchRejected() throws Exception {splitCountdown();assertTrue(touch());prefs.edit().putBoolean("skip_ads",false).commit();idle();noClick();}
    @Test public void promotionExcludedBeforeTouchRejected() throws Exception {splitCountdown();assertTrue(touch());prefs.edit().putStringSet("skip_excluded",java.util.Set.of(pkg)).commit();idle();noClick();}
    @Test public void promotionWrongWindowBeforeTouchRejected() throws Exception {splitCountdown();assertTrue(touch());when(root.getWindowId()).thenReturn(8);idle();noClick();}
    @Test public void promotionExpiredBeforeTouchRejected() throws Exception {splitCountdown();assertTrue(touch());org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10));idle();noClick();}
    @Test public void bareUnclickableWithoutCountdownSiblingNotTouched() throws Exception {promotion(SkipRules.IDLEFISH,"跳过");when(button.isClickable()).thenReturn(false);assertFalse(touch());noClick();}
    @Test public void backgroundOwnWindowEventDoesNotCancelPromotion() throws Exception {promotion(SkipRules.NETDISK,"跳过");enter("cn.quietstart");assertTrue(countdown());}
    @Test public void backgroundOtherAppEventRequiresFocusResolution() throws Exception {
        promotion(SkipRules.NETDISK,"跳过");android.view.accessibility.AccessibilityEvent e=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(SkipRules.IDLEFISH);service.onAccessibilityEvent(e);e.recycle();assertTrue(countdown());
    }
    @Test public void focusedWindowOverridesStaleActiveRoot() throws Exception {
        promotion(SkipRules.NETDISK,"跳过");AccessibilityNodeInfo stale=node(null);when(stale.getPackageName()).thenReturn("cn.quietstart");doReturn(stale).when(service).getRootInActiveWindow();
        android.view.accessibility.AccessibilityWindowInfo window=mock(android.view.accessibility.AccessibilityWindowInfo.class);when(window.isFocused()).thenReturn(true);when(window.getRoot()).thenReturn(root);doReturn(List.of(window)).when(service).getWindows();assertTrue(countdown());
    }
    @Test public void focusedOtherWindowPreventsTouch() throws Exception {
        splitCountdown();AccessibilityNodeInfo other=node(null);when(other.getPackageName()).thenReturn("com.example.other");
        android.view.accessibility.AccessibilityWindowInfo window=mock(android.view.accessibility.AccessibilityWindowInfo.class);when(window.isFocused()).thenReturn(true);when(window.getRoot()).thenReturn(other);doReturn(List.of(window)).when(service).getWindows();assertFalse(touch());noClick();
    }
    @Test public void descriptionOnlyCountdownClicks() throws Exception {when(button.getText()).thenReturn(null);when(button.getContentDescription()).thenReturn("跳过 4");assertTrue(countdown());}
    @Test public void nonClickableTextUsesSmallSingleChildParent() throws Exception {AccessibilityNodeInfo parent=wrapper();assertTrue(countdown());verify(parent).performAction(AccessibilityNodeInfo.ACTION_CLICK);verify(button,never()).performAction(anyInt());}
    @Test public void bottomLeftCountdownWithRightMarkerClicks() throws Exception {buttonBox.set(35,2350,250,2450);markerBox.set(275,2370,365,2420);assertTrue(countdown());}
    @Test public void bottomRightCountdownWithLeftMarkerClicks() throws Exception {buttonBox.set(950,2350,1160,2450);markerBox.set(830,2370,920,2420);assertTrue(countdown());}
    @Test public void bottomExplicitAdSkipNeedsNoSeparateMarker() throws Exception {
        buttonBox.set(35,2350,250,2450);when(button.getText()).thenReturn("跳过广告");when(root.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(List.of(button));
        when(root.findAccessibilityNodeInfosByText("广告")).thenReturn(List.of());scan();verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    @Test public void missingTextIndexUsesTree() throws Exception {
        when(root.findAccessibilityNodeInfosByText("跳过")).thenReturn(List.of());when(root.findAccessibilityNodeInfosByText("广告")).thenReturn(List.of());
        when(root.getChildCount()).thenReturn(2);when(root.getChild(0)).thenReturn(marker);when(root.getChild(1)).thenReturn(button);
        scan();verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    @Test public void mapScreenshotBrandMarkerAtBottomClicksWithoutActivityGuess() throws Exception {
        pkg=SkipRules.BAIDU;when(button.getText()).thenReturn("跳过 02");when(marker.getText()).thenReturn("品牌广告");markerBox.set(155,2365,305,2410);
        assertTrue(countdown());verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    @Test public void bareSkipWithNearbyAdClicks() throws Exception {when(button.getText()).thenReturn("跳过");assertTrue(countdown());}
    @Test public void bareSkipWithoutAdDoesNotClick() throws Exception {when(button.getText()).thenReturn("跳过");when(marker.isVisibleToUser()).thenReturn(false);assertFalse(countdown());noClick();}
    @Test public void bottomLoginNotClicked() throws Exception {buttonBox.set(35,2350,250,2450);when(button.getText()).thenReturn("跳过登录");assertFalse(countdown());noClick();}
    @Test public void bottomCenterNotClicked() throws Exception {buttonBox.set(500,2350,700,2450);markerBox.set(380,2370,470,2420);assertFalse(countdown());noClick();}
    @Test public void middleOfPageNotClicked() throws Exception {buttonBox.set(950,1350,1160,1450);markerBox.set(830,1370,920,1420);assertFalse(countdown());noClick();}
    @Test public void fullPageClickableParentRejected() throws Exception {AccessibilityNodeInfo p=wrapper();bounds(p,new Rect(0,0,1200,2608));assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void multiActionParentRejected() throws Exception {AccessibilityNodeInfo p=wrapper();when(p.getChildCount()).thenReturn(2);assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void parentInOtherWindowRejected() throws Exception {AccessibilityNodeInfo p=wrapper();when(p.getWindowId()).thenReturn(9);assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void markerDisappearsBeforeParentClickRejected() throws Exception {AccessibilityNodeInfo p=wrapper();when(marker.isVisibleToUser()).thenReturn(true,false);assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void brandMarkerIsNotAGlobalException() throws Exception {when(marker.getText()).thenReturn("品牌广告");markerBox.set(155,2365,305,2410);assertFalse(countdown());noClick();}
    @Test public void mapBrandMarkerDoesNotAuthorizeBareSkip() throws Exception {pkg=SkipRules.BAIDU;when(button.getText()).thenReturn("跳过");when(marker.getText()).thenReturn("品牌广告");markerBox.set(155,2365,305,2410);assertFalse(countdown());noClick();}
    @Test public void markerInAnotherWindowRejected() throws Exception {when(marker.getWindowId()).thenReturn(8);assertFalse(countdown());noClick();}
    @Test public void changedLabelRejected() throws Exception {when(button.getText()).thenReturn("跳过 4","跳过登录");assertFalse(countdown());noClick();}
    @Test public void excludedAppRejected() throws Exception {prefs.edit().putStringSet("skip_excluded",java.util.Set.of(pkg)).commit();assertFalse(countdown());noClick();}
    @Test public void netEaseRefreshesCacheBeforeRoot() throws Exception {scan();org.mockito.InOrder order=inOrder(service);order.verify(service).clearCache();order.verify(service).getRootInActiveWindow();}
    private void explicitLabel(){
        when(button.getText()).thenReturn("跳过广告");when(root.findAccessibilityNodeInfosByText("跳过广告")).thenReturn(List.of(button));
    }
    private boolean explicitClick() throws Exception {
        Method m=AdSkipService.class.getDeclaredMethod("clickSkip",AccessibilityNodeInfo.class,String.class,boolean.class);m.setAccessible(true);
        return (boolean)m.invoke(service,button,pkg,true);
    }
    @Test public void bottomRightExplicitLabelClicks() throws Exception {explicitLabel();buttonBox.set(950,2350,1160,2450);assertTrue(explicitClick());}
    @Test public void explicitLabelChangingToPurchaseIsRejected() throws Exception {
        explicitLabel();when(button.getText()).thenReturn("立即购买");assertFalse(explicitClick());noClick();
    }
    @Test public void explicitLabelMultiActionParentRejected() throws Exception {explicitLabel();AccessibilityNodeInfo p=wrapper();when(p.getChildCount()).thenReturn(2);assertFalse(explicitClick());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void explicitClickableStateRefreshesBeforeDecision() throws Exception {
        explicitLabel();when(button.isClickable()).thenReturn(false);when(button.refresh()).thenAnswer(i->{when(button.isClickable()).thenReturn(true);return true;});
        assertTrue(explicitClick());verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    @Test public void changedTargetPackageRejected() throws Exception {AccessibilityNodeInfo p=wrapper();when(p.getPackageName()).thenReturn("com.example.other");assertFalse(countdown());verify(p,never()).performAction(anyInt());noClick();}
    @Test public void switchedForegroundRejected() throws Exception {AccessibilityNodeInfo other=node(null);when(other.getPackageName()).thenReturn("com.example.other");doReturn(root,other).when(service).getRootInActiveWindow();assertFalse(countdown());noClick();}
    @Test public void disabledSkippingRejected() throws Exception {prefs.edit().putBoolean("skip_ads",false).commit();assertFalse(countdown());noClick();}
    @Test public void descriptionOnlyMarkerMatches() throws Exception {when(marker.getText()).thenReturn(null);when(marker.getContentDescription()).thenReturn("广告");assertTrue(countdown());}
    @Test public void overlappingMarkerRejected() throws Exception {markerBox.set(buttonBox);assertFalse(countdown());noClick();}
    @Test public void mapBrandMarkerWithPurchaseTextRejected() throws Exception {pkg=SkipRules.BAIDU;when(marker.getText()).thenReturn("品牌广告 立即购买");markerBox.set(155,2365,305,2410);assertFalse(countdown());noClick();}
    @Test public void mapBrandMarkerOnSmallPopupRejected() throws Exception {pkg=SkipRules.BAIDU;when(marker.getText()).thenReturn("品牌广告");markerBox.set(155,2365,305,2410);bounds(root,new Rect(800,100,1200,400));assertFalse(countdown());noClick();}
    @Test public void bottomBareEntryNeedsAdContext() throws Exception {
        pkg=SkipRules.BAIDU;buttonBox.set(35,2350,250,2450);when(button.getText()).thenReturn("跳过");
        android.view.accessibility.AccessibilityEvent event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setClassName("com.baidu.baidumaps.MapsActivity");
        Method track=AdSkipService.class.getDeclaredMethod("trackWindow",android.view.accessibility.AccessibilityEvent.class,String.class);track.setAccessible(true);track.invoke(service,event,pkg);event.recycle();
        Method m=AdSkipService.class.getDeclaredMethod("clickEntrySkip",AccessibilityNodeInfo.class,String.class);m.setAccessible(true);assertFalse((boolean)m.invoke(service,button,pkg));noClick();
    }
}
