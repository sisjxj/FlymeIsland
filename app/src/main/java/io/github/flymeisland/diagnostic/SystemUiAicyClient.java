package io.github.flymeisland.diagnostic;

import android.app.BroadcastOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI-triggered / opt-in notification source -> UID-authenticated SystemUI window.
 * Only a whitelisted command, bounded local preview and bounded sizes are sent.
 * Neither message content nor sender is logged or saved to SharedPreferences.
 */
public final class SystemUiAicyClient {
    private static final String PREF = "systemui_island_enabled_v023";
    private SystemUiAicyClient() {}

    public static boolean enabled(Context c) {
        return MusicPrefs.get(c).getBoolean(PREF, false);
    }

    public static void setEnabled(Context c, boolean enabled) {
        MusicPrefs.get(c).edit().putBoolean(PREF, enabled).apply();
        if (!enabled) send(c, MiniBridgeContract.COMMAND_HIDE, null, null, null, null, null, null, false);
    }

    public static void demo(Context c, MiniBridgeClient.Callback callback) {
        send(c, MiniBridgeContract.COMMAND_DEMO, null, null, null, null, null, callback, false);
    }

    public static void stop(Context c, MiniBridgeClient.Callback callback) {
        send(c, MiniBridgeContract.COMMAND_HIDE, null, null, null, null, null, callback, false);
    }

    public static void refreshPriority(Context c) {
        if (enabled(c)) send(c, MiniBridgeContract.COMMAND_PRIORITY, null, null, null, null, null, null, false);
    }

