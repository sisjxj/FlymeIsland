package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * User-controlled dimensions for the SystemUI-drawn black island.
 * Stores numbers only, never message text, so changing size survives reboot.
 */
public final class SystemUiIslandPrefs {
    private static final String SMALL_W = "system_island_pill_width_v025";
    private static final String SMALL_H = "system_island_pill_height_v025";
    private static final String TOP = "system_island_pill_top_v025";
    private static final String BIG_W = "system_island_panel_width_v025";
    private static final String PANEL_SAFE_TOP = "panel_safe_top_v050";
    public static final int PANEL_SAFE_TOP_MIN = 0, PANEL_SAFE_TOP_MAX = 80, PANEL_SAFE_TOP_DEFAULT = 28;
    private static final String BIG_H = "system_island_panel_height_v027";
    private static final String SWIPE = "island_swipe_direction_v028";
    public static final int SWIPE_OFF = 0, SWIPE_LEFT = 1, SWIPE_RIGHT = 2, SWIPE_BOTH = 3;

    public static final int PILL_W_MIN = 96, PILL_W_MAX = 300, PILL_W_DEFAULT = 148;
    public static final int PILL_H_MIN = 24, PILL_H_MAX = 64, PILL_H_DEFAULT = 29;
    public static final int TOP_MIN = 0, TOP_MAX = 48, TOP_DEFAULT = 8;
    public static final int PANEL_W_MIN = 230, PANEL_W_MAX = 540, PANEL_W_DEFAULT = 420;
    public static final int PANEL_H_MIN = 180, PANEL_H_MAX = 480, PANEL_H_DEFAULT = 320;

    private SystemUiIslandPrefs() {}

    public static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    public static int pillWidth(Context c) {
        return clamp(MusicPrefs.get(c).getInt(SMALL_W, PILL_W_DEFAULT),
                PILL_W_MIN, PILL_W_MAX);
    }
    public static int pillHeight(Context c) {
        return clamp(MusicPrefs.get(c).getInt(SMALL_H, PILL_H_DEFAULT),
                PILL_H_MIN, PILL_H_MAX);
    }
    public static int top(Context c) {
        return clamp(MusicPrefs.get(c).getInt(TOP, TOP_DEFAULT), TOP_MIN, TOP_MAX);
    }
    public static int panelWidth(Context c) {
        return clamp(MusicPrefs.get(c).getInt(BIG_W, PANEL_W_DEFAULT),
                PANEL_W_MIN, PANEL_W_MAX);
    }
    public static int panelHeight(Context c) {
        return clamp(MusicPrefs.get(c).getInt(BIG_H, PANEL_H_DEFAULT),
                PANEL_H_MIN, PANEL_H_MAX);
    }
    public static int panelSafeTop(Context c) {
        return clamp(MusicPrefs.get(c).getInt(PANEL_SAFE_TOP, PANEL_SAFE_TOP_DEFAULT), PANEL_SAFE_TOP_MIN, PANEL_SAFE_TOP_MAX);
    }
    public static void setPanelSafeTop(Context c, int x) {
        save(c, PANEL_SAFE_TOP, clamp(x, PANEL_SAFE_TOP_MIN, PANEL_SAFE_TOP_MAX));
        SystemUiAicyClient.syncWaveStyle(c);
    }
    public static void setPillWidth(Context c, int x) {
        save(c, SMALL_W, clamp(x, PILL_W_MIN, PILL_W_MAX));
    }
    public static void setPillHeight(Context c, int x) {
        save(c, SMALL_H, clamp(x, PILL_H_MIN, PILL_H_MAX));
    }
    public static void setTop(Context c, int x) {
        save(c, TOP, clamp(x, TOP_MIN, TOP_MAX));
    }
    public static void setPanelWidth(Context c, int x) {
        save(c, BIG_W, clamp(x, PANEL_W_MIN, PANEL_W_MAX));
    }
    public static void setPanelHeight(Context c, int x) {
        save(c, BIG_H, clamp(x, PANEL_H_MIN, PANEL_H_MAX));
        SystemUiAicyClient.syncWaveStyle(c);
    }


    /** Default right swipe dismiss; upward swipe always retracts expanded card. */
    public static int swipeMode(Context c) {
        return clamp(MusicPrefs.get(c).getInt(SWIPE, SWIPE_RIGHT), SWIPE_OFF, SWIPE_BOTH);
    }
    public static void setSwipeMode(Context c, int value) {
        save(c, SWIPE, clamp(value, SWIPE_OFF, SWIPE_BOTH));
    }
    public static void reset(Context c) {
        SharedPreferences.Editor e = MusicPrefs.get(c).edit();
        e.remove(SMALL_W).remove(SMALL_H).remove(TOP).remove(BIG_W).remove(BIG_H).remove(PANEL_SAFE_TOP);
        e.apply();
        SystemUiAicyClient.syncWaveStyle(c);
    }

    private static void save(Context c, String key, int value) {
        MusicPrefs.get(c).edit().putInt(key, value).apply();
    }
}
