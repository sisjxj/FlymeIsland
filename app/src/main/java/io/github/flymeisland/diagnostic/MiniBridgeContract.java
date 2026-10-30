package io.github.flymeisland.diagnostic;

/** UID-authenticated: bounded opt-in notification previews, no IntentSender or arbitrary commands. */
public final class MiniBridgeContract {
    public static final String APP = "io.github.flymeisland.diagnostic";
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final String ACTION_PROBE = APP + ".bridge.PROBE_UID_ONLY_V017";
    public static final String ACTION_CAMERA_ANCHOR = APP + ".bridge.CAMERA_ANCHOR_TEST_V022";
    public static final String ACTION_SYSTEM_ISLAND = APP + ".bridge.SYSTEM_UI_ISLAND_V023";
    public static final String ACTION_SCREEN_RELAY = APP + ".bridge.SCREEN_STATE_V049";
    public static final String EXTRA_SCREEN_STATE = "screen_state_v049";
    public static final String ACTION_AICY_PRESENCE = APP + ".bridge.AICY_WINDOW_PRESENCE_V031";
    public static final String EXTRA_AICY_ACTIVE = "aicy_voice_window_active_v031";
    public static final String EXTRA_ISLAND_COMMAND = "safe_system_island_command";
    public static final String EXTRA_ISLAND_TITLE = "island_sender_label_v025";
    public static final String EXTRA_ISLAND_BODY = "island_message_preview_v025";
    public static final String EXTRA_ISLAND_PILL_W = "island_pill_w_v025";
    public static final String EXTRA_ISLAND_PILL_H = "island_pill_h_v025";
    public static final String EXTRA_ISLAND_TOP = "island_pill_top_v025";
    public static final String EXTRA_ISLAND_PANEL_W = "island_panel_w_v025";
    public static final String EXTRA_PANEL_SAFE_TOP = "panel_safe_top_v050";
    public static final String EXTRA_ISLAND_PANEL_H = "island_panel_h_v025";
    public static final String EXTRA_ISLAND_AVATAR = "island_avatar_png_v026";
    public static final String EXTRA_ISLAND_CHAT_ACTION = "island_chat_action_v027";
    public static final String EXTRA_ISLAND_SOURCE_KEY = "island_source_key_v028";
    public static final String EXTRA_ISLAND_SWIPE_MODE = "island_swipe_mode_v028";
    public static final String EXTRA_ISLAND_RESTORE = "island_restore_existing_v028";
    public static final String EXTRA_SOURCE_PACKAGE = "island_source_package_v035";
    public static final String EXTRA_APP_PRIORITY = "island_app_priority_v035";
    public static final String EXTRA_SELECTED_APPS = "island_selected_apps_v036";
    /**
     * Strictly app-bound notification cleanup request sent by SystemUI only
     * after a user-initiated source PendingIntent has been dispatched.
     */
    public static final String ACTION_OPENED_DISMISS =
            APP + ".bridge.OPENED_SOURCE_DISMISS_V030";
    public static final String COMMAND_UNLOCK_REPLAY = "UNLOCK_REPLAY";
    public static final String EXTRA_UNLOCK_CYCLE = "unlock_cycle";
    public static final String COMMAND_REMOVE = "REMOVE";
    public static final String COMMAND_DEMO = "DEMO";
    public static final String COMMAND_WECHAT = "WECHAT";
    public static final String COMMAND_QQ = "QQ";
    public static final String COMMAND_OTHER = "OTHER";
    public static final String COMMAND_CALL = "CALL";
    public static final String EXTRA_CALL_LAYOUT = "call_layout_v061";
    public static final String EXTRA_CALL_SIZES = "call_sizes_v055";
    public static final String EXTRA_CALL_CAPS = "call_caps_v054";
    public static final String ACTION_CALL_CONTROL = APP + ".bridge.CALL_CONTROL_V052";
    public static final String COMMAND_PRIORITY = "PRIORITY";
    public static final String COMMAND_SELECTION = "SELECTION";
    public static final String COMMAND_MUSIC = "MUSIC";
    public static final String COMMAND_WAVE = "WAVE";
    public static final String EXTRA_WAVE_LEVELS = "audio_wave_levels_v037";
    public static final String COMMAND_CHARGE = "CHARGE";
    public static final String COMMAND_TEST = "TEST";
    public static final String MUSIC_KEY = "island:media";
    public static final String ACTION_MEDIA_CONTROL = APP + ".bridge.MEDIA_CONTROL_V045";
    public static final String EXTRA_MEDIA_ACTIONS = "media_actions_v045";
    public static final String EXTRA_MEDIA_CUSTOM_IDS = "media_custom_ids_v045";
    public static final String EXTRA_MEDIA_ICON_MODE = "media_icon_mode_v047";
    public static final String EXTRA_MEDIA_CUSTOM_ICONS = "media_custom_icons_v047";
    public static final String EXTRA_MEDIA_CUSTOM_LABELS = "media_custom_labels_v045";
    public static final String ACTION_MEDIA_GESTURE = APP + ".bridge.MEDIA_GESTURE_V039";
    public static final String EXTRA_GESTURE_APPS = "media_gesture_apps_v039";
    public static final String EXTRA_MEDIA_GESTURE_ENABLED = "media_gesture_enabled_v044";
    public static final String EXTRA_MEDIA_GESTURE_SOURCE = "media_gesture_source_v044";
    public static final String EXTRA_MEDIA_DIRECTION = "media_direction_v039";
    public static final String COMMAND_GESTURE_CONFIG = "GESTURE_CONFIG";
    public static final String COMMAND_APP_SIZE = "APP_SIZE";
    public static final String COMMAND_WAVE_STYLE = "WAVE_STYLE";
    public static final String EXTRA_WAVE_STYLE = "wave_style_v040";
    public static final String EXTRA_MEDIA_PLAYING = "media_playing_v037";
    public static final String CHARGE_KEY = "island:charge";
    public static final String COMMAND_HIDE = "HIDE";
    public static final String EXTRA_SENT_AT = "created_elapsed";
    public static final String EXTRA_REPLY = "safe_result_receiver";
    public static final String EXTRA_REPORT = "bridge_report";

    private MiniBridgeContract() {}
}
