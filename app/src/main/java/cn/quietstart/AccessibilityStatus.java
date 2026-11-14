package cn.quietstart;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** Reads system configuration separately from the live service connection. */
final class AccessibilityStatus {
    enum State { OFF, CONNECTED, DISCONNECTED, NOT_ENABLED, UNRESPONSIVE, UNKNOWN }

    static State read(Context context) {
        if (!AdSkipService.enabled(context)) return State.OFF;
        try {
            String services = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            ComponentName expected = new ComponentName(context, AdSkipService.class);
            if (services != null) {
                for (String entry : services.split(":")) {
                    if (expected.equals(ComponentName.unflattenFromString(entry))) {
                        return !AdSkipService.connected ? State.DISCONNECTED : AdSkipService.scanUnresponsive() ? State.UNRESPONSIVE : State.CONNECTED;
                    }
                }
            }
            return State.NOT_ENABLED;
        } catch (SecurityException unavailable) {
            return State.UNKNOWN;
        }
    }

    static String label(State state) {
        switch (state) {
            case OFF: return "应用内开关已关闭";
            case CONNECTED: return "已连接";
            case DISCONNECTED: return "系统开关已开启，服务未连接";
            case NOT_ENABLED: return "系统开关未开启";
            case UNRESPONSIVE: return "已授权，扫描暂未响应";
            default: return "服务未连接，系统开关状态无法读取";
        }
    }
}
