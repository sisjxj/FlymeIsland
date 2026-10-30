package io.github.flymeisland.diagnostic;

import android.content.Context;

/** Every message feature is opt-in; only boolean preferences are persisted. */
public final class MessagePrefs {
    private static final String WECHAT = "messages_wechat";
    private static final String QQ = "messages_qq";
    private static final String STRICT = "messages_strict";
    private static final String DND = "messages_respect_dnd";
    private MessagePrefs() {}

    public static boolean wechat(Context c) {
        return MusicPrefs.get(c).getBoolean(WECHAT, false);
    }
    public static boolean qq(Context c) {
        return MusicPrefs.get(c).getBoolean(QQ, false);
    }
    public static boolean strict(Context c) {
        return MusicPrefs.get(c).getBoolean(STRICT, true);
    }
    public static boolean respectDnd(Context c) {
        return MusicPrefs.get(c).getBoolean(DND, true);
    }
    public static void setWechat(Context c, boolean b) {
        MusicPrefs.get(c).edit().putBoolean(WECHAT, b).apply();
    }
    public static void setQq(Context c, boolean b) {
        MusicPrefs.get(c).edit().putBoolean(QQ, b).apply();
    }
    public static void setStrict(Context c, boolean b) {
        MusicPrefs.get(c).edit().putBoolean(STRICT, b).apply();
    }
    public static void setRespectDnd(Context c, boolean b) {
        MusicPrefs.get(c).edit().putBoolean(DND, b).apply();
    }
    public static boolean changed(String key) {
        return WECHAT.equals(key) || QQ.equals(key) || STRICT.equals(key) || DND.equals(key);
    }
}
