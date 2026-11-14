package cn.quietstart;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PAPER=0xFFF4F5F0, INK=0xFF263D32, MUTED=0xFF637060, GREEN=0xFF397D62, MINT=0xFFDAF3CD;
    private LinearLayout body,content,rowGroup;
    private ProtectionArt protectionArt;
    private TextView status,counts,network,skipStatus,batteryStatus;
    private Switch allApps;
    private Button mainToggle,accessHelp,batteryRequest;
    private String page="home";
    private String homeShortcut;
    private boolean vpnRequestPending;
    private long vpnRequestEpoch;
    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){refresh();}
    };
    private String lastProbe="尚未运行链路自检";
    private final List<Switch> targets=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){ public void run(){ refresh(); handler.postDelayed(this,1500); }};
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        vpnRequestPending=b!=null&&b.getBoolean("vpn_request_pending",false);
        vpnRequestEpoch=b==null?0:b.getLong("vpn_request_epoch",0);
        homeShortcut=b==null?null:b.getString("home_shortcut");
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(22),dp(14),dp(22),dp(32));
        body.setBackgroundColor(PAPER); scroll.addView(body); setContentView(scroll);
        showPage(b==null?"home":b.getString("page","home"));
    }
    private void showPage(String destination) {
        // Old saved pages remain usable after an upgrade.
        page="skip".equals(destination)?"home":destination;
        if("home".equals(page)||"settings".equals(page))homeShortcut=null;
        body.removeAllViews();content=body;rowGroup=null;protectionArt=null;targets.clear();allApps=null;status=null;counts=null;
        mainToggle=null;skipStatus=null;batteryStatus=null;accessHelp=null;batteryRequest=null;
        body.setPadding(dp(22),dp(14),dp(22),dp("home".equals(page)?18:32));
        ((ScrollView)body.getParent()).scrollTo(0,0);
        network=new TextView(this);
        if("home".equals(page)) {
            LinearLayout hero=panel(28,24);
            GradientDrawable heroBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF284E3E,0xFF14392E});
            heroBackground.setCornerRadius(dp(28));hero.setBackground(heroBackground);content=hero;
            filterHeader();
            protectionArt=new ProtectionArt(this);content.addView(protectionArt,new LinearLayout.LayoutParams(-1,dp(112)));
            status=label("",25,true);status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);
            counts=label("",12,false);counts.setTextColor(0xFFBDD1BE);counts.setGravity(Gravity.CENTER);
            gap(18);
            mainToggle=button("开启过滤",()->{
                if(FilterVpnService.stopping)return;
                if(!FilterVpnService.needsConsent&&(FilterVpnService.running||FilterVpnService.starting||FilterVpnService.waitingForVpn)){
                    vpnRequestPending=false;FilterVpnService.pause(this);refresh();
                }else startFilter();
            },true);
            content=body;gap(18);
            LinearLayout automatic=panel(22,18);content=automatic;
            Switch skip=preference("自动跳过广告","skip_ads",false,()->{
                refresh();
                if(AdSkipService.enabled(this)&&BackgroundSupport.needsAccessHelp(AccessibilityStatus.read(this)))showAccessHelp();
            });
            skip.setCompoundDrawables(icon("spark",GREEN,22),null,null,null);skip.setCompoundDrawablePadding(dp(12));
            skipStatus=label("",12,false);skipStatus.setPadding(dp(34),0,0,dp(2));
            accessHelp=button("去设置",this::showAccessHelp,false);
            content=body;gap(14);homeShortcuts();
            refresh();return;
        }
        backLink();
        switch(page){
            case "settings":
                label("设置",28,true);gap(16);
                preference("关闭广告弹窗","popup_ads",true,null);
                row("过滤范围",()->showPage("scope"));
                row("后台与权限",()->showPage("background"));
                row("高级",()->showPage("advanced"));
                row("关于静启",()->showPage("about"));break;
            case "advanced":
                label("高级",28,true);gap(16);
                row("过滤规则",()->showPage("rules"));
                row("不自动跳过的应用",this::editSkipExclusions);
                row("VPN 兼容",()->showPage("compatibility"));
                row("问题诊断",()->showPage("diagnostics"));break;
            case "compatibility":
            case "clash":
                label("VPN 兼容",28,true);gap(12);
                label("Clash 等 VPN 运行时，静启暂停过滤；退出后自动恢复。自动跳过广告仍可使用。",15,false);
                label("若切换异常，请检查系统中静启的“始终开启 VPN”和“阻止不使用 VPN 的连接”是否关闭，以及 Clash 是否排除了静启。",14,false);
                row("系统 VPN 设置",()->open(new Intent(Settings.ACTION_VPN_SETTINGS)));break;
            case "scope":
                label("过滤范围",28,true);
                label("修改前请先暂停过滤。",13,false);gap(12);
                allApps=preference("全部应用","all_apps",true,this::refresh);
                for(int i=0;i<FilterVpnService.PACKAGES.length;i++){
                    String pkg=FilterVpnService.PACKAGES[i];boolean available=installed(pkg);
                    Switch sw=preference(FilterVpnService.NAMES[i]+(available?"":"（未安装）"),pkg,true,null);
                    sw.setTag(available);targets.add(sw);
                }break;
            case "rules":
                label("过滤规则",28,true);gap(12);
                preference("完整规则库","full_rules",true,this::reloadRules);
                label("已内置规则，通常无需调整。",13,false);
                row("更新规则",this::updateSubscription);
                row("白名单",()->editRules("allow","白名单"));
                row("额外拦截",()->editRules("block","额外拦截"));
                row("导入规则",this::importSettings);break;
            case "background":
                label("后台与权限",28,true);gap(12);
                skipStatus=label("",14,false);
                row("无障碍设置",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
                gap(16);label("后台运行",18,true);
                label("正常使用无需调整。若清理或锁屏后经常停止，可尝试放宽电池限制；可能增加耗电。",14,false);
                batteryStatus=label("",13,false);
                batteryRequest=button("放宽电池限制",this::requestBatteryExemption,false);
                row("电池设置",()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
                row("应用系统设置",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));break;
            case "diagnostics":
                label("问题诊断",28,true);gap(12);
                label("记录仅保存在本机，用于排查。",13,false);
                row("检查过滤连接",this::probe);
                row("记录广告问题 · 60 秒",this::beginDiagnostic);
                row("结束记录并查看报告",this::showDiagnostic);
                row("请求记录",this::showEvents);
                preference("临时记录域名","diagnostics",false,null);
                row("自动跳过记录",this::showSkipDiagnostic);break;
            case "about":
                String version;try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){version="";}
                label("静启",32,true);label(version,14,false);gap(16);
                label("减少广告打扰，轻装使用。",16,false);
                label("部分广告可能无法过滤或跳过。",13,false);
                row("隐私与使用说明",()->message("隐私与使用说明","过滤使用系统 VPN 接口处理域名查询，普通网络连接不经过代理。未拦截的域名交给阿里公共 DNS，失败后尝试腾讯公共 DNS。其他 VPN 运行时暂停过滤。\n\n自动跳过需无障碍授权，在本机读取当前界面控件并点击识别到的广告关闭按钮，不截屏、不保存或上传页面内容。可随时在首页关闭。\n\n本应用不上传日志或统计信息；诊断记录仅在本机。缓存广告、加密 DNS 和部分广告样式可能无法处理。系统清理或强行停止可能中断运行。"));
                row("开源许可",()->{try{message("anti-AD · MIT License","规则来源：https://github.com/privacy-protection-tools/anti-AD\n\n"+new String(getAssets().open("ANTI-AD-LICENSE.txt").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){message("读取失败",e.getMessage());}});break;
            default:showPage("home");return;
        }
        refresh();
    }
    private String parentPage(){
        if(page.equals(homeShortcut))return "home";
        switch(page){
            case "settings":return "home";
            case "rules":case "diagnostics":case "compatibility":return "advanced";
            case "clash":return "compatibility";
            default:return "settings";
        }
    }
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private RippleDrawable surface(int color,int radius){
        return new RippleDrawable(ColorStateList.valueOf(0x19397D62),shape(color,radius),shape(Color.WHITE,radius));
    }
    private QuietIcon icon(String kind,int color,int size){
        QuietIcon drawable=new QuietIcon(kind,color);drawable.setBounds(0,0,dp(size),dp(size));return drawable;
    }
    private LinearLayout panel(int radius,int padding){
        rowGroup=null;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(padding),dp(padding),dp(padding),dp(padding));panel.setBackground(shape(Color.WHITE,radius));
        content.addView(panel,new LinearLayout.LayoutParams(-1,-2));return panel;
    }
    private void homeShortcuts(){
        LinearLayout shortcuts=new LinearLayout(this);
        String[] titles={"过滤范围","后台与权限"};String[] destinations={"scope","background"};
        for(int i=0;i<titles.length;i++){
            if(i>0)shortcuts.addView(new View(this),new LinearLayout.LayoutParams(dp(14),1));
            Button shortcut=new Button(this);shortcut.setText(titles[i]);shortcut.setAllCaps(false);shortcut.setTextSize(14);
            shortcut.setTextColor(INK);shortcut.setGravity(Gravity.CENTER);shortcut.setBackground(surface(Color.WHITE,22));
            shortcut.setStateListAnimator(null);shortcut.setElevation(0);shortcut.setPadding(dp(12),dp(18),dp(12),dp(18));
            shortcut.setMinHeight(dp(104));shortcut.setMinimumHeight(dp(104));
            shortcut.setCompoundDrawables(null,icon(rowIcon(titles[i]),GREEN,24),null,null);shortcut.setCompoundDrawablePadding(dp(10));
            String destination=destinations[i];shortcut.setOnClickListener(v->{homeShortcut=destination;showPage(destination);});
            shortcuts.addView(shortcut,new LinearLayout.LayoutParams(0,-2,1));
        }
        content.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
    }
    private void filterHeader(){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new View(this),new LinearLayout.LayoutParams(dp(48),1));
        TextView title=new TextView(this);title.setText("广告过滤");title.setTextSize(12);title.setTextColor(0xFFBFD3BE);title.setGravity(Gravity.CENTER);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton settings=new ImageButton(this);settings.setImageDrawable(icon("settings",MINT,22));settings.setContentDescription("设置");
        settings.setBackground(surface(Color.TRANSPARENT,16));settings.setPadding(dp(13),dp(13),dp(13),dp(13));
        settings.setOnClickListener(v->showPage("settings"));header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
    }
    private void backLink(){
        ImageButton back=new ImageButton(this);back.setImageDrawable(icon("back",INK,22));back.setContentDescription("返回");
        back.setBackground(surface(Color.WHITE,16));back.setPadding(dp(12),dp(12),dp(12),dp(12));
        content.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->showPage(parentPage()));gap(20);
    }
    private String rowIcon(String text){
        switch(text){
            case "过滤范围":case "不自动跳过的应用":return "apps";
            case "后台与权限":case "电池设置":return "leaf";
            case "高级":case "VPN 兼容":case "应用系统设置":case "系统 VPN 设置":return "sliders";
            case "关于静启":case "开源许可":return "info";
            case "隐私与使用说明":return "lock";
            case "更新规则":case "导入规则":return "download";
            case "问题诊断":case "检查过滤连接":return "pulse";
            case "无障碍设置":return "spark";
            case "过滤规则":case "白名单":return "shield";
            default:return "list";
        }
    }
    private void row(String text,Runnable action){
        if(rowGroup==null){
            rowGroup=new LinearLayout(this);rowGroup.setOrientation(LinearLayout.VERTICAL);rowGroup.setBackground(shape(Color.WHITE,22));
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=dp(10);content.addView(rowGroup,layout);
        }else{
            View divider=new View(this);divider.setBackgroundColor(0xFFF0F2EC);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(1));layout.leftMargin=dp(54);layout.rightMargin=dp(18);rowGroup.addView(divider,layout);
        }
        Button row=new Button(this);row.setText(text);row.setAllCaps(false);row.setTextSize(15);row.setTextColor(INK);
        row.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18),dp(14),dp(16),dp(14));row.setMinHeight(dp(64));row.setMinimumHeight(dp(64));
        row.setBackground(surface(Color.TRANSPARENT,22));row.setStateListAnimator(null);row.setElevation(0);
        row.setCompoundDrawables(icon(rowIcon(text),GREEN,22),null,icon("next",0xFF9EA99C,16),null);row.setCompoundDrawablePadding(dp(14));
        rowGroup.addView(row,new LinearLayout.LayoutParams(-1,-2));row.setOnClickListener(v->action.run());
    }
    private Switch preference(String title,String key,boolean fallback,Runnable changed){
        rowGroup=null;Switch sw=new Switch(this);sw.setText(title);sw.setTextSize(15);sw.setTextColor(INK);
        sw.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));sw.setSwitchPadding(dp(12));
        StateListDrawable track=new StateListDrawable();
        int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{}};
        int[] colors={0xFFE6E9E1,GREEN,0xFFCED5CA};
        for(int i=0;i<states.length;i++){GradientDrawable pill=shape(colors[i],16);pill.setSize(dp(48),dp(28));track.addState(states[i],pill);}
        sw.setTrackTintList(null);sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));sw.setTrackDrawable(track);
        sw.setSwitchMinWidth(dp(48));sw.setSplitTrack(false);
        boolean home="home".equals(page);int pad=home?0:18;
        sw.setPadding(dp(pad),dp(home?4:16),dp(pad),dp(home?4:16));sw.setMinHeight(dp(home?48:64));
        if(!home)sw.setBackground(surface(Color.WHITE,22));
        sw.setChecked(FilterVpnService.prefs(this).getBoolean(key,fallback));
        sw.setOnCheckedChangeListener((v,on)->{FilterVpnService.prefs(this).edit().putBoolean(key,on).apply();if(changed!=null)changed.run();});
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);if(!home)layout.bottomMargin=dp(6);
        content.addView(sw,layout);return sw;
    }
    private void showSkipDiagnostic(){
        android.content.SharedPreferences p=FilterVpnService.prefs(this);
        StringBuilder report=new StringBuilder(AccessibilityStatus.label(AccessibilityStatus.read(this)))
            .append("\n事件：").append(AdSkipService.received).append("，扫描：").append(AdSkipService.scans)
            .append("\n最近收到界面事件：").append(diagnosticTime(AdSkipService.lastEventAt))
            .append("\n最近扫描完成：").append(diagnosticTime(AdSkipService.lastScanFinishedAt))
            .append("\n过滤链路检查：").append(diagnosticTime(FilterVpnService.lastHealthCheck)).append(" · ").append(FilterVpnService.healthResult)
            .append("\n").append(AdSkipService.outcome).append("\n").append(AdSkipService.lastPackage)
            .append("\n累计发出点击：").append(p.getLong("skip_clicks",0)).append("（不等于成功关闭次数）")
            .append("\n携程：").append(p.getString("ctrip_tap_status","暂无记录"));
        for(String key:new String[]{"baidu_last_scan","baidu_last_candidate","baidu_last_click"}){
            long time=p.getLong(key+"_time",0);
            report.append("\n").append(key).append("：").append(time==0?"暂无记录":new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time))+" "+p.getString(key,""));
        }
        message("自动跳过记录",report.toString());
    }
    private String diagnosticTime(long time){return time==0?"本次运行暂无记录":new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time));}
    private void editSkipExclusions(){
        EditText input=new EditText(this);input.setMinLines(4);input.setHint("每行一个应用包名，例如 com.example.app");
        input.setText(String.join("\n",FilterVpnService.prefs(this).getStringSet("skip_excluded",Collections.emptySet())));
        new AlertDialog.Builder(this).setTitle("不自动点击这些应用").setMessage("排除名单只影响自动关闭，不影响域名过滤。保存后立即生效。").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
            Set<String> excluded=new HashSet<>();for(String line:input.getText().toString().split("\\s+"))if(!line.isBlank())excluded.add(line.trim());
            FilterVpnService.prefs(this).edit().putStringSet("skip_excluded",excluded).apply();
        }).show();
    }
    private void gap(int h) {rowGroup=null;View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView label(String text,int size,boolean bold) {
        rowGroup=null;TextView t=new TextView(this);t.setText(text);t.setTextSize(Math.min(size,26));t.setTextColor(bold?INK:MUTED);
        t.setLineSpacing(dp(3),1);t.setPadding(0,dp(4),0,dp(5));
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        content.addView(t);return t;
    }
    private Button button(String text,Runnable action,boolean primary) {
        rowGroup=null;Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));
        b.setPadding(dp(16),dp(12),dp(16),dp(12));
        b.setBackground(surface(primary?MINT:0xFFEAF0E5,16));b.setTextColor(primary?0xFF204632:GREEN);
        b.setStateListAnimator(null);b.setElevation(0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(b,p);b.setOnClickListener(v->action.run());
        return b;
    }
    private boolean installed(String pkg) { try {getPackageManager().getPackageInfo(pkg,0); return true;}catch(PackageManager.NameNotFoundException e){return false;} }
    private void startFilter() {
        if(FilterVpnService.starting){message("正在载入规则","请稍候，完整规则库只在启动或更新时加载。");return;}
        if(FilterVpnService.running) { message("已经开启","过滤服务正在运行，可以收起页面。"); return; }
        boolean any=FilterVpnService.prefs(this).getBoolean("all_apps",true);
        for(String pkg:FilterVpnService.PACKAGES) if(installed(pkg)&&FilterVpnService.prefs(this).getBoolean(pkg,true)) any=true;
        if(!any) {message("没有目标应用","请安装并勾选小红书、网易云音乐或百度地图中的至少一个。");return;}
        vpnRequestPending=true;
        vpnRequestEpoch=FilterVpnService.prefs(this).getLong("pause_epoch",0);
        if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},11);
        else requestVpn();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {super.onRequestPermissionsResult(request,permissions,results); if(request==11) requestVpn();}
    private void requestVpn() {
        if(!validVpnRequest())return;
        try {
            // prepare() itself can take ownership from a previously consented VPN.
            // Observe first; when another VPN is present start only the waiting session.
            if(VpnMonitor.otherVpnPresent(this)){launchService();return;}
            Intent intent=VpnService.prepare(this);
            if(intent!=null)startActivityForResult(intent,10);else launchService();
        }catch(RuntimeException error){vpnRequestPending=false;message("暂时无法开启",error.getClass().getSimpleName());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==12&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            runImport(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("无法读取文件");
                return ImportedRules.parse(Subscription.readBounded(in,ImportedRules.LIMIT));
            }},"");
        }
        if(request==10&&validVpnRequest()){
            if(result==RESULT_OK)launchService();
            else {vpnRequestPending=false;FilterVpnService.pause(this);FilterVpnService.status="未授权 VPN，过滤未开启";refresh();}
        }
    }
    private void launchService() {
        if(!validVpnRequest())return;
        vpnRequestPending=false;
        try {
            FilterVpnService.start(this);refresh();
        }catch(Exception e){FilterVpnService.starting=false;FilterVpnService.status="启动失败";refresh();message("启动失败",e.getMessage());}
    }
    private boolean validVpnRequest(){
        if(vpnRequestEpoch!=FilterVpnService.prefs(this).getLong("pause_epoch",0))vpnRequestPending=false;
        return vpnRequestPending;
    }
    private void requestBatteryExemption(){
        if(BackgroundSupport.batteryState(this)==BackgroundSupport.BatteryState.EXEMPT){refresh();return;}
        try{startActivity(BackgroundSupport.batteryRequest(this));}
        catch(ActivityNotFoundException|SecurityException unavailable){
            new AlertDialog.Builder(this).setTitle("系统未提供确认弹窗")
                    .setMessage("可以保持当前设置继续使用。若确实反复掉线，可在系统电池优化页面手动调整静启。")
                    .setNegativeButton("保持当前设置",null)
                    .setPositiveButton("查看系统设置",(d,w)->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))).show();
        }
    }
    private void showAccessHelp(){
        AccessibilityStatus.State state=AccessibilityStatus.read(this);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setNegativeButton("暂不处理",null);
        switch(state){
            case NOT_ENABLED:
                dialog.setTitle("允许自动跳过广告").setMessage("请在系统无障碍设置中开启“静启 · 自动关闭广告”，然后返回。")
                        .setPositiveButton("打开系统设置",(d,w)->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));break;
            case DISCONNECTED:
                dialog.setTitle("授权还在，正在等待连接").setMessage("权限仍保留，请稍候。若经常断开，可查看后台设置。")
                        .setPositiveButton("稍后再检查",(d,w)->refresh())
                        .setNeutralButton("后台选项",(d,w)->showPage("background"));break;
            case CONNECTED:
                dialog.setTitle("自动跳过已连接").setMessage("当前连接正常，无需额外设置。")
                        .setPositiveButton("知道了",null).setNeutralButton("后台选项",(d,w)->showPage("background"));break;
            case UNRESPONSIVE:
                dialog.setTitle("自动跳过暂未响应").setMessage("权限仍保留，但扫描没有及时完成。可稍后检查；持续异常时可在系统无障碍设置中重新连接静启。")
                        .setPositiveButton("查看系统设置",(d,w)->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));break;
            case OFF:
                dialog.setTitle("自动跳过已暂停").setMessage("在首页打开“自动跳过广告”即可。")
                        .setPositiveButton("查看开关",(d,w)->showPage("skip"));break;
            default:
                dialog.setTitle("暂时无法确认授权状态").setMessage("请到系统设置查看静启的无障碍权限。")
                        .setPositiveButton("查看系统设置",(d,w)->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
        dialog.show();
    }
    private void refresh() {
        if(body==null)return;
        if(batteryStatus!=null){
            BackgroundSupport.BatteryState battery=BackgroundSupport.batteryState(this);
            batteryStatus.setText(BackgroundSupport.batteryLabel(battery));
            batteryRequest.setEnabled(battery!=BackgroundSupport.BatteryState.EXEMPT);
            batteryRequest.setText(battery==BackgroundSupport.BatteryState.EXEMPT?"已放宽电池限制":"放宽电池限制");
        }
        if(skipStatus!=null){
            AccessibilityStatus.State access=AccessibilityStatus.read(this);
            String hint;
            switch(access){
                case CONNECTED:hint="已开启";break;
                case OFF:hint="已关闭";break;
                case NOT_ENABLED:hint="需要允许无障碍权限";break;
                case DISCONNECTED:hint="正在等待系统连接";break;
                case UNRESPONSIVE:hint="扫描暂未响应";break;
                default:hint="请检查无障碍权限";
            }
            skipStatus.setText(("background".equals(page)?"自动跳过：":"")+hint);
            skipStatus.setTextColor(access==AccessibilityStatus.State.CONNECTED?GREEN:MUTED);
            if(accessHelp!=null){
                accessHelp.setVisibility(BackgroundSupport.needsAccessHelp(access)?View.VISIBLE:View.GONE);
                accessHelp.setText(access==AccessibilityStatus.State.NOT_ENABLED?"去授权":"查看连接");
            }
        }
        if(protectionArt!=null)protectionArt.setActive(FilterVpnService.running);
        if(status!=null)status.setText(FilterVpnService.stopping?"正在暂停…":FilterVpnService.needsConsent?"等待授权":FilterVpnService.starting?"正在开启…":FilterVpnService.waitingForVpn?"等待恢复过滤":FilterVpnService.healthChecking?"正在检查过滤":FilterVpnService.healthProblem?"过滤需要检查":FilterVpnService.running?"过滤已开启":"过滤未开启");
        if(mainToggle!=null){
            mainToggle.setText(FilterVpnService.needsConsent?"继续开启":FilterVpnService.stopping?"正在暂停…":FilterVpnService.starting?"取消开启":FilterVpnService.waitingForVpn?"暂停过滤":FilterVpnService.running?"暂停过滤":"开启过滤");
            mainToggle.setEnabled(!FilterVpnService.stopping);
        }
        if(counts!=null)counts.setText(FilterVpnService.waitingForVpn?"当前暂停过滤，其他 VPN 退出后自动恢复":getString(R.string.home_counts,FilterVpnService.blocked.get()));
        boolean all=FilterVpnService.prefs(this).getBoolean("all_apps",true);
        for(Switch sw:targets){sw.setVisibility(all?View.GONE:View.VISIBLE);sw.setEnabled(Boolean.TRUE.equals(sw.getTag())&&!FilterVpnService.running&&!FilterVpnService.starting&&!all);}
        if(allApps!=null)allApps.setEnabled(!FilterVpnService.running&&!FilterVpnService.starting);
        if("diagnostics".equals(page)){
            String hint="未检测到私人 DNS";
            ConnectivityManager cm=getSystemService(ConnectivityManager.class);
            if(cm!=null)for(Network n:cm.getAllNetworks()){LinkProperties lp=cm.getLinkProperties(n);if(lp!=null&&lp.isPrivateDnsActive()){hint="私人 DNS 已开启，可能绕过过滤";break;}}
            network.setText(hint);
        }
    }
    private void message(String title,String text) {new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
    private void probe() {
        if(!FilterVpnService.running){message("请先开启过滤","链路自检需要静启 VPN 正在运行。");return;}
        Toast.makeText(this,"正在自检，最多约 12 秒",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String result="本地拦截："+DnsProbe.check(FilterVpnService.SELF_TEST,3)+"\n上游解析："+DnsProbe.check("example.com",0);
            runOnUiThread(()->{lastProbe=result;if(!isFinishing()&&!isDestroyed()) message("链路自检",result+"\n\n通过只说明静启自己的 DNS 测试链路工作，不代表目标应用使用相同链路，也不代表广告已经拦住。");});
        },"QuietStart-Probe").start();
    }
    private void beginDiagnostic() {
        if(!FilterVpnService.running){message("请先开启过滤","开启后再复现广告，才能记录过滤过程。");return;}
        new AlertDialog.Builder(this).setTitle("选择要复现广告的应用").setItems(FilterVpnService.NAMES,(d,index)->{
            String pkg=FilterVpnService.PACKAGES[index];
            if(!installed(pkg)||(!FilterVpnService.prefs(this).getBoolean("all_apps",true)&&!FilterVpnService.prefs(this).getBoolean(pkg,true))){message("应用未在过滤范围内","请先暂停过滤，安装并勾选该应用，再重新开启。");return;}
            String version="";try{version=getPackageManager().getPackageInfo(pkg,0).versionName;}catch(PackageManager.NameNotFoundException ignored){}
            final String app=FilterVpnService.NAMES[index]+" "+version;
            new AlertDialog.Builder(this).setTitle("记录 60 秒请求").setMessage("记录本机经过过滤器的域名，保存在内存中，不上传。点击开始后打开目标应用；等广告出现，回到静启点击“结束记录 / 查看诊断报告”。这次测试不会新增拦截规则。")
                .setNegativeButton("取消",null).setPositiveButton("开始并打开应用",(a,b)->{
                    FilterVpnService.beginDiagnostic(app);
                    Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
                    if(launch!=null) open(launch); else message("记录已开始","请手动打开目标应用。");
                }).show();
        }).show();
    }
    private void showDiagnostic() {
        String text=FilterVpnService.finishDiagnostic()+"\n链路自检：\n"+lastProbe+"\n系统提示："+network.getText()
            +"\n额外拦截：\n"+FilterVpnService.prefs(this).getString("block","")+"\n白名单：\n"+FilterVpnService.prefs(this).getString("allow","");
        TextView report=new TextView(this);report.setText(text);report.setTextIsSelectable(true);report.setPadding(dp(16),dp(12),dp(16),dp(12));report.setTextSize(13);
        ScrollView scroll=new ScrollView(this);scroll.addView(report);
        new AlertDialog.Builder(this).setTitle("诊断报告（本机预览）").setView(scroll).setPositiveButton("关闭",null)
            .setNeutralButton("复制报告",(d,w)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("静启诊断",text));Toast.makeText(this,"已复制，可粘贴到对话中",Toast.LENGTH_SHORT).show();}).show();
    }
    private void open(Intent i) {try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){message("请手动打开设置","当前系统没有开放这个快捷入口，请在手机设置中查找对应选项。可以返回静启继续使用。");}}
    private void editRules(String key,String title) {
        EditText input=new EditText(this); input.setText(FilterVpnService.prefs(this).getString(key,"")); input.setGravity(Gravity.TOP); input.setMinLines(5); input.setMaxLines(10); input.setHint("每行一个完整域名，不要填网址或路径"); input.setTextSize(15);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage("会同时匹配该域名及子域名。保存后立即应用，新规则不清除其他应用的 DNS 或广告缓存。").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{Rules.parse(input.getText().toString()); saveRuleText(key,input.getText().toString());dialog.dismiss();}catch(IllegalArgumentException e){input.setError(e.getMessage());}})); dialog.show();
    }
    private void saveRuleText(String key,String value) {
        FilterVpnService.prefs(this).edit().putString(key,value).apply();
        reloadRules();
    }
    private void reloadRules() {
        if(FilterVpnService.running||FilterVpnService.starting) startService(new Intent(this,FilterVpnService.class).setAction(FilterVpnService.RELOAD));
    }
    private void updateSubscription() {
        if(!RuleRepository.updating.compareAndSet(false,true)){message("正在更新","请等待当前下载结束。");return;}
        Context app=getApplicationContext();
        Toast.makeText(this,"正在通过 HTTPS 下载完整规则，失败时保留原规则",Toast.LENGTH_LONG).show();
        new Thread(()->{
            String result;
            try {
                Subscription.Parsed parsed=RuleRepository.update(app);
                result="已保存 "+parsed.count()+" 条规则\n版本："+parsed.version()+"\n无效行："+parsed.rejected()+"\n完整规则模式开启时自动应用。";
                if(FilterVpnService.running||FilterVpnService.starting)app.startService(new Intent(app,FilterVpnService.class).setAction(FilterVpnService.RELOAD));
            }catch(Exception e){result="未完成更新，原规则仍保留。\n"+e.getMessage();}
            finally {RuleRepository.updating.set(false);}
            String report=result;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message("规则更新",report);});
        },"QuietStart-Subscription").start();
    }
    private void importSettings() {
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView description=new TextView(this);
        description.setText("支持 UTF-8 纯域名、hosts 和 AdGuard 整域规则，最多 10 MB / 50 万条。每次导入替换上次导入的规则，与内置库、额外拦截合并，白名单优先。仅手动更新。\n复杂例外、脚本、路径规则不能转换；有跳过项时需确认。FilterFusion 请使用 DNS 版本。\n最近导入："+FilterVpnService.prefs(this).getString("import_report","尚未导入"));panel.addView(description);
        Switch enabled=new Switch(this);enabled.setText("启用导入的规则");enabled.setChecked(FilterVpnService.prefs(this).getBoolean("import_enabled",true));
        enabled.setOnCheckedChangeListener((v,on)->{FilterVpnService.prefs(this).edit().putBoolean("import_enabled",on).apply();reloadRules();});panel.addView(enabled);
        EditText address=new EditText(this);address.setSingleLine(true);address.setHint("HTTPS 规则文件直链");address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(FilterVpnService.prefs(this).getString("import_url",""));panel.addView(address);
        Button example=new Button(this);example.setText("填入 FilterFusion DNS 地址");example.setOnClickListener(v->address.setText("https://cdn.jsdelivr.net/gh/Chaniug/FilterFusion@main/dist/dns-blocklist.txt"));panel.addView(example);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("导入屏蔽规则").setView(scroll).setNegativeButton("关闭",null)
            .setNeutralButton("选择本地文件",(d,w)->{
                try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),12);}
                catch(ActivityNotFoundException e){message("无法选择文件","系统没有可用的文件选择器");}
            }).setPositiveButton("下载 / 更新",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String url=address.getText().toString().trim();
            if(!url.startsWith("https://")){address.setError("请填写 HTTPS 规则直链");return;}
            runImport(()->ImportRepository.download(url),url);dialog.dismiss();
        }));dialog.show();
    }
    private interface ImportSource { ImportedRules.Parsed read() throws Exception; }
    private void runImport(ImportSource source,String url) { runImport(source,url,false); }
    private void runImport(ImportSource source,String url,boolean confirmed) {
        if(!ImportRepository.busy.compareAndSet(false,true)){message("正在导入","请等待当前导入结束");return;}
        Context app=getApplicationContext();
        Toast.makeText(this,"正在读取和验证规则",Toast.LENGTH_LONG).show();
        new Thread(()->{
            String report;
            try {
                ImportedRules.Parsed parsed=source.read();
                if(parsed.skipped()>0&&!confirmed){
                    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("部分规则无法转换").setMessage("可导入 "+parsed.count()+" 条屏蔽域名；跳过 "+parsed.skipped()+" 条复杂规则（可能包含放行例外）。忽略例外可能误拦，效果与原规则不同。是否继续？").setNegativeButton("取消",null).setPositiveButton("仍然导入",(d,w)->runImport(()->parsed,url,true)).show();});
                    return;
                }
                ImportRepository.save(app,parsed.text());
                report=parsed.count()+" 条，跳过 "+parsed.skipped()+" 条 · "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date());
                FilterVpnService.prefs(app).edit().putString("import_url",url).putString("import_report",report).putBoolean("import_enabled",true).apply();
                report="已导入并启用 "+report+"\n已替换上次导入的规则。过滤运行时会重新加载；已有广告缓存不会清除。";
                if(FilterVpnService.running||FilterVpnService.starting)app.startService(new Intent(app,FilterVpnService.class).setAction(FilterVpnService.RELOAD));
            }catch(Exception e){report="导入未完成："+e.getMessage();}
            finally{ImportRepository.busy.set(false);}
            String result=report;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message("规则导入",result);});
        },"QuietStart-Import").start();
    }
    private void showEvents() {
        List<FilterVpnService.Event> list=FilterVpnService.events();
        if(list.isEmpty()){message("暂无记录","日常过滤仅累计计数。排查时开启“临时记录查询域名”或使用 60 秒复现记录，再打开目标应用。没有记录不代表没有广告。");return;}
        String[] rows=new String[list.size()];for(int i=0;i<rows.length;i++){var e=list.get(i);rows[i]=e.time()+"  "+e.action()+"\n"+e.domain();}
        new AlertDialog.Builder(this).setTitle("最近请求 · 点击调整规则").setItems(rows,(d,index)->{
            String domain=list.get(index).domain();
            new AlertDialog.Builder(this).setTitle(domain).setMessage("调整域名可能影响应用功能；可以在规则编辑中撤销。")
                .setPositiveButton("加入白名单",(a,b)->append("allow",domain)).setNeutralButton("加入拦截",(a,b)->append("block",domain)).setNegativeButton("取消",null).show();
        }).setNeutralButton("清空",(a,b)->FilterVpnService.clearEvents()).setPositiveButton("关闭",null).show();
    }
    private void append(String key,String domain) {try{Rules.normalize(domain);saveRuleText(key,FilterVpnService.prefs(this).getString(key,"")+"\n"+domain);}catch(IllegalArgumentException e){message("无法添加",e.getMessage());}}
    @Override protected void onStart(){super.onStart();registerReceiver(stateReceiver,new IntentFilter(FilterVpnService.STATE_CHANGED),Context.RECEIVER_NOT_EXPORTED);}
    @Override protected void onStop(){unregisterReceiver(stateReceiver);super.onStop();}
    @Override protected void onResume(){super.onResume();FilterVpnService.restoreRequestedFiltering(this);FilterVpnService.requestHealthCheck(this);handler.removeCallbacks(tick);refresh();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override public void onBackPressed(){if("home".equals(page))finishAndRemoveTask();else showPage(parentPage());}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("page",page);state.putString("home_shortcut",homeShortcut);state.putBoolean("vpn_request_pending",vpnRequestPending);state.putLong("vpn_request_epoch",vpnRequestEpoch);super.onSaveInstanceState(state);}
}
