package cn.quietstart;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private LinearLayout body;
    private TextView status,counts,network,ruleStatus,skipStatus;
    private Switch allApps;
    private Button mainToggle;
    private String page="home";
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
        getWindow().setStatusBarColor(Color.rgb(243,247,246)); getWindow().setNavigationBarColor(Color.rgb(243,247,246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(26),dp(24),dp(32));
        body.setBackgroundColor(Color.rgb(243,247,246)); scroll.addView(body); setContentView(scroll);
        showPage(b==null?"home":b.getString("page","home"));
    }
    private void showPage(String destination) {
        page=destination;body.removeAllViews();targets.clear();allApps=null;ruleStatus=null;mainToggle=null;skipStatus=null;
        ((ScrollView)body.getParent()).scrollTo(0,0);
        network=new TextView(this);
        if("home".equals(page)) {
        label("静启",34,true); label("让应用和浏览更安静",17,false);
        gap(18); status=label("",22,true); counts=label("",14,false);
        label("开启后，收起页面即可继续过滤。",14,false);
        skipStatus=label("",13,false);
        gap(10);
        mainToggle=button("开启过滤",()->{
            if(FilterVpnService.stopping)return;
            if(FilterVpnService.running||FilterVpnService.starting){FilterVpnService.pause(this);refresh();}
            else startFilter();
        },true);
        button("收起",this::finishAndRemoveTask,false);
        button("设置",()->showPage("settings"),false);
        refresh();return;
        }
        button("返回",()->showPage("settings".equals(page)?"home":"settings"),false);
        status=label("",16,true);counts=label("",13,false);
        if("settings".equals(page)) {
            label("设置",28,true);
            button("过滤范围",()->showPage("scope"),false);
            button("规则与白名单",()->showPage("rules"),false);
            button("自动关闭广告",()->showPage("skip"),false);
            button("后台运行",()->showPage("background"),false);
            button("问题排查",()->showPage("diagnostics"),false);
            button("关于静启",()->showPage("about"),false);
            refresh();return;
        }
        if("skip".equals(page)) {
            label("自动关闭广告",24,true);
            label("在各应用中识别明确的跳过广告、关闭广告按钮，以及同一局部区域内带广告标记的关闭按钮。系统界面不处理；不保存或上传界面内容。",14,false);
            Switch skip=new Switch(this);skip.setText("启用自动关闭广告");skip.setChecked(AdSkipService.enabled(this));
            skip.setOnCheckedChangeListener((v,on)->{FilterVpnService.prefs(this).edit().putBoolean("skip_ads",on).apply();refresh();});body.addView(skip);
            Switch popup=new Switch(this);popup.setText("同时关闭带明确标识的广告弹窗");popup.setChecked(AdSkipService.popupsEnabled(this));
            popup.setOnCheckedChangeListener((v,on)->FilterVpnService.prefs(this).edit().putBoolean("popup_ads",on).apply());body.addView(popup);
            Switch ctripTap=new Switch(this);ctripTap.setText("携程开屏兼容点击");ctripTap.setChecked(FilterVpnService.prefs(this).getBoolean("ctrip_tap",true));
            ctripTap.setOnCheckedChangeListener((v,on)->FilterVpnService.prefs(this).edit().putBoolean("ctrip_tap",on).apply());body.addView(ctripTap);
            label("携程未提供可点击控件时，仅在实时识别的右上角“跳过广告”文字处轻点；不使用固定坐标。",13,false);
            label("中国移动开屏：只在右上角倒计时跳过按钮和明确广告标记同时存在时轻点。",13,false);
            button("自动关闭排除名单",this::editSkipExclusions,false);
            skipStatus=label("",15,true);
            button("打开系统无障碍设置",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),false);
            label("打开上方开关后，在系统无障碍设置启用“静启 · 自动关闭广告”。无需逐个添加应用；不同应用和版本不保证成功。",13,false);
            label("与 VPN 过滤独立开关，无需 VPN 也可跳过。暂停过滤只停止域名过滤；关闭本页开关立即停止自动点击。广告可能短暂闪现。没有明确标识的促销弹窗、图片内的关闭按钮可能无法识别；不会自动清除网页广告空白。",13,false);
        }
        if("scope".equals(page)) {
        gap(16); label("过滤哪些应用",20,true);
        label("默认覆盖全部应用和浏览器；关闭全局过滤后，可选择下方应用。修改范围前请先暂停。",13,false);
        allApps=new Switch(this);allApps.setText("过滤所有应用和浏览器");allApps.setChecked(FilterVpnService.prefs(this).getBoolean("all_apps",true));
        allApps.setOnCheckedChangeListener((v,on)->{FilterVpnService.prefs(this).edit().putBoolean("all_apps",on).apply();refresh();});body.addView(allApps);
        for(int i=0;i<FilterVpnService.PACKAGES.length;i++) {
            String pkg=FilterVpnService.PACKAGES[i]; boolean installed=installed(pkg);
            Switch sw=new Switch(this); sw.setText(getString(R.string.target_label,FilterVpnService.NAMES[i],installed?"":"（未安装）")); sw.setTextSize(16);
            sw.setPadding(0,dp(10),0,dp(10)); sw.setTag(installed);
            sw.setChecked(FilterVpnService.prefs(this).getBoolean(pkg,true)); sw.setEnabled(installed&&!FilterVpnService.running);
            sw.setOnCheckedChangeListener((v,on)->FilterVpnService.prefs(this).edit().putBoolean(pkg,on).apply());
            body.addView(sw); targets.add(sw);
        }
        }
        if("rules".equals(page)) {
        gap(16); label("规则与白名单",20,true);
        ruleStatus=label(FilterVpnService.ruleInfo,13,false);
        Switch fullRules=new Switch(this);fullRules.setText(R.string.full_rules);fullRules.setChecked(FilterVpnService.prefs(this).getBoolean("full_rules",true));
        fullRules.setPadding(0,dp(12),0,dp(12));body.addView(fullRules);
        fullRules.setOnCheckedChangeListener((v,on)->{FilterVpnService.prefs(this).edit().putBoolean("full_rules",on).apply();reloadRules();});
        label("完整库随安装包提供，覆盖多个应用共用的广告平台；可一键更新，无需每次重新安装。若正常功能受影响，可关闭此开关切回精简规则，或加入白名单。",13,false);
        button("从 GitHub 更新完整规则",this::updateSubscription,false);
        button("导入屏蔽规则 / 自定义订阅",this::importSettings,false);
        }
        if("diagnostics".equals(page)) {
        gap(16);label("问题排查",20,true);
        button("检查过滤链路是否真的工作",this::probe,false);
        button("开始 60 秒广告复现记录",this::beginDiagnostic,false);
        button("结束记录 / 查看诊断报告",this::showDiagnostic,false);
        button("查看请求记录 / 放行误拦域名",this::showEvents,false);
        }
        if("rules".equals(page)) {
        button("编辑额外拦截域名",()->editRules("block","额外拦截域名"),false);
        button("编辑白名单",()->editRules("allow","白名单（优先放行）"),false);
        button("查看精简规则",()->{try{message("精简广告域名（完整库另行加载）",FilterVpnService.defaults(this));}catch(Exception e){message("读取失败",e.getMessage());}},false);
        }
        if("diagnostics".equals(page)) {
        Switch diagnostics=new Switch(this); diagnostics.setText("临时记录查询域名（排查时开启）"); diagnostics.setTextSize(15);
        diagnostics.setPadding(0,dp(12),0,dp(12)); diagnostics.setChecked(FilterVpnService.prefs(this).getBoolean("diagnostics",false));
        diagnostics.setOnCheckedChangeListener((v,on)->FilterVpnService.prefs(this).edit().putBoolean("diagnostics",on).apply()); body.addView(diagnostics);
        label("日常仅累计计数和记录解析错误，减少日志开销。开启此开关或 60 秒复现记录后才记录拦截、放行域名。普通记录最多 120 条，仅在本机内存保存。",12,false);
        }
        if("background".equals(page)) {
        gap(16); label("小米后台运行设置",20,true);
        network=label("",13,false);
        button("打开 VPN 设置",()->open(new Intent("android.settings.VPN_SETTINGS")),false);
        button("打开电池优化设置",()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),false);
        button("打开应用信息 / 自启动设置",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))),false);
        label("在系统设置中允许静启后台自启动，将省电策略设为无限制。VPN 设置若提供“始终开启 VPN”可启用；请勿开启“阻止不使用 VPN 的连接”，本软件只接管 DNS，这个选项可能导致断网。菜单名称以你的澎湃 OS 版本为准。",13,false);
        }
        if("about".equals(page)) {
        label("静启 0.6.0",24,true);
        gap(16); label("使用前请了解",20,true);
        label("• 首次开启需要系统 VPN 授权；已有 VPN 会被替换。\n• 会隐藏最近任务卡片，系统仍可显示 VPN 标识、通知和运行服务。清理、强行停止或系统省电仍可能终止过滤。\n• 广告加载成功或已有缓存时，摇一摇仍可能触发。本软件不能禁用其他应用的传感器，也不保证三个应用所有版本都有效。\n• 自带加密 DNS、直连 IP、共享业务域名的广告可能绕过。第一版支持系统 UDP DNS，暂不支持客户端直接使用 TCP DNS。\n• 普通网络连接不经过代理。未拦截域名交给阿里公共 DNS（223.5.5.5），失败后尝试腾讯公共 DNS（119.29.29.29），使用普通 DNS 查询；本软件没有上传日志或统计功能。",13,false);
        button("规则来源与许可",()->{try{message("anti-AD · MIT License","默认使用 anti-AD 完整域名库，另保留精简兼容模式。点击更新才会通过 HTTPS 下载公开规则，不上传诊断记录。\nhttps://github.com/privacy-protection-tools/anti-AD\n\n"+new String(getAssets().open("ANTI-AD-LICENSE.txt").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){message("错误",e.getMessage());}},false);
        }
        refresh();
    }
    private void editSkipExclusions(){
        EditText input=new EditText(this);input.setMinLines(4);input.setHint("每行一个应用包名，例如 com.example.app");
        input.setText(String.join("\n",FilterVpnService.prefs(this).getStringSet("skip_excluded",Collections.emptySet())));
        new AlertDialog.Builder(this).setTitle("不自动点击这些应用").setMessage("排除名单只影响自动关闭，不影响域名过滤。保存后立即生效。").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
            Set<String> excluded=new HashSet<>();for(String line:input.getText().toString().split("\\s+"))if(!line.isBlank())excluded.add(line.trim());
            FilterVpnService.prefs(this).edit().putStringSet("skip_excluded",excluded).apply();
        }).show();
    }
    private void gap(int h) { View v=new View(this); body.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private TextView label(String text,int size,boolean bold) {
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.rgb(29,53,50));
        t.setLineSpacing(dp(3),1); t.setPadding(0,dp(4),0,dp(5)); if(bold) t.setTypeface(null,Typeface.BOLD); body.addView(t); return t;
    }
    private Button button(String text,Runnable action,boolean primary) {
        Button b=new Button(this); b.setText(text); b.setTextSize(15); b.setAllCaps(false); b.setMinHeight(dp(48));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(primary?Color.rgb(19,117,107):Color.WHITE); shape.setCornerRadius(dp(12));
        b.setBackground(shape); b.setTextColor(primary?Color.WHITE:Color.rgb(19,90,83));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(8); body.addView(b,p); b.setOnClickListener(v->action.run());
        return b;
    }
    private boolean installed(String pkg) { try {getPackageManager().getPackageInfo(pkg,0); return true;}catch(PackageManager.NameNotFoundException e){return false;} }
    private void startFilter() {
        if(FilterVpnService.starting){message("正在载入规则","请稍候，完整规则库只在启动或更新时加载。");return;}
        if(FilterVpnService.running) { message("已经开启","过滤服务正在运行，可以收起页面。"); return; }
        boolean any=FilterVpnService.prefs(this).getBoolean("all_apps",true);
        for(String pkg:FilterVpnService.PACKAGES) if(installed(pkg)&&FilterVpnService.prefs(this).getBoolean(pkg,true)) any=true;
        if(!any) {message("没有目标应用","请安装并勾选小红书、网易云音乐或百度地图中的至少一个。");return;}
        if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},11);
        else requestVpn();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {super.onRequestPermissionsResult(request,permissions,results); if(request==11) requestVpn();}
    private void requestVpn() {Intent intent=VpnService.prepare(this); if(intent!=null) startActivityForResult(intent,10); else launchService();}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==12&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            runImport(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("无法读取文件");
                return ImportedRules.parse(Subscription.readBounded(in,ImportedRules.LIMIT));
            }},"");
        }
        if(request==10){
            if(result==RESULT_OK)launchService();
            else {FilterVpnService.starting=false;FilterVpnService.status="未授权 VPN，过滤未开启";refresh();}
        }
    }
    private void launchService() {
        try {
            FilterVpnService.start(this);refresh();
        }catch(Exception e){FilterVpnService.starting=false;FilterVpnService.status="启动失败";refresh();message("启动失败",e.getMessage());}
    }
    private void refresh() {
        if(status==null)return;
        if(skipStatus!=null)skipStatus.setText(getString(R.string.skip_state,!AdSkipService.enabled(this)?"关闭":AdSkipService.connected?"已连接":"等待开启系统无障碍服务","skip".equals(page)?getString(R.string.skip_count,FilterVpnService.prefs(this).getLong("skip_clicks",0)):""));
        if(skipStatus!=null&&"skip".equals(page))skipStatus.append("\n本次界面事件："+AdSkipService.received+"，扫描："+AdSkipService.scans+"\n"+AdSkipService.outcome+"\n"+AdSkipService.lastPackage);
        if(skipStatus!=null&&"skip".equals(page))skipStatus.append("\n最近携程触摸："+FilterVpnService.prefs(this).getString("ctrip_tap_status","尚未识别到跳过按钮"));
        status.setText(FilterVpnService.status);
        if(mainToggle!=null){
            mainToggle.setText(FilterVpnService.stopping?"正在暂停…":FilterVpnService.starting?"取消启动":FilterVpnService.running?"暂停过滤":"开启过滤");
            mainToggle.setEnabled(!FilterVpnService.stopping);
        }
        counts.setText("home".equals(page)?getString(R.string.home_counts,FilterVpnService.blocked.get()):getString(R.string.request_counts,FilterVpnService.blocked.get(),FilterVpnService.passed.get(),FilterVpnService.failed.get(),FilterVpnService.running?"范围："+FilterVpnService.scope:"开启后可收起页面，桌面图标始终保留"));
        boolean all=FilterVpnService.prefs(this).getBoolean("all_apps",true);
        for(Switch sw:targets) sw.setEnabled(Boolean.TRUE.equals(sw.getTag())&&!FilterVpnService.running&&!FilterVpnService.starting&&!all);
        if(allApps!=null)allApps.setEnabled(!FilterVpnService.running&&!FilterVpnService.starting);
        if(ruleStatus!=null)ruleStatus.setText(FilterVpnService.ruleInfo);
        String hint="开启后若请求计数一直为零，请检查私人 DNS 或应用是否使用加密 DNS。";
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        for(Network n:cm.getAllNetworks()) { LinkProperties lp=cm.getLinkProperties(n); if(lp!=null && lp.isPrivateDnsActive()) {hint="检测到私人 DNS：它可能绕过本地过滤。请在系统设置搜索“私人 DNS”，测试时设为关闭。"; break;} }
        network.setText(hint);
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
    private void open(Intent i) {try{startActivity(i);}catch(ActivityNotFoundException e){message("请手动打开设置","当前系统没有这个快捷入口，请在手机设置中搜索 VPN、自启动或电池优化。");}}
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
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(tick);refresh();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override public void onBackPressed(){if("home".equals(page))finishAndRemoveTask();else showPage("settings".equals(page)?"home":"settings");}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("page",page);super.onSaveInstanceState(state);}
}