    public static void syncWaveStyle(Context c) {
        if (enabled(c)) send(c, MiniBridgeContract.COMMAND_WAVE_STYLE, null, null, null, null, null, null, false);
    }
    public static void syncAppSize(Context c, String pkg) {
        if (!enabled(c) || pkg == null || pkg.isEmpty() || pkg.length() > 200 || Build.VERSION.SDK_INT < 34) return;
        Intent intent = new Intent(MiniBridgeContract.ACTION_SYSTEM_ISLAND).setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_COMMAND, MiniBridgeContract.COMMAND_APP_SIZE);
        intent.putExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE, pkg);
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_W, AppSizePrefs.width(c, pkg));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_H, AppSizePrefs.height(c, pkg));
        try { c.getApplicationContext().sendBroadcast(intent,null,BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()); }
        catch (RuntimeException ignored) {}
    }

    public static void replayAfterUnlock(Context c, long cycle) {
        if (!enabled(c) || Build.VERSION.SDK_INT < 34) return;
        Intent intent = new Intent(MiniBridgeContract.ACTION_SYSTEM_ISLAND).setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_COMMAND, MiniBridgeContract.COMMAND_UNLOCK_REPLAY);
        intent.putExtra(MiniBridgeContract.EXTRA_UNLOCK_CYCLE, cycle);
        try { c.getApplicationContext().sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()); }
        catch (RuntimeException ignored) {}
    }

    public static void syncGestureApps(Context c) {
        if (enabled(c)) send(c, MiniBridgeContract.COMMAND_GESTURE_CONFIG, null, null, null, null, null, null, false);
    }

    public static void syncSelectedApps(Context c) {
        if (enabled(c)) send(c, MiniBridgeContract.COMMAND_SELECTION, null, null, null, null, null, null, false);
    }

    public static void showMusic(Context c, String pkg, String title, String artist, byte[] artwork, boolean playing, boolean restore, PendingIntent sessionAction,
                                 long actions, java.util.ArrayList<String> customIds, java.util.ArrayList<String> customLabels, java.util.ArrayList<Integer> customIcons) {
        if (!enabled(c) || pkg == null || pkg.isEmpty()) return;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        PendingIntent action = sessionAction;
        if (action != null && !pkg.equals(action.getCreatorPackage())) action = null;
        if (action == null && launch != null) action = PendingIntent.getActivity(c, 3501, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        send(c, MiniBridgeContract.COMMAND_MUSIC, safe(title, 64), safe(artist, 180),
                artwork, action, MiniBridgeContract.MUSIC_KEY, null, restore, pkg, playing, actions, customIds, customLabels, customIcons, 0);
    }

    public static void wave(Context c, byte[] levels) {
        if (!enabled(c) || levels == null || levels.length != 5 || Build.VERSION.SDK_INT < 34) return;
        Intent intent = new Intent(MiniBridgeContract.ACTION_SYSTEM_ISLAND).setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_COMMAND, MiniBridgeContract.COMMAND_WAVE);
        intent.putExtra(MiniBridgeContract.EXTRA_WAVE_LEVELS, levels);
        try { c.getApplicationContext().sendBroadcast(intent, null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()); }
        catch (RuntimeException ignored) {}
    }

    public static void showCharge(Context c, int percent, boolean full) {
        if (!enabled(c)) return;
        String title = (full ? "已充满" : "充电中") + (percent < 0 ? "" : " " + percent + "%");
        send(c, MiniBridgeContract.COMMAND_CHARGE, title, title,
                null, null, MiniBridgeContract.CHARGE_KEY, null, false);
    }

    public static void test(Context c, int index, boolean remove) {
        if (!enabled(c) || index < 0 || index > 2) return;
        String key = "island:test:" + index;
        send(c, remove ? MiniBridgeContract.COMMAND_REMOVE : MiniBridgeContract.COMMAND_TEST,
                "模拟应用 " + (char) ('A' + index), "这是用于测试的模拟通知",
                null, null, key, null, false);
    }

    public static void removeState(Context c, String key) {
        if (enabled(c)) send(c, MiniBridgeContract.COMMAND_REMOVE, null, null, null, null, key, null, false);
    }

    /** Manual sample does not read any real chat. */
    public static void notifyMessage(Context c, String sourcePackage) {
        if (!enabled(c)) return;
        String cmd = commandForPackage(sourcePackage);
        if (cmd != null) demo(c, null);
    }

    /** Called only for real notifications from selected applications. */
    public static void notifyMessage(Context c, StatusBarNotification sbn) {
        notifyMessage(c, sbn, false);
    }

    /** Restore existing Android notifications compactly, without an alert. */
    public static void notifyMessage(Context c, StatusBarNotification sbn, boolean restore) {
        if (sbn == null || !enabled(c)) return;
        Notification notification = sbn.getNotification();
        String cmd = "com.android.incallui".equals(sbn.getPackageName()) && notification != null
                && Notification.CATEGORY_CALL.equals(notification.category) ? MiniBridgeContract.COMMAND_CALL : commandForPackage(sbn.getPackageName());
        if (cmd == null || notification == null) return;

        KeyguardManager keyguard = c.getSystemService(KeyguardManager.class);
        if (keyguard != null && (keyguard.isKeyguardLocked() || keyguard.isDeviceLocked())) return;

        String sender = null;
        String body = null;
        byte[] avatar = null;
        if (MusicPrefs.messagePreview(c)
                && notification.visibility != Notification.VISIBILITY_SECRET
                && notification.extras != null) {
            try {
                Bundle extras = notification.extras;
                sender = safe(extras.getCharSequence(Notification.EXTRA_TITLE), 64);
                body = safe(extras.getCharSequence(Notification.EXTRA_TEXT), 180);
                if (body.isEmpty()) {
                    body = safe(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), 180);
                }
                if (body.isEmpty()) {
                    CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if (lines != null && lines.length > 0) {
                        body = safe(lines[lines.length - 1], 180);
                    }
                }
                if (body.isEmpty()) {
                    Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                    if (messages != null) {
                        for (int i = messages.length - 1; i >= 0; i--) {
                            if (messages[i] instanceof Bundle) {
                                body = safe(((Bundle) messages[i]).getCharSequence("text"), 180);
                                if (!body.isEmpty()) break;
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                sender = null;
                body = null;
            }
            avatar = NotificationAvatarExtractor.fromNotification(c, notification);
        }
        PendingIntent original = notification.contentIntent;
        // Only forward the genuine app-issued click capability. Do not forge a
        // chat Intent; some notifications legitimately lack a click target.
        if (original != null) {
            try {
                if (!sbn.getPackageName().equals(original.getCreatorPackage())) {
                    original = null;
                }
            } catch (RuntimeException ignored) { original = null; }
        }
        String key = sbn.getKey();
        send(c, cmd, emptyToNull(sender), emptyToNull(body), avatar, original,
                key == null ? null : key.substring(0, Math.min(500, key.length())), null, restore, null, false, 0L, null, null, null,
                MiniBridgeContract.COMMAND_CALL.equals(cmd) ? callCapabilities(notification) : 0);
    }

    /**
     * Original removal callbacks govern persistence. Never remove the original
     * notification from Android, only the SystemUI island entry with this key.
     */
    public static void notificationRemoved(Context c, StatusBarNotification sbn) {
        if (sbn == null || !enabled(c)) return;
        if (commandForPackage(sbn.getPackageName()) == null) return;
        String key = sbn.getKey();
        if (key == null || key.isEmpty()) return;
        send(c, MiniBridgeContract.COMMAND_REMOVE, null, null, null, null,
                key.substring(0, Math.min(500, key.length())), null, false);
    }

    private static int callCapabilities(Notification n) {
        int caps=0;
        if (n.actions == null) return 0;
        for (Notification.Action a : n.actions) {
            if (a == null || a.title == null || a.actionIntent == null) continue;
            try { if (!"com.android.incallui".equals(a.actionIntent.getCreatorPackage())) continue; }
            catch (RuntimeException e) { continue; }
            switch (CallActionPolicy.classify(a.title.toString())) {
                case ANSWER: caps |= 1; break; case REJECT: caps |= 2; break;
                case HANGUP: caps |= 4; break; case SPEAKER_ON: caps |= 8; break;
                case SPEAKER_OFF: caps |= 16; break; default: break;
            }
        }
        return caps;
    }
    private static String commandForPackage(String source) {
        if (MessageCapsulePublisher.WECHAT.equals(source))
            return MiniBridgeContract.COMMAND_WECHAT;
        if (MessageCapsulePublisher.QQ.equals(source))
            return MiniBridgeContract.COMMAND_QQ;
        return source == null || source.isEmpty() || MiniBridgeContract.APP.equals(source)
                ? null : MiniBridgeContract.COMMAND_OTHER;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Remove control, formatting and bidi override characters before UI transfer. */
    private static String safe(CharSequence input, int max) {
        if (input == null) return "";
        String value = input.toString().replaceAll("[\\p{Cntrl}\\p{Cf}]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (value.length() > max) value = value.substring(0, max);
        return value;
    }

    private static void send(Context c, String command, String sender,
                             String message, byte[] avatar, PendingIntent action,
                             String sourceKey, MiniBridgeClient.Callback callback, boolean restore) {
        send(c, command, sender, message, avatar, action, sourceKey, callback, restore, null, false, 0L, null, null, null, 0);
    }

    private static void send(Context c, String command, String sender,
                             String message, byte[] avatar, PendingIntent action,
                             String sourceKey, MiniBridgeClient.Callback callback, boolean restore,
                             String mediaPackage, boolean mediaPlaying, long mediaActions,
                             java.util.ArrayList<String> customIds, java.util.ArrayList<String> customLabels, java.util.ArrayList<Integer> customIcons, int callCaps) {
        if (Build.VERSION.SDK_INT < 34) {
            if (callback != null) callback.onResult("需要 Android 14 及以上系统");
            return;
        }
        if (!MiniBridgeContract.COMMAND_DEMO.equals(command)
                && !MiniBridgeContract.COMMAND_WECHAT.equals(command)
                && !MiniBridgeContract.COMMAND_QQ.equals(command)
                && !MiniBridgeContract.COMMAND_OTHER.equals(command)
                && !MiniBridgeContract.COMMAND_CALL.equals(command)
                && !MiniBridgeContract.COMMAND_PRIORITY.equals(command)
                && !MiniBridgeContract.COMMAND_GESTURE_CONFIG.equals(command)
                && !MiniBridgeContract.COMMAND_WAVE_STYLE.equals(command)
                && !MiniBridgeContract.COMMAND_SELECTION.equals(command)
                && !MiniBridgeContract.COMMAND_MUSIC.equals(command)
                && !MiniBridgeContract.COMMAND_CHARGE.equals(command)
                && !MiniBridgeContract.COMMAND_TEST.equals(command)
                && !MiniBridgeContract.COMMAND_HIDE.equals(command)
                && !MiniBridgeContract.COMMAND_REMOVE.equals(command)) return;
        Context app = c.getApplicationContext();
        Intent intent = new Intent(MiniBridgeContract.ACTION_SYSTEM_ISLAND);
        intent.setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_COMMAND, command);
        if (MiniBridgeContract.COMMAND_CALL.equals(command)) { intent.putExtra(MiniBridgeContract.EXTRA_CALL_CAPS, callCaps); intent.putExtra(MiniBridgeContract.EXTRA_CALL_SIZES, CallSizePrefs.get(c)); }
        intent.putExtra(MiniBridgeContract.EXTRA_WAVE_STYLE, WaveStylePrefs.get(c));
        intent.putExtra(MiniBridgeContract.EXTRA_PANEL_SAFE_TOP, SystemUiIslandPrefs.panelSafeTop(c));
        intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_ICON_MODE, MusicPrefs.mediaIconMode(c));
        intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_GESTURE_ENABLED, GestureAppPrefs.mediaEnabled(c));
        intent.putStringArrayListExtra(MiniBridgeContract.EXTRA_GESTURE_APPS,
                new java.util.ArrayList<>(GestureAppPrefs.get(c)));
        if (MiniBridgeContract.COMMAND_MUSIC.equals(command)) {
            intent.putExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE, mediaPackage);
            intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_PLAYING, mediaPlaying);
            intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_ACTIONS, mediaActions);
            intent.putStringArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_IDS, customIds);
            intent.putStringArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_LABELS, customLabels);
            intent.putIntegerArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_ICONS, customIcons);
        }
        intent.putStringArrayListExtra(MiniBridgeContract.EXTRA_APP_PRIORITY, PriorityPrefs.get(c));
        if (MiniBridgeContract.COMMAND_SELECTION.equals(command))
            intent.putStringArrayListExtra(MiniBridgeContract.EXTRA_SELECTED_APPS,
                    new java.util.ArrayList<>(AppSelectionPrefs.get(c)));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_RESTORE, restore);
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_SWIPE_MODE,
                SystemUiIslandPrefs.swipeMode(c));
        if (sourceKey != null && !sourceKey.isEmpty())
            intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY, sourceKey);
        String dimensionPackage = MiniBridgeContract.COMMAND_CHARGE.equals(command) ? "island.charge"
                : MiniBridgeContract.COMMAND_MUSIC.equals(command) ? "island.media" : mediaPackage;
        if (dimensionPackage == null && sourceKey != null && sourceKey.contains("|")) {
            int first = sourceKey.indexOf('|'), second = sourceKey.indexOf('|', first + 1);
            if (second > first) dimensionPackage = sourceKey.substring(first + 1, second);
        }
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_W, AppSizePrefs.width(c, dimensionPackage));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_H, AppSizePrefs.height(c, dimensionPackage));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_TOP, SystemUiIslandPrefs.top(c));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_W, SystemUiIslandPrefs.panelWidth(c));
        intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_H, SystemUiIslandPrefs.panelHeight(c));
        // Preview still requires the explicit existing user opt-in. Either the
        // sender or the body can legitimately be missing in a source notification.
        if (sender != null) intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_TITLE, sender);
        if (message != null) intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_BODY, message);
        if (action != null && (MiniBridgeContract.COMMAND_WECHAT.equals(command)
                || MiniBridgeContract.COMMAND_QQ.equals(command)
                || MiniBridgeContract.COMMAND_OTHER.equals(command) || MiniBridgeContract.COMMAND_CALL.equals(command))) {
            intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_CHAT_ACTION, action);
        }
        if (action != null && MiniBridgeContract.COMMAND_MUSIC.equals(command))
            intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_CHAT_ACTION, action);
        if (avatar != null && avatar.length > 0
                && avatar.length <= NotificationAvatarExtractor.MAX_BYTES) {
            intent.putExtra(MiniBridgeContract.EXTRA_ISLAND_AVATAR, avatar);
        }

        Handler h = new Handler(Looper.getMainLooper());
        AtomicBoolean done = new AtomicBoolean(false);
        if (callback != null) {
            ResultReceiver reply = new ResultReceiver(h) {
                @Override protected void onReceiveResult(int code, Bundle data) {
                    if (!done.compareAndSet(false, true)) return;
                    String report = data == null ? null
                            : data.getString(MiniBridgeContract.EXTRA_REPORT);
                    callback.onResult(report == null ? "SystemUI 无应答内容" : report);
                }
            };
            intent.putExtra(MiniBridgeContract.EXTRA_REPLY, reply);
        }
        try {
            BroadcastOptions options = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true);
            app.sendBroadcast(intent, null, options.toBundle());
        } catch (RuntimeException failure) {
            if (callback != null && done.compareAndSet(false, true)) {
                callback.onResult("发送 SystemUI 请求失败：" + failure.getClass().getSimpleName());
            }
            return;
        }
        if (callback != null) {
            h.postDelayed(() -> {
                if (done.compareAndSet(false, true)) {
                    callback.onResult("8 秒内未收到 SystemUI 回应；请在 LSPosed 检查"
                            + " SystemUI 作用域并重启。日志：SystemIsland v0.28");
                }
            }, 8000L);
        }
    }
}
