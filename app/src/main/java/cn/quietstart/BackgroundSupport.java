package cn.quietstart;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

/** Standard Android battery state only; OEM autostart/cleanup settings are not inferred. */
final class BackgroundSupport {
    enum BatteryState { OPTIMIZED, EXEMPT, UNKNOWN }
    static BatteryState batteryState(Context context){
        try{
            PowerManager manager=context.getSystemService(PowerManager.class);
            if(manager==null)return BatteryState.UNKNOWN;
            return manager.isIgnoringBatteryOptimizations(context.getPackageName())?BatteryState.EXEMPT:BatteryState.OPTIMIZED;
        }catch(RuntimeException unavailable){return BatteryState.UNKNOWN;}
    }
    static String batteryLabel(BatteryState state){
        switch(state){
            case EXEMPT:return "电池限制已放宽";
            case OPTIMIZED:return "当前使用系统省电设置";
            default:return "暂时无法读取电池设置";
        }
    }
    static Intent batteryRequest(Context context){
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+context.getPackageName()));
    }
    static boolean needsAccessHelp(AccessibilityStatus.State state){
        return state==AccessibilityStatus.State.DISCONNECTED||state==AccessibilityStatus.State.NOT_ENABLED||state==AccessibilityStatus.State.UNKNOWN;
    }
}
