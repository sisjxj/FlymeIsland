package io.github.flymeisland.diagnostic;
import android.content.Context;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
/** Application-level opt-in, independent from notification selection. */
public final class GestureAppPrefs {
    public static final String KEY = "island_media_gesture_apps_v039";
    public static final String MEDIA_KEY = "media_capsule_gesture_v044";
    private GestureAppPrefs() {}
    public static boolean mediaEnabled(Context c) { return MusicPrefs.get(c).getBoolean(MEDIA_KEY, false); }
    public static void setMediaEnabled(Context c, boolean enabled) {
        MusicPrefs.get(c).edit().putBoolean(MEDIA_KEY, enabled).apply(); SystemUiAicyClient.syncGestureApps(c);
    }
    public static Set<String> get(Context c) { return new HashSet<>(MusicPrefs.get(c).getStringSet(KEY, Collections.emptySet())); }
    public static boolean enabled(Context c, String pkg) { return pkg != null && get(c).contains(pkg); }
    public static void save(Context c, Set<String> values) {
        MusicPrefs.get(c).edit().putStringSet(KEY, new HashSet<>(values)).apply();
        SystemUiAicyClient.syncGestureApps(c);
    }
}
