package io.github.flymeisland.diagnostic;

import android.content.Context;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Explicit per-application opt-in; a fresh installation starts empty. */
public final class AppSelectionPrefs {
    public static final String KEY = "island_selected_apps_v036";
    private AppSelectionPrefs() {}
    public static Set<String> get(Context context) {
        return new HashSet<>(MusicPrefs.get(context).getStringSet(KEY, Collections.emptySet()));
    }
    public static boolean enabled(Context context, String pkg) {
        return pkg != null && get(context).contains(pkg);
    }
    public static void save(Context context, Set<String> values) {
        MusicPrefs.get(context).edit().putStringSet(KEY, new HashSet<>(values)).apply();
        SystemUiAicyClient.syncSelectedApps(context);
    }
}
