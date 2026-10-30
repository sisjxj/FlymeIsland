package io.github.flymeisland.diagnostic;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class PriorityPrefs {
    private static final String KEY = "island_app_priority_v035";
    private static final String DEFAULT = "com.tencent.mm,com.tencent.mobileqq";
    private PriorityPrefs() {}
    public static ArrayList<String> get(Context c) {
        String value = MusicPrefs.get(c).getString(KEY, DEFAULT);
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String pkg : value.split(",")) {
            if (valid.size() == 64) break;
            if (("android".equals(pkg) || pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) && pkg.length() <= 200)
                valid.add(pkg);
        }
        return new ArrayList<>(valid);
    }
    public static void save(Context c, List<String> packages) {
        MusicPrefs.get(c).edit().putString(KEY, String.join(",", packages)).apply();
    }
}
