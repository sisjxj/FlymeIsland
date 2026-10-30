package io.github.flymeisland.diagnostic;

import android.content.Context;

/** Independent sizes for compact, in-call controls and incoming card. */
final class CallSizePrefs {
    static final int[] MIN = {96, 24, 160, 32, 230, 180};
    static final int[] MAX = {300, 64, 400, 96, 540, 480};
    static final int[] DEFAULTS = {148, 32, 250, 48, 420, 240};
    private static final String KEY = "call_stage_size_v055_";
    private CallSizePrefs() {}
    static int[] get(Context context) {
        int[] values = new int[DEFAULTS.length];
        for (int i=0; i<values.length; i++)
            values[i] = SystemUiIslandPrefs.clamp(MusicPrefs.get(context).getInt(KEY+i, DEFAULTS[i]), MIN[i], MAX[i]);
        return values;
    }
    static void save(Context context, int[] input) {
        if (input == null || input.length != DEFAULTS.length) return;
        android.content.SharedPreferences.Editor editor = MusicPrefs.get(context).edit();
        for (int i=0; i<input.length; i++) editor.putInt(KEY+i, SystemUiIslandPrefs.clamp(input[i], MIN[i], MAX[i]));
        editor.apply();
    }
}
