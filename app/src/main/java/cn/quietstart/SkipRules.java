package cn.quietstart;
import java.util.Set;
import java.util.regex.Pattern;
/** Conservative ad-dismiss labels; generic close requires local ad context. */
public final class SkipRules {
    public static final String CMCC="com.greenpoint.android.mc10086.activity";
    public static boolean cmccLabel(CharSequence text){return clean(text).matches("[0-9]{1,2}跳过");}
    private static final Set<String> SYSTEM=Set.of("android","cn.quietstart","com.android.settings","com.android.systemui","com.android.permissioncontroller","com.android.packageinstaller","com.android.launcher","com.android.launcher3","com.google.android.permissioncontroller","com.google.android.packageinstaller","com.miui.securitycenter","com.miui.home","com.miui.packageinstaller","com.miui.aod");
    private static final Pattern LABEL=Pattern.compile("(?:\\d{1,2}[sS秒]?)?跳过广告(?:[（(]?\\d{1,2}[sS秒]?[）)]?)?");
    private static final Pattern COUNTDOWN=Pattern.compile("(?:[0-9]{1,2}[sS秒]?跳过|跳过[（(]?[0-9]{1,2}[sS秒]?[）)]?)");
    public static boolean countdown(CharSequence text){return COUNTDOWN.matcher(clean(text)).matches();}
    public static boolean eligible(String pkg){return pkg!=null&&!pkg.isBlank()&&!SYSTEM.contains(pkg);}
    private static String clean(CharSequence text){return text==null?"":text.toString().replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);}
    public static boolean matches(String pkg,CharSequence text){return eligible(pkg)&&LABEL.matcher(clean(text)).matches();}
    public static boolean ctripTap(String pkg,CharSequence text,CharSequence description,int l,int t,int r,int b,int w,int h){return "ctrip.android.view".equals(pkg)&&(matches(pkg,text)||matches(pkg,description))&&safeBounds(l,t,r,b,w,h);}
    public static boolean explicitClose(CharSequence text){return Set.of("关闭广告","跳过此广告","关闭此广告","closead","closeadvertisement","skipad").contains(clean(text));}
    public static boolean closeLabel(CharSequence text){return Set.of("关闭","close","×","✕","✖").contains(clean(text));}
    public static boolean adMarker(CharSequence text){return Set.of("广告","advertisement","sponsored").contains(clean(text));}
    public static boolean smallBounds(int l,int t,int r,int b,int w,int h){return w>0&&h>0&&l>=0&&t>=0&&r<=w&&b<=h&&r>l&&b>t&&r-l<=w/2&&b-t<=h/8;}
    public static boolean safeBounds(int l,int t,int r,int b,int w,int h){return w>0&&h>0&&l>=w/2&&t>=0&&r<=w&&b<=h/4&&r>l&&b>t&&r-l<=w/2&&b-t<=h/8;}
}
