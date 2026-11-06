package cn.quietstart;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Event-driven matching; Ctrip fallback taps only a freshly verified ad-skip label. */
public final class AdSkipService extends AccessibilityService {
    public static volatile boolean connected;
    public static volatile long received,scans;
    public static volatile String outcome="尚未扫描",lastPackage="";
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private volatile boolean destroyed;
    private volatile long lastScan,lastClick;
    private volatile long eventRevision;
    private volatile boolean tapPending;
    private volatile String foregroundPackage="",jdActivity="",mapActivity="";
    private volatile long jdEntered=-1,mapEntered=-1,musicEntered=-1;
    private volatile String lastMapReport="";
    private void mapReport(String key,String message){
        FilterVpnService.prefs(this).edit().putString(key,message).putLong(key+"_time",System.currentTimeMillis()).apply();
    }
    private void recordMapScan(String pkg){
        long age=SystemClock.elapsedRealtime()-mapEntered;
        if(!SkipRules.BAIDU.equals(pkg)||mapEntered<0||age<0||age>12000||outcome.equals(lastMapReport))return;
        lastMapReport=outcome;mapReport("baidu_last_scan",outcome);
    }
    private void trackWindow(AccessibilityEvent event,String pkg){
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(!pkg.equals(foregroundPackage)){
            foregroundPackage=pkg;jdActivity="";mapActivity="";
            jdEntered=SkipRules.JD.equals(pkg)?SystemClock.elapsedRealtime():-1;
            mapEntered=SkipRules.BAIDU.equals(pkg)?SystemClock.elapsedRealtime():-1;
            musicEntered=SkipRules.MI_MUSIC.equals(pkg)?SystemClock.elapsedRealtime():-1;
            if(mapEntered>=0){lastMapReport="";mapReport("baidu_last_scan","开始观察启动界面");}
        }
        CharSequence cls=event.getClassName();
        if(SkipRules.JD.equals(pkg)&&cls!=null&&(cls.toString().startsWith("com.jingdong.")||cls.toString().startsWith("com.jd.")))jdActivity=cls.toString();
        if(SkipRules.BAIDU.equals(pkg)&&cls!=null&&cls.toString().startsWith("com.baidu."))mapActivity=cls.toString();
    }
    private boolean jdSplashWindow(){
        long age=SystemClock.elapsedRealtime()-jdEntered;
        return SkipRules.JD.equals(foregroundPackage)&&"com.jingdong.app.mall.MainFrameActivity".equals(jdActivity)&&jdEntered>=0&&age>=0&&age<10000;
    }
    private boolean entrySplashWindow(String pkg){
        if(SkipRules.JD.equals(pkg))return jdSplashWindow();
        long age=SystemClock.elapsedRealtime()-mapEntered;
        return SkipRules.BAIDU.equals(pkg)&&pkg.equals(foregroundPackage)&&mapEntered>=0&&age>=0&&age<10000
                &&("com.baidu.baidumaps.MapsActivity".equals(mapActivity)||"com.baidu.baidumaps.WelcomeScreen".equals(mapActivity));
    }
    private void tapStatus(String message){
        outcome=message;
        android.util.Log.i("QuietStartTap",message);
        FilterVpnService.prefs(this).edit().putString("ctrip_tap_status",message).apply();
    }
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private String pendingPackage;
    private boolean scheduled;
    private final Runnable drain=()->{
        scheduled=false;
        if(destroyed||!enabled(this)){pendingPackage=null;return;}
        if(pendingPackage==null||busy.get())return;
        String pkg=pendingPackage;pendingPackage=null;
        busy.set(true);lastScan=SystemClock.elapsedRealtime();
        try{
            worker.execute(()->{
                try{scan(pkg);}
                catch(RuntimeException error){outcome="界面读取中断："+error.getClass().getSimpleName();}
                finally{try{recordMapScan(pkg);}finally{busy.set(false);handler.post(this::schedulePending);}}
            });
        }catch(RejectedExecutionException ignored){busy.set(false);}
    };
    public static boolean enabled(Context c){return FilterVpnService.prefs(c).getBoolean("skip_ads",false);}
    public static boolean popupsEnabled(Context c){return FilterVpnService.prefs(c).getBoolean("popup_ads",true);}
    @Override protected void onServiceConnected(){
        android.accessibilityservice.AccessibilityServiceInfo info=getServiceInfo();
        info.packageNames=null;
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        connected=true;outcome="已连接，等待应用界面";FilterVpnService.notifyState(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(destroyed||!enabled(this)||event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        trackWindow(event,pkg);
        if(!SkipRules.eligible(pkg)||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return;
        received++;lastPackage=pkg;
        eventRevision++;
        pendingPackage=pkg;
        schedulePending();
    }
    private void schedulePending(){
        if(destroyed||pendingPackage==null||scheduled||busy.get())return;
        long now=SystemClock.elapsedRealtime();
        long delay=Math.max(0,Math.max(lastScan+300-now,lastClick+2500-now));
        scheduled=true;handler.postDelayed(drain,delay);
    }
    private void scan(String pkg){
        scans++;outcome="未发现符合规则的按钮";
        long revision=eventRevision;
        if("ctrip.android.view".equals(pkg)||SkipRules.CMCC.equals(pkg)||SkipRules.JD.equals(pkg)||SkipRules.BAIDU.equals(pkg)||SkipRules.MI_MUSIC.equals(pkg))clearCache();
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null){outcome="当前界面未提供控件";return;}
        ArrayDeque<AccessibilityNodeInfo> pending=new ArrayDeque<>();pending.add(root);
        try{
            // A late overlay can sit beyond the bounded content traversal. Ask Android
            // for ad-skip labels first, then apply the same exact label and safety checks.
            if(!pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName())){outcome="活动窗口已切换，未处理旧界面";return;}
            if(findCmccSkip(root,pkg)||findMapSkip(root,pkg)||findEntrySkip(root,pkg)||findSkipLabel(root,pkg)||findCountdownLabel(root,pkg)||findMusicPopup(root,pkg))return;
            int count=0;
            // Yield obsolete traversal to the pending event instead of spending the
            // short splash countdown walking a hierarchy that has already changed.
            while(!pending.isEmpty()&&count++<350&&revision==eventRevision&&!destroyed&&enabled(this)){
                AccessibilityNodeInfo node=pending.removeFirst();
                try{
                    if(!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName()))continue;
                    if(node.isVisibleToUser()&&node.isEnabled()){
                        boolean skip=SkipRules.matches(pkg,node.getText())||SkipRules.matches(pkg,node.getContentDescription());
                        boolean popup=popupsEnabled(this)&&(SkipRules.explicitClose(node.getText())||SkipRules.explicitClose(node.getContentDescription())||localAdClose(node,pkg));
                        if(skip||popup){
                            outcome="识别到按钮，但位置或点击条件不满足";
                            if(clickSkip(node,pkg,!popup))return;
                            if(skip&&queueCtripTap(node,pkg))return;
                        }
                    }
                    for(int i=0;i<node.getChildCount()&&pending.size()<350-count&&revision==eventRevision;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)pending.addLast(child);}
                }finally{node.recycle();}
            }
        }finally{while(!pending.isEmpty())pending.removeFirst().recycle();}
    }
    private boolean clickEntrySkip(AccessibilityNodeInfo node,String pkg){
        if(!entrySplashWindow(pkg)||node==null||!node.refresh()||!node.isVisibleToUser()||!node.isEnabled()
                ||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                ||!SkipRules.entrySkipLabel(node.getText())&&!SkipRules.entrySkipLabel(node.getContentDescription()))return false;
        outcome="识别到启动跳过文字，尚未通过点击条件";
        boolean clicked=clickSkip(node,pkg,true,true);
        if(SkipRules.BAIDU.equals(pkg))mapReport("baidu_last_candidate",clicked?"已发出跳过点击，尚未确认广告消失":outcome);
        return clicked;
    }
    private boolean findEntrySkip(AccessibilityNodeInfo root,String pkg){
        if(!entrySplashWindow(pkg))return false;
        java.util.List<AccessibilityNodeInfo> labels=root.findAccessibilityNodeInfosByText("跳过");
        if(labels!=null)try{for(int i=0;i<Math.min(32,labels.size());i++)if(clickEntrySkip(labels.get(i),pkg))return true;}
        finally{for(AccessibilityNodeInfo label:labels)if(label!=null)label.recycle();}
        // Some splash controls are absent from Android's text index. Traverse a
        // bounded tree as well, visiting later overlay children before page content.
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(AccessibilityNodeInfo.obtain(root));
        try{
            for(int count=0;!nodes.isEmpty()&&count<350&&!destroyed&&enabled(this)&&entrySplashWindow(pkg);count++){
                AccessibilityNodeInfo node=nodes.removeLast();
                try{
                    if(!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName()))continue;
                    if((SkipRules.entrySkipLabel(node.getText())||SkipRules.entrySkipLabel(node.getContentDescription()))&&clickEntrySkip(node,pkg))return true;
                    for(int i=0;i<node.getChildCount()&&nodes.size()<350-count;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)nodes.addLast(child);}
                }finally{node.recycle();}
            }
            return false;
        }finally{while(!nodes.isEmpty())nodes.removeLast().recycle();}
    }
    /** CMCC 11.9.2 draws its ad marker into the creative, but exposes a native skip control. */
    private boolean cmccNativeNode(AccessibilityNodeInfo node){
        String pkg=SkipRules.CMCC;
        if(node==null||!node.refresh()||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                ||!(pkg+":id/video_time_skip").equals(node.getViewIdResourceName())
                ||!SkipRules.cmccLabel(node.getText())||!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable())return false;
        AccessibilityNodeInfo parent=node.getParent();if(parent==null)return false;
        try{
            if(!(pkg+":id/ll_top_right").equals(parent.getViewIdResourceName())||!pkg.contentEquals(parent.getPackageName()==null?"":parent.getPackageName())||!parent.isVisibleToUser())return false;
            AccessibilityNodeInfo container=parent.getParent();if(container==null)return false;
            try{return (pkg+":id/video_layout").equals(container.getViewIdResourceName())&&pkg.contentEquals(container.getPackageName()==null?"":container.getPackageName())&&container.isVisibleToUser();}
            finally{container.recycle();}
        }finally{parent.recycle();}
    }
    private boolean findCmccSkip(AccessibilityNodeInfo root,String pkg){
        if(!SkipRules.CMCC.equals(pkg))return false;
        java.util.List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByViewId(pkg+":id/video_time_skip");
        if(matches==null)return false;
        try{
            for(int i=0;i<Math.min(matches.size(),8);i++){
                AccessibilityNodeInfo node=matches.get(i);if(!cmccNativeNode(node))continue;
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(!validBounds(bounds,getResources().getDisplayMetrics(),true))continue;
                AccessibilityNodeInfo active=getRootInActiveWindow();if(active==null)continue;
                try{
                    if(active.getWindowId()!=node.getWindowId()||!pkg.contentEquals(active.getPackageName()==null?"":active.getPackageName()))continue;
                    if(!cmccNativeNode(node))continue;
                    Rect fresh=new Rect();node.getBoundsInScreen(fresh);if(!bounds.equals(fresh))continue;
                    if(destroyed||!enabled(this)||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return false;
                    if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                        lastClick=SystemClock.elapsedRealtime();splashStatus(pkg,"已点击原生开屏跳过控件（尚未确认广告消失）");
                        android.content.SharedPreferences p=FilterVpnService.prefs(this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        FilterVpnService.notifyState(this);return true;
                    }
                    splashStatus(pkg,"原生开屏跳过控件拒绝点击");
                }finally{active.recycle();}
            }
            return false;
        }finally{for(AccessibilityNodeInfo node:matches)if(node!=null)node.recycle();}
    }
    private boolean findMapSkip(AccessibilityNodeInfo root,String pkg){
        if(!"com.baidu.BaiduMap".equals(pkg))return false;
        String id=pkg+":id/ms_skipView";
        java.util.List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByViewId(id);
        if(matches==null)return false;
        try{
            for(int i=0;i<Math.min(matches.size(),8);i++){
                AccessibilityNodeInfo node=matches.get(i);
                if(node==null||!node.refresh()||!id.equals(node.getViewIdResourceName())||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())||!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable())continue;
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(!validBounds(bounds,getResources().getDisplayMetrics(),true))continue;
                AccessibilityNodeInfo live=getRootInActiveWindow();if(live==null)continue;
                try{
                    if(live.getWindowId()!=node.getWindowId()||!pkg.contentEquals(live.getPackageName()==null?"":live.getPackageName()))continue;
                    if(!node.refresh()||!id.equals(node.getViewIdResourceName())||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())||!node.isVisibleToUser()||!node.isEnabled()||!node.isClickable())continue;
                    Rect fresh=new Rect();node.getBoundsInScreen(fresh);if(!bounds.equals(fresh))continue;
                    if(destroyed||!enabled(this)||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return false;
                    if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                        lastClick=SystemClock.elapsedRealtime();outcome="百度地图：已点击开屏跳过控件（尚未确认消失）";
                        mapReport("baidu_last_click",outcome);
                        android.util.Log.i("QuietStartTap",outcome);
                        android.content.SharedPreferences p=FilterVpnService.prefs(this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        FilterVpnService.notifyState(this);return true;
                    }
                }finally{live.recycle();}
            }
            return false;
        }finally{for(AccessibilityNodeInfo node:matches)if(node!=null)node.recycle();}
    }
    private boolean findSkipLabel(AccessibilityNodeInfo root,String pkg){
        java.util.List<AccessibilityNodeInfo> matches;
        try{matches=root.findAccessibilityNodeInfosByText(SkipRules.CMCC.equals(pkg)?"跳过":"跳过广告");}
        catch(RuntimeException unavailable){return false;}
        if(matches==null)return false;
        try{
            for(int i=0;i<Math.min(matches.size(),32)&&!destroyed&&enabled(this);i++){
                AccessibilityNodeInfo node=matches.get(i);
                if(node==null||!node.isVisibleToUser()||!node.isEnabled()||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName()))continue;
                if(SkipRules.CMCC.equals(pkg)&&SkipRules.cmccLabel(node.getText())){
                    if(queueCmccTap(node,pkg))return true;
                    continue;
                }
                if(!SkipRules.matches(pkg,node.getText())&&!SkipRules.matches(pkg,node.getContentDescription()))continue;
                if(clickSkip(node,pkg,true)||queueCtripTap(node,pkg))return true;
            }
            return false;
        }finally{for(AccessibilityNodeInfo node:matches)if(node!=null)node.recycle();}
    }
    /** Xiaomi Music's QQ-backed splash exposes its ad context on the full-page root,
     * not beside the skip label. Never target the neighbouring VIP entrance. */
    private boolean musicSplashContext(AccessibilityNodeInfo root){
        String pkg=SkipRules.MI_MUSIC;
        if(!pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return false;
        java.util.List<AccessibilityNodeInfo> contexts=root.findAccessibilityNodeInfosByViewId(pkg+":id/root");
        if(contexts==null)return false;
        try{
            for(int i=0;i<Math.min(contexts.size(),8);i++){
                AccessibilityNodeInfo node=contexts.get(i);
                if(node==null||!node.refresh()||!node.isVisibleToUser()||!node.isEnabled()||node.getWindowId()!=root.getWindowId()
                        ||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                        ||!(pkg+":id/root").equals(node.getViewIdResourceName())||!"开屏广告".contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))continue;
                Rect box=new Rect();node.getBoundsInScreen(box);android.util.DisplayMetrics m=getResources().getDisplayMetrics();
                if(box.width()>=m.widthPixels/2&&box.height()>=m.heightPixels/2)return true;
            }
            return false;
        }finally{for(AccessibilityNodeInfo node:contexts)if(node!=null)node.recycle();}
    }
    private boolean findCountdownLabel(AccessibilityNodeInfo root,String pkg){
        if(!SkipRules.eligible(pkg)||SkipRules.CMCC.equals(pkg))return false;
        java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText("跳过");
        if(nodes==null)return false;
        try{
            for(int i=0;i<Math.min(nodes.size(),32)&&!destroyed&&enabled(this);i++){
                AccessibilityNodeInfo node=nodes.get(i);
                if(node==null||!node.isClickable()||!node.refresh()||!node.isVisibleToUser()||!node.isEnabled()||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())||!SkipRules.countdown(node.getText()))continue;
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                android.util.DisplayMetrics m=getResources().getDisplayMetrics();
                if(!validBounds(bounds,m,true))continue;
                AccessibilityNodeInfo live=getRootInActiveWindow();if(live==null)continue;
                try{
                    if(live.getWindowId()!=node.getWindowId()||!pkg.contentEquals(live.getPackageName()==null?"":live.getPackageName()))continue;
                    boolean music=SkipRules.MI_MUSIC.equals(pkg)&&musicSplashContext(live);
                    if(!music&&!nearbyAdMarker(live,pkg,bounds,m))continue;
                    if(!node.refresh()||!node.isClickable()||!node.isVisibleToUser()||!node.isEnabled()||!SkipRules.countdown(node.getText()))continue;
                    Rect refreshed=new Rect();node.getBoundsInScreen(refreshed);if(!refreshed.equals(bounds))continue;
                    if(music&&!musicSplashContext(live))continue;
                    if(destroyed||!enabled(this)||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return false;
                    if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                        lastClick=SystemClock.elapsedRealtime();outcome=music?"小米音乐：已点击开屏跳过（尚未确认广告消失）":"已点击带广告标记的倒计时跳过（尚未确认消失）";
                        android.util.Log.i("QuietStartTap",pkg+": "+outcome);
                        android.content.SharedPreferences p=FilterVpnService.prefs(this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        FilterVpnService.notifyState(this);return true;
                    }
                }finally{live.recycle();}
            }
            return false;
        }finally{for(AccessibilityNodeInfo node:nodes)if(node!=null)node.recycle();}
    }
    private boolean nearbyAdMarker(AccessibilityNodeInfo root,String pkg,Rect button,android.util.DisplayMetrics m){
        java.util.List<AccessibilityNodeInfo> markers=root.findAccessibilityNodeInfosByText("广告");
        if(markers==null)return false;
        try{
            for(int i=0;i<Math.min(markers.size(),32);i++){
                AccessibilityNodeInfo marker=markers.get(i);
                if(marker==null||!marker.isVisibleToUser()||!pkg.contentEquals(marker.getPackageName()==null?"":marker.getPackageName())||!SkipRules.adMarker(marker.getText()))continue;
                Rect r=new Rect();marker.getBoundsInScreen(r);
                if(SkipRules.smallBounds(r.left,r.top,r.right,r.bottom,m.widthPixels,m.heightPixels)&&r.bottom<=m.heightPixels/4&&Math.abs(r.centerY()-button.centerY())<=Math.max(button.height(),r.height())&&r.right<=button.left&&button.left-r.right<=m.widthPixels/4)return true;
            }
            return false;
        }finally{for(AccessibilityNodeInfo marker:markers)if(marker!=null)marker.recycle();}
    }
    private void splashStatus(String pkg,String message){
        if(SkipRules.CMCC.equals(pkg)){
            outcome="中国移动："+message;android.util.Log.i("QuietStartTap",outcome);
            FilterVpnService.prefs(this).edit().putString("cmcc_tap_status",outcome).apply();
        }else tapStatus(message);
    }
    private boolean queueCtripTap(AccessibilityNodeInfo node,String pkg){
        if(!"ctrip.android.view".equals(pkg))return false;
        return queueSplashTap(node,pkg,false);
    }
    private boolean queueCmccTap(AccessibilityNodeInfo node,String pkg){
        if(!SkipRules.CMCC.equals(pkg))return false;
        return queueSplashTap(node,pkg,true);
    }
    private boolean cmccAdContext(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
        try{
            if(!SkipRules.CMCC.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return false;
            java.util.List<AccessibilityNodeInfo> labels=root.findAccessibilityNodeInfosByText("广告");
            if(labels==null)return false;
            try{for(AccessibilityNodeInfo label:labels)if(label!=null&&label.isVisibleToUser()&&SkipRules.CMCC.contentEquals(label.getPackageName()==null?"":label.getPackageName())&&(SkipRules.adMarker(label.getText())||SkipRules.adMarker(label.getContentDescription())))return true;}
            finally{for(AccessibilityNodeInfo label:labels)if(label!=null)label.recycle();}
            return false;
        }finally{root.recycle();}
    }
    private boolean splashBounds(AccessibilityNodeInfo node,String pkg,Rect bounds,android.util.DisplayMetrics m,boolean cmcc){
        return cmcc?SkipRules.CMCC.equals(pkg)&&SkipRules.cmccLabel(node.getText())&&SkipRules.safeBounds(bounds.left,bounds.top,bounds.right,bounds.bottom,m.widthPixels,m.heightPixels):SkipRules.ctripTap(pkg,node.getText(),node.getContentDescription(),bounds.left,bounds.top,bounds.right,bounds.bottom,m.widthPixels,m.heightPixels);
    }
    private boolean queueSplashTap(AccessibilityNodeInfo node,String pkg,boolean cmcc){
        if(tapPending)return false;
        if(cmcc&&!cmccAdContext()){outcome="中国移动跳过候选缺少可读取的广告标记";return false;}
        if(!cmcc&&!FilterVpnService.prefs(this).getBoolean("ctrip_tap",true))return false;
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();
        if(!splashBounds(node,pkg,bounds,m,cmcc)){splashStatus(pkg,"开屏跳过位置不安全："+bounds+" / "+m.widthPixels+"x"+m.heightPixels);return false;}
        AccessibilityNodeInfo fresh=AccessibilityNodeInfo.obtain(node);
        tapPending=true;
        splashStatus(pkg,"开屏跳过已识别："+bounds);
        // Run dispatch on the main thread, then re-check the live window and label before touching it.
        if(!handler.post(()->{
            try{
                if(destroyed||!enabled(this)||(!cmcc&&!FilterVpnService.prefs(this).getBoolean("ctrip_tap",true))||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg)){splashStatus(pkg,"开屏点击取消：功能已关闭或应用已排除");return;}
                AccessibilityNodeInfo active=getRootInActiveWindow();if(active==null){splashStatus(pkg,"开屏点击取消：无活动窗口");return;}
                try{if(active.getWindowId()!=fresh.getWindowId()||!pkg.contentEquals(active.getPackageName()==null?"":active.getPackageName())){splashStatus(pkg,"开屏点击取消：活动窗口已变化");return;}}finally{active.recycle();}
                if(cmcc&&!cmccAdContext()){splashStatus(pkg,"中国移动点击取消：广告标记已消失");return;}
                if(!fresh.refresh()||!fresh.isVisibleToUser()||!fresh.isEnabled()||!pkg.contentEquals(fresh.getPackageName()==null?"":fresh.getPackageName())){splashStatus(pkg,"开屏点击取消：按钮已失效");return;}
                Rect current=new Rect();fresh.getBoundsInScreen(current);
                if(!splashBounds(fresh,pkg,current,m,cmcc)){splashStatus(pkg,"开屏点击取消：刷新后标签或位置不安全");return;}
                android.graphics.Path path=new android.graphics.Path();path.moveTo(cmcc?current.left+current.width()*0.75f:current.exactCenterX(),current.exactCenterY());
                android.accessibilityservice.GestureDescription gesture=new android.accessibilityservice.GestureDescription.Builder().addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,android.view.ViewConfiguration.getTapTimeout())).build();
                boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription g){
                        if(destroyed)return;
                        android.content.SharedPreferences p=FilterVpnService.prefs(AdSkipService.this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        splashStatus(pkg,"开屏跳过按钮触摸已完成（尚未确认广告消失）");FilterVpnService.notifyState(AdSkipService.this);
                    }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription g){splashStatus(pkg,"开屏兼容点击被系统取消");lastClick=0;}
                },handler);
                if(sent)lastClick=SystemClock.elapsedRealtime();
                splashStatus(pkg,sent?"已提交开屏兼容点击："+current:"系统未允许兼容点击，请重开无障碍服务");
            }catch(RuntimeException error){splashStatus(pkg,"开屏兼容点击中断："+error.getClass().getSimpleName());}
            finally{tapPending=false;fresh.recycle();}
        })){tapPending=false;fresh.recycle();return false;}
        return true;
    }
    private boolean musicPopupWindow(){
        long age=SystemClock.elapsedRealtime()-musicEntered;
        return SkipRules.MI_MUSIC.equals(foregroundPackage)&&musicEntered>=0&&age>=0&&age<15000;
    }
    private boolean musicPopupContext(AccessibilityNodeInfo root,boolean renewal){
        String pkg=SkipRules.MI_MUSIC;
        java.util.List<AccessibilityNodeInfo> matches=renewal?root.findAccessibilityNodeInfosByText("你已失去豪华绿钻全部权益")
                :root.findAccessibilityNodeInfosByViewId(pkg+":id/iv_background_dialog_content");
        if(matches==null)return false;
        try{for(int i=0;i<Math.min(matches.size(),8);i++){
            AccessibilityNodeInfo n=matches.get(i);
            if(n==null||!n.refresh()||!n.isVisibleToUser()||!n.isEnabled()||n.getWindowId()!=root.getWindowId()
                    ||!pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))continue;
            String desc=n.getContentDescription()==null?"":n.getContentDescription().toString();
            if(renewal){if("你已失去豪华绿钻全部权益".equals(desc))return true;}
            else if((pkg+":id/iv_background_dialog_content").equals(n.getViewIdResourceName())&&"弹窗".equals(desc)){
                AccessibilityNodeInfo parent=n.getParent();
                try{if(parent!=null&&parent.refresh()&&parent.isVisibleToUser()&&parent.getWindowId()==root.getWindowId()
                        &&pkg.contentEquals(parent.getPackageName()==null?"":parent.getPackageName())
                        &&(pkg+":id/fl_dialog_container").equals(parent.getViewIdResourceName()))return true;}
                finally{if(parent!=null)parent.recycle();}
            }
        }return false;}finally{for(AccessibilityNodeInfo n:matches)if(n!=null)n.recycle();}
    }
    private boolean musicPopupButton(AccessibilityNodeInfo n,boolean renewal){
        String pkg=SkipRules.MI_MUSIC;
        if(n==null||!n.refresh()||!n.isVisibleToUser()||!n.isEnabled()||!n.isClickable()
                ||!pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))return false;
        String desc=n.getContentDescription()==null?"":n.getContentDescription().toString();
        return renewal?"关闭弹框按钮".equals(desc)
                :(pkg+":id/iv_close_dialog_button").equals(n.getViewIdResourceName())&&"关闭".equals(desc);
    }
    /** Only the two observed launch promotions; never a generic close on music pages. */
    private boolean findMusicPopup(AccessibilityNodeInfo root,String pkg){
        if(!SkipRules.MI_MUSIC.equals(pkg)||!musicPopupWindow()||!popupsEnabled(this))return false;
        for(boolean renewal:new boolean[]{true,false}){
            java.util.List<AccessibilityNodeInfo> buttons=renewal?root.findAccessibilityNodeInfosByText("关闭弹框按钮")
                    :root.findAccessibilityNodeInfosByViewId(pkg+":id/iv_close_dialog_button");
            if(buttons==null)continue;
            try{for(int i=0;i<Math.min(buttons.size(),8);i++){
                AccessibilityNodeInfo n=buttons.get(i);if(!musicPopupButton(n,renewal))continue;
                Rect box=new Rect();n.getBoundsInScreen(box);if(!validBounds(box,getResources().getDisplayMetrics(),false))continue;
                AccessibilityNodeInfo live=getRootInActiveWindow();if(live==null)continue;
                try{
                    if(live.getWindowId()!=n.getWindowId()||!pkg.contentEquals(live.getPackageName()==null?"":live.getPackageName())
                            ||!musicPopupContext(live,renewal)||!musicPopupButton(n,renewal))continue;
                    Rect fresh=new Rect();n.getBoundsInScreen(fresh);if(!box.equals(fresh))continue;
                    if(!musicPopupWindow()||destroyed||!enabled(this)||!popupsEnabled(this)
                            ||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return false;
                    if(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                        lastClick=SystemClock.elapsedRealtime();outcome="小米音乐：已点击"+(renewal?"续费提示":"活动弹窗")+"关闭（尚未确认消失）";
                        android.util.Log.i("QuietStartTap",pkg+": "+outcome);
                        android.content.SharedPreferences p=FilterVpnService.prefs(this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        FilterVpnService.notifyState(this);return true;
                    }
                }finally{live.recycle();}
            }}finally{for(AccessibilityNodeInfo n:buttons)if(n!=null)n.recycle();}
        }
        return false;
    }
    private boolean localAdClose(AccessibilityNodeInfo node,String pkg){
        if(!SkipRules.closeLabel(node.getText())&&!SkipRules.closeLabel(node.getContentDescription()))return false;
        AccessibilityNodeInfo parent=node.getParent();if(parent==null)return false;
        try{
            if(!pkg.contentEquals(parent.getPackageName()==null?"":parent.getPackageName())||parent.getChildCount()>12)return false;
            Rect box=new Rect();parent.getBoundsInScreen(box);
            android.util.DisplayMetrics m=getResources().getDisplayMetrics();
            if(box.width()<=0||box.height()<=0||box.left<0||box.top<0||box.right>m.widthPixels||box.bottom>m.heightPixels||(long)box.width()*box.height()>(long)m.widthPixels*m.heightPixels*3/4)return false;
            for(int i=0;i<parent.getChildCount();i++){
                AccessibilityNodeInfo sibling=parent.getChild(i);if(sibling==null)continue;
                try{if(sibling.isVisibleToUser()&&pkg.contentEquals(sibling.getPackageName()==null?"":sibling.getPackageName())&&(SkipRules.adMarker(sibling.getText())||SkipRules.adMarker(sibling.getContentDescription())))return true;}
                finally{sibling.recycle();}
            }
            return false;
        }finally{parent.recycle();}
    }
    private boolean clickSkip(AccessibilityNodeInfo node,String pkg,boolean opening){
        return clickSkip(node,pkg,opening,false);
    }
    private boolean clickSkip(AccessibilityNodeInfo node,String pkg,boolean opening,boolean entrySplash){
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        Rect label=new Rect();node.getBoundsInScreen(label);
        if(!validBounds(label,metrics,opening))return false;
        AccessibilityNodeInfo target=AccessibilityNodeInfo.obtain(node);
        try{
            for(int depth=0;target!=null&&depth<3;depth++){
                if(!pkg.contentEquals(target.getPackageName()==null?"":target.getPackageName()))return false;
                Rect bounds=new Rect();target.getBoundsInScreen(bounds);
                if(!validBounds(bounds,metrics,opening))return false;
                // A popup's wrapper must not also contain its advert or another action.
                if(!opening&&depth>0&&(target.getChildCount()!=1||!bounds.equals(label)))return false;
                if(target.isClickable()&&target.isEnabled()&&target.isVisibleToUser()){
                    if(destroyed||!enabled(this)||(!opening&&!popupsEnabled(this))||FilterVpnService.prefs(this).getStringSet("skip_excluded",java.util.Collections.emptySet()).contains(pkg))return false;
                    AccessibilityNodeInfo active=getRootInActiveWindow();if(active==null)return false;
                    try{if(active.getWindowId()!=node.getWindowId()||!pkg.contentEquals(active.getPackageName()==null?"":active.getPackageName()))return false;}finally{active.recycle();}
                    if(!target.refresh()||!target.isVisibleToUser()||!target.isEnabled())return false;
                    target.getBoundsInScreen(bounds);if(!validBounds(bounds,metrics,opening))return false;
                    if(entrySplash){
                        if(!entrySplashWindow(pkg)||!node.refresh()||!node.isVisibleToUser()||!node.isEnabled()
                                ||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName())
                                ||!SkipRules.entrySkipLabel(node.getText())&&!SkipRules.entrySkipLabel(node.getContentDescription()))return false;
                        Rect freshLabel=new Rect();node.getBoundsInScreen(freshLabel);if(!label.equals(freshLabel))return false;
                    }
                    boolean clicked=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if(clicked){
                        outcome="已发出关闭点击（尚未确认广告消失）";
                        android.util.Log.i("QuietStartTap",pkg+": "+outcome);
                        if(SkipRules.BAIDU.equals(pkg))mapReport("baidu_last_click",outcome);
                        lastClick=SystemClock.elapsedRealtime();
                        android.content.SharedPreferences p=FilterVpnService.prefs(this);
                        p.edit().putLong("skip_clicks",p.getLong("skip_clicks",0)+1).putString("skip_last",pkg).apply();
                        FilterVpnService.notifyState(this);
                    }
                    else outcome="按钮拒绝无障碍点击";
                    return clicked;
                }
                AccessibilityNodeInfo parent=target.getParent();target.recycle();target=parent;
            }
            return false;
        }finally{if(target!=null)target.recycle();}
    }
    private boolean validBounds(Rect r,android.util.DisplayMetrics m,boolean opening){return opening?SkipRules.safeBounds(r.left,r.top,r.right,r.bottom,m.widthPixels,m.heightPixels):SkipRules.smallBounds(r.left,r.top,r.right,r.bottom,m.widthPixels,m.heightPixels);}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(android.content.Intent intent){
        connected=false;outcome="系统已断开服务连接";
        FilterVpnService.notifyState(this);
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){destroyed=true;connected=false;handler.removeCallbacksAndMessages(null);pendingPackage=null;worker.shutdownNow();FilterVpnService.notifyState(this);super.onDestroy();}
}
