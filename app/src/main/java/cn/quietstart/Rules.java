package cn.quietstart;

import java.net.IDN;
import java.util.*;

/** Exact names and subdomains, with label boundaries and allow-list precedence. */
public final class Rules {
    private final Set<String> blocked, allowed;
    public Rules(String defaults, String custom, String exceptions) {
        blocked = parse(defaults + "\n" + custom); allowed = parse(exceptions);
    }
    public static Set<String> parse(String text) {
        Set<String> result = new HashSet<>();
        for (String line : text.split("\\R")) {
            line = line.split("#", 2)[0].trim();
            if (line.isEmpty()) continue;
            result.add(normalize(line));
        }
        return result;
    }
    public static String normalize(String name) {
        String value = name.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) value = value.substring(0, value.length()-1);
        value = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES);
        if (value.length() > 253 || !value.contains(".")) throw new IllegalArgumentException("请输入完整域名：" + name);
        for (String label : value.split("\\.", -1)) if (label.isEmpty() || label.length() > 63) throw new IllegalArgumentException("无效域名：" + name);
        return value;
    }
    private static boolean matches(Set<String> set, String name) {
        for (;;) {
            if (set.contains(name)) return true;
            int dot = name.indexOf('.');
            if (dot < 0) return false;
            name = name.substring(dot+1);
        }
    }
    public boolean blocks(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) name = name.substring(0, name.length()-1);
        return !matches(allowed, name) && matches(blocked, name);
    }
    public int size() { return blocked.size(); }
}
