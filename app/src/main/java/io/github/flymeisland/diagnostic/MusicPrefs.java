package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.SharedPreferences;

/** Charging and Spotify are opt-in. Quiet defaults on for new installs. */
public final class MusicPrefs {
    private static final String NAME = "flyme_island_music";
    private static final String ENABLED = "spotify_enabled";
    private static final String QUIET = "quiet_channel_enabled";
    private static final String CHARGING = "charging_enabled";
    private static final String WECHAT = "message_wechat_enabled";
    private static final String QQ = "message_qq_enabled";
    private static final String RESPECT_DND = "messages_respect_dnd";
    private static final String MESSAGE_PREVIEW = "messages_preview_enabled";
    private static final String MINI_EXPERIMENT = "wechat_mini_action_experimental";
    private static final String KEEP_MIRROR = "messages_retain_mirror";
    /** Opt-in A/B variant. Never changes the known-working default RemoteViews. */
    private static final String WECHAT_EXPANDED_ROOT_CLICK = "wechat_expanded_root_click_v018";
    /** New independent v0.19 flag; old v0.18 root-click setting is ignored. */
    private static final String WECHAT_MUSIC_LIKE_CLICK = "wechat_music_like_click_v019";

    private MusicPrefs() {}

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return get(context).getBoolean(ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    public static boolean mediaIconMode(Context c) { return get(c).getBoolean("media_icon_mode_v047", true); }
    public static void setMediaIconMode(Context c, boolean enabled) { get(c).edit().putBoolean("media_icon_mode_v047", enabled).apply(); SystemUiAicyClient.syncWaveStyle(c); }

    public static boolean callEnabled(Context c) { return get(c).getBoolean("call_island_v052", true); }
    public static void setCallEnabled(Context c, boolean enabled) { get(c).edit().putBoolean("call_island_v052", enabled).apply(); }
    public static boolean realWave(Context context) { return get(context).getBoolean("real_audio_wave_v037", false); }
    public static void setRealWave(Context context, boolean value) { get(context).edit().putBoolean("real_audio_wave_v037", value).apply(); }

    public static boolean quiet(Context context) {
        return get(context).getBoolean(QUIET, true);
    }

    public static void setQuiet(Context context, boolean quiet) {
        get(context).edit().putBoolean(QUIET, quiet).apply();
    }
    public static boolean chargingEnabled(Context context) {
        return get(context).getBoolean(CHARGING, false);
    }

    public static void setChargingEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(CHARGING, enabled).apply();
    }
    public static boolean wechatEnabled(Context context) {
        return get(context).getBoolean(WECHAT, false);
    }

    public static void setWechatEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(WECHAT, enabled).apply();
    }

    public static boolean qqEnabled(Context context) {
        return get(context).getBoolean(QQ, false);
    }

    public static void setQqEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(QQ, enabled).apply();
    }

    public static boolean respectDnd(Context context) {
        return get(context).getBoolean(RESPECT_DND, true);
    }

    public static void setRespectDnd(Context context, boolean enabled) {
        get(context).edit().putBoolean(RESPECT_DND, enabled).apply();
    }
    public static boolean messagePreview(Context context) {
        return get(context).getBoolean(MESSAGE_PREVIEW, false);
    }

    public static void setMessagePreview(Context context, boolean enabled) {
        get(context).edit().putBoolean(MESSAGE_PREVIEW, enabled).apply();
    }
    /** Opt-in: adds a standard Android notification action; never changes capsule RemoteViews. */
    public static boolean miniExperiment(Context context) {
        return get(context).getBoolean(MINI_EXPERIMENT, false);
    }
    public static void setMiniExperiment(Context context, boolean enabled) {
        get(context).edit().putBoolean(MINI_EXPERIMENT, enabled).apply();
    }
    /**
     * Experimental: only on real WeChat message capsules, use a separate
     * expanded RemoteViews layout with a click action on its root container.
     * Default OFF means the proven v0.13 layout stays byte-for-byte unchanged.
     */
    public static boolean wechatExpandedRootClick(Context context) {
        return get(context).getBoolean(WECHAT_EXPANDED_ROOT_CLICK, false);
    }
    public static void setWechatExpandedRootClick(Context context, boolean enabled) {
        get(context).edit().putBoolean(WECHAT_EXPANDED_ROOT_CLICK, enabled).apply();
    }

    /** Test the Spotify publisher's click strategy: only Notification.contentIntent. */
    public static boolean wechatMusicLikeClick(Context context) {
        return get(context).getBoolean(WECHAT_MUSIC_LIKE_CLICK, false);
    }

    public static void setWechatMusicLikeClick(Context context, boolean enabled) {
        // Force-disable the failed v0.18 root-click preference for clarity.
        get(context).edit().putBoolean(WECHAT_EXPANDED_ROOT_CLICK, false)
                .putBoolean(WECHAT_MUSIC_LIKE_CLICK, enabled).apply();
    }

    /** Keeps a *second*, redacted app notification temporarily. Never alters source. */
    public static boolean keepMessageMirror(Context c) {
        return get(c).getBoolean(KEEP_MIRROR, false);
    }
    public static void setKeepMessageMirror(Context c, boolean enabled) {
        get(c).edit().putBoolean(KEEP_MIRROR, enabled).apply();
    }
}



