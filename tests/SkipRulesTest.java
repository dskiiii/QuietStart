package cn.quietstart;
public final class SkipRulesTest {
    public static void main(String[] args){
        for(String s:new String[]{"跳过广告","4 跳过广告","跳过广告 5s","跳过广告（3秒）"})if(!SkipRules.matches("ctrip.android.view",s))throw new AssertionError(s);
        for(String s:new String[]{"跳过","关闭","立即购买","跳过广告领取奖励","跳过登录","确认支付","不跳过广告","广告","关闭广告"})if(SkipRules.matches("ctrip.android.view",s))throw new AssertionError(s);
        if(!SkipRules.matches("com.example.app","跳过广告")||SkipRules.matches("ctrip.android.view",null))throw new AssertionError("scope/null");
        if(!SkipRules.eligible("com.android.chrome")||!SkipRules.eligible("com.miui.browser"))throw new AssertionError("browser excluded");
        for(String pkg:new String[]{"android","cn.quietstart","com.android.settings","com.android.systemui","com.miui.securitycenter","com.google.android.permissioncontroller"})if(SkipRules.matches(pkg,"跳过广告"))throw new AssertionError("system: "+pkg);
        for(String s:new String[]{"关闭广告","关闭此广告","Close ad","Skip ad"})if(!SkipRules.explicitClose(s))throw new AssertionError(s);
        for(String s:new String[]{"关闭","确认支付","关闭广告提醒","观看广告领取奖励","关闭个性化广告","跳过登录","不再提示", ""})if(SkipRules.explicitClose(s))throw new AssertionError("ambiguous: "+s);
        for(String s:new String[]{"广告设置","广告隐私","广告加载失败","这不是广告","ad",""})if(SkipRules.adMarker(s))throw new AssertionError("marker: "+s);
        if(!SkipRules.adMarker("广告")||!SkipRules.closeLabel("×")||SkipRules.closeLabel("取消")||SkipRules.closeLabel("确认"))throw new AssertionError("local labels");
        if(!SkipRules.smallBounds(400,1600,500,1700,1080,2340)||SkipRules.smallBounds(-1,0,50,50,1080,2340)||SkipRules.smallBounds(0,0,1080,2340,1080,2340))throw new AssertionError("popup bounds");
        if(!SkipRules.safeBounds(800,90,1030,200,1080,2340))throw new AssertionError("valid button");
        if(!SkipRules.ctripTap("ctrip.android.view","跳过广告",null,847,141,1003,193,1080,2340))throw new AssertionError("Ctrip observed label");
        if(SkipRules.ctripTap("com.example.app","跳过广告",null,847,141,1003,193,1080,2340)||SkipRules.ctripTap("ctrip.android.view","立即参与",null,847,141,1003,193,1080,2340)||SkipRules.ctripTap("ctrip.android.view","跳过广告",null,0,123,1080,211,1080,2340)||SkipRules.ctripTap("ctrip.android.view","跳过广告",null,847,1410,1003,1493,1080,2340))throw new AssertionError("unsafe Ctrip gesture");
        if(SkipRules.safeBounds(0,0,1080,2340,1080,2340)||SkipRules.safeBounds(800,1600,1030,1720,1080,2340)||SkipRules.safeBounds(800,90,1200,200,1080,2340))throw new AssertionError("unsafe click area");
        if(!SkipRules.cmccLabel("2 跳过")||SkipRules.cmccLabel("跳过")||SkipRules.cmccLabel("2 跳过登录")||SkipRules.cmccLabel(null))throw new AssertionError("CMCC exact countdown");
        for(String label:new String[]{"跳过 5","5 跳过","跳过(3s)"})if(!SkipRules.countdown(label))throw new AssertionError(label);
        for(String label:new String[]{"跳过","跳过登录","跳过5领取奖励","倒计时5"})if(SkipRules.countdown(label))throw new AssertionError(label);
        System.out.println("PASS: universal ad labels, system exclusions, ambiguous prompts, popup and opening bounds");
    }
}
