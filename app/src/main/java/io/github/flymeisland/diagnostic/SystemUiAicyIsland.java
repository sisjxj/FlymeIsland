package io.github.flymeisland.diagnostic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.ActivityOptions;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Method;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.StaticLayout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import de.robv.android.xposed.XposedBridge;

/**
 * An independently drawn, system-level floating island hosted INSIDE the
 * com.android.systemui process by LSPosed. Not a Flyme LiveNotification capsule
 * and not a TYPE_APPLICATION_OVERLAY in the normal app process.
 *
 * User-initiated demo or explicitly enabled generic WeChat/QQ notification only.
 * All incoming commands are UID-authenticated and strictly whitelisted.
 * Only authenticated, whitelisted notification previews and their original
 * creator-validated PendingIntent can be used; content is never logged.
 *
 * Fail closed: if system window type is rejected, no normal overlay fallback.
 */
public final class SystemUiAicyIsland {
    private static final String TAG = "[FlymeIsland/SystemIsland v0.34-r2] ";
    private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int TYPE_VOICE_INTERACTION_STARTING = 2033;
    private static final int[] WINDOW_TYPES = {
            TYPE_STATUS_BAR_SUB_PANEL, TYPE_VOICE_INTERACTION_STARTING
    };
    private static final long EXPANDED_MS = 5100L;
    private static final long DEMO_PILL_MS = 8000L;
    // 580ms intentional idle gap in v0.28 caused the visibly stalled transition.
    // Keep the compact badge visible for only a few frames, then morph immediately.
    private static final long PILL_INTRO_MS = 110L;
    private static final long MORPH_MS = 360L;
    private static final long ROTATE_MS = 6000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable COLLAPSE = SystemUiAicyIsland::collapseAndRestoreMusic;
    private static final Runnable HIDE = SystemUiAicyIsland::dismissIsland;
    private static final Runnable EXPAND = () -> morph(true);
    private static final Runnable ROTATE = SystemUiAicyIsland::showNextCompact;

    // Only in-process, only authenticated source notification entries. Clearing the
    // last source clears the island; no artificial timeout for real messages.
    private static final LinkedHashMap<String, Notice> ACTIVE = new LinkedHashMap<>();
    // A user swipe or close wins over a later unlock restore. A genuinely
    // new notification event for the same key may display it again.
    private static final Set<String> SUPPRESSED_KEYS = new HashSet<>();
    private static Set<String> gestureApps = new HashSet<>();
    private static boolean mediaGestureEnabled;
    private static final int MAX_NOTICES = 32;
    private static boolean demoOnly;
    private static String visibleKey;
    private static ArrayList<String> appPriority = new ArrayList<>(Arrays.asList(
            MessageCapsulePublisher.WECHAT, MessageCapsulePublisher.QQ));
    private static int swipeMode = SystemUiIslandPrefs.SWIPE_RIGHT;

    private static final class Notice {
        final String key, command, sender, body, sourcePackage;
        final byte[] avatar;
        final PendingIntent action;
        int pillWidth, pillHeight;
        Notice(String key, String command, String sender, String body,
               byte[] avatar, PendingIntent action, String sourcePackage, int pillWidth, int pillHeight) {
            this.pillWidth = pillWidth; this.pillHeight = pillHeight;
            this.key = key; this.command = command; this.sender = sender;
            this.body = body; this.avatar = avatar; this.action = action;
            this.sourcePackage = sourcePackage;
        }
    }

    private static Context context;
    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static FrameLayout root;
    private static LinearLayout fullContent;
    private static LinearLayout pillContent, mediaControls;
    private static TextView fullTitle, fullSubtitle, pillTitle, chargeLabel, messageText;
    private static ImageView pillIcon, contactAvatar;
    private static MediaWaveView mediaWave;
    private static int[] waveStyle = WaveStylePrefs.DEFAULTS.clone();
    private static ValueAnimator coverAnimator;
    private static boolean mediaPlaying;
    private static long mediaActions;
    private static ArrayList<String> mediaCustomIds = new ArrayList<>(), mediaCustomLabels = new ArrayList<>();
    private static ArrayList<Integer> mediaCustomIcons = new ArrayList<>();
    private static int panelHeightLimit;
    private static int baseBigW, basePanelHeightLimit;
    private static int callCaps;
    private static int[] callSizes = CallSizePrefs.DEFAULTS.clone();
    private static LinearLayout callMedium;
    private static boolean callMediumVisible;
    private static boolean previousCallConnected;
    private static boolean isCurrentCall() { Notice n=ACTIVE.get(visibleKey); return n!=null && MiniBridgeContract.COMMAND_CALL.equals(n.command); }
    private static boolean callConnected() { return (callCaps & (4|8|16)) != 0; }
    private static int requestedPanelSafeTop = SystemUiIslandPrefs.PANEL_SAFE_TOP_DEFAULT;
    private static BroadcastReceiver screenOff;
    private static ValueAnimator running;
    private static ViewTreeObserver.OnPreDrawListener pendingFrame;
    private static Runnable pendingExit;
    private static int generation;
    private static boolean isExpanded;
    private static long lastUnlockCycle = -1L;
    private static long pendingUnlockCycle = -1L;
    private static boolean mediaIconMode = true;
    private static boolean closedForScreenOff;
    private static int bigW, bigH, bigY, smallW, smallH, smallY;
    private static float radius;
    private static int liveWindowType;
    private static boolean launchInProgress;
    private static boolean aicyShowing;
    private static PendingIntent chatAction;
    private static String chatPackage;
    private static long actionReceivedAt;

    private SystemUiAicyIsland() {}

    public static void onRequest(Context c, BroadcastReceiver receiver, Intent intent) {
        if (intent == null
                || !MiniBridgeContract.ACTION_SYSTEM_ISLAND.equals(intent.getAction()))
            return;
        if (!MiniBridgeContract.SYSTEM_UI.equals(c.getPackageName())) return;

        try {
            int uid = receiver.getSentFromUid();
            int expected = c.getPackageManager().getPackageUid(MiniBridgeContract.APP, 0);
            if (uid == Process.INVALID_UID || uid != expected) {
                log("rejected sender UID");
                return;
            }
            String sender = receiver.getSentFromPackage();
            if (sender != null && !MiniBridgeContract.APP.equals(sender)) {
                log("rejected sender package");
                return;
            }
            long sent = intent.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1L);
            long age = SystemClock.elapsedRealtime() - sent;
            if (sent < 0 || age < 0 || age > 10000L) {
                log("rejected expired request");
                return;
            }
            String cmd = intent.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_COMMAND);
            if (!MiniBridgeContract.COMMAND_UNLOCK_REPLAY.equals(cmd)
                    && !MiniBridgeContract.COMMAND_DEMO.equals(cmd)
                    && !MiniBridgeContract.COMMAND_WECHAT.equals(cmd)
                    && !MiniBridgeContract.COMMAND_QQ.equals(cmd)
                    && !MiniBridgeContract.COMMAND_OTHER.equals(cmd)
                    && !MiniBridgeContract.COMMAND_CALL.equals(cmd)
                    && !MiniBridgeContract.COMMAND_PRIORITY.equals(cmd)
                    && !MiniBridgeContract.COMMAND_GESTURE_CONFIG.equals(cmd)
                    && !MiniBridgeContract.COMMAND_APP_SIZE.equals(cmd)
                    && !MiniBridgeContract.COMMAND_WAVE_STYLE.equals(cmd)
                    && !MiniBridgeContract.COMMAND_SELECTION.equals(cmd)
                    && !MiniBridgeContract.COMMAND_MUSIC.equals(cmd)
                    && !MiniBridgeContract.COMMAND_WAVE.equals(cmd)
                    && !MiniBridgeContract.COMMAND_CHARGE.equals(cmd)
                    && !MiniBridgeContract.COMMAND_TEST.equals(cmd)
                    && !MiniBridgeContract.COMMAND_HIDE.equals(cmd)
                    && !MiniBridgeContract.COMMAND_REMOVE.equals(cmd)) {
                log("invalid command rejected");
                return;
            }
            if (MiniBridgeContract.COMMAND_UNLOCK_REPLAY.equals(cmd)) {
                long cycle = intent.getLongExtra(MiniBridgeContract.EXTRA_UNLOCK_CYCLE, -1L);
                long now = SystemClock.elapsedRealtime();
                android.os.PowerManager power = c.getSystemService(android.os.PowerManager.class);
                Notice current = ACTIVE.get(visibleKey);
                android.util.Log.i("FlymeIslandUnlock", "replay request attached=" + (root != null) + " expanded=" + isExpanded + " aicy=" + aicyShowing + " active=" + ACTIVE.size());
                if (root == null && cycle > lastUnlockCycle && cycle <= now && now - cycle <= 10000L
                        && !isLocked(c) && power != null && power.isInteractive()) {
                    pendingUnlockCycle = cycle;
                    return;
                }
                if (!UnlockReplayPolicy.shouldReplay(cycle, lastUnlockCycle, now,
                        root instanceof CameraIslandLayout && current != null,
                        isLocked(c) || power == null || !power.isInteractive(),
                        isExpanded, aicyShowing || pendingExit != null || launchInProgress,
                        running != null || pendingFrame != null,
                        current != null && MiniBridgeContract.COMMAND_CHARGE.equals(current.command))) return;
                lastUnlockCycle = cycle;
                pendingUnlockCycle = -1L;
                MAIN.removeCallbacks(EXPAND);
                MAIN.removeCallbacks(COLLAPSE);
                MAIN.removeCallbacks(ROTATE);
                MAIN.removeCallbacks(HIDE);
                animateCompactArrival();
                return;
            }
            if (MiniBridgeContract.COMMAND_WAVE.equals(cmd)) {
                byte[] levels = intent.getByteArrayExtra(MiniBridgeContract.EXTRA_WAVE_LEVELS);
                if (levels != null && levels.length == 5 && mediaWave != null
                        && ACTIVE.containsKey(MiniBridgeContract.MUSIC_KEY)) mediaWave.setLevels(levels);
                return;
            }
            if (MiniBridgeContract.COMMAND_APP_SIZE.equals(cmd)) {
                String pkg = intent.getStringExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE);
                if (pkg == null || pkg.length() > 200 || sourcePackageFromKey("0|" + pkg + "|0").isEmpty()) return;
                int w = SystemUiIslandPrefs.clamp(intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_W, SystemUiIslandPrefs.PILL_W_DEFAULT),SystemUiIslandPrefs.PILL_W_MIN,SystemUiIslandPrefs.PILL_W_MAX);
                int h = SystemUiIslandPrefs.clamp(intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_H, SystemUiIslandPrefs.PILL_H_DEFAULT),SystemUiIslandPrefs.PILL_H_MIN,SystemUiIslandPrefs.PILL_H_MAX);
                for (Notice notice : ACTIVE.values()) if (pkg.equals(sizeSource(notice))) { notice.pillWidth=w; notice.pillHeight=h; }
                Notice current = ACTIVE.get(visibleKey);
                if (current != null && pkg.equals(sizeSource(current))) bindNotice(current);
                return;
            }
            if (intent.hasExtra(MiniBridgeContract.EXTRA_MEDIA_ICON_MODE)) {
                boolean newMode = intent.getBooleanExtra(MiniBridgeContract.EXTRA_MEDIA_ICON_MODE, true);
                if (mediaIconMode != newMode) { mediaIconMode = newMode; refreshMediaControls(); }
            }
            if (intent.hasExtra(MiniBridgeContract.EXTRA_PANEL_SAFE_TOP)) {
                int safeTop = SystemUiIslandPrefs.clamp(intent.getIntExtra(MiniBridgeContract.EXTRA_PANEL_SAFE_TOP,
                        SystemUiIslandPrefs.PANEL_SAFE_TOP_DEFAULT), SystemUiIslandPrefs.PANEL_SAFE_TOP_MIN, SystemUiIslandPrefs.PANEL_SAFE_TOP_MAX);
                if (requestedPanelSafeTop != safeTop) {
                    requestedPanelSafeTop = safeTop;
                    if (fullContent != null && messageText != null && root != null) {
                        calculateExpandedHeight(messageText.getText().toString());
                        if (isExpanded && pendingExit == null && !launchInProgress) morph(true);
                    }
                }
            }
            if (MiniBridgeContract.COMMAND_WAVE_STYLE.equals(cmd) && root != null
                    && intent.hasExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_H)) {
                int limit = dp(SystemUiIslandPrefs.clamp(intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_H,
                        SystemUiIslandPrefs.PANEL_H_DEFAULT), SystemUiIslandPrefs.PANEL_H_MIN, SystemUiIslandPrefs.PANEL_H_MAX));
                if (limit != panelHeightLimit) {
                    panelHeightLimit = limit;
                    if (messageText != null) calculateExpandedHeight(messageText.getText().toString());
                    if (isExpanded && pendingExit == null && !launchInProgress) morph(true);
                }
            }
            int[] suppliedStyle = intent.getIntArrayExtra(MiniBridgeContract.EXTRA_WAVE_STYLE);
            if (suppliedStyle != null && suppliedStyle.length == WaveStylePrefs.DEFAULTS.length) {
                int[] normalized = new int[suppliedStyle.length];
                for (int i=0;i<normalized.length;i++) normalized[i] = SystemUiIslandPrefs.clamp(suppliedStyle[i],WaveStylePrefs.MIN[i],WaveStylePrefs.MAX[i]);
                if (!Arrays.equals(waveStyle, normalized)) { waveStyle = normalized; applyWaveStyle(); }
            }
            if (MiniBridgeContract.COMMAND_WAVE_STYLE.equals(cmd)) { refreshMediaAppearance(); return; }
            if (intent.hasExtra(MiniBridgeContract.EXTRA_MEDIA_GESTURE_ENABLED))
                mediaGestureEnabled = intent.getBooleanExtra(MiniBridgeContract.EXTRA_MEDIA_GESTURE_ENABLED, false);
            ArrayList<String> gestureSelection = intent.getStringArrayListExtra(MiniBridgeContract.EXTRA_GESTURE_APPS);
            if (gestureSelection != null && gestureSelection.size() <= 2048)
                gestureApps = new HashSet<>(gestureSelection);
            if (MiniBridgeContract.COMMAND_GESTURE_CONFIG.equals(cmd)) return;
            boolean restore = intent.getBooleanExtra(
                    MiniBridgeContract.EXTRA_ISLAND_RESTORE, false);
            String sourceKey = intent.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY);
            if (sourceKey != null && sourceKey.length() > 500) sourceKey = null;
            ArrayList<String> priority = intent.getStringArrayListExtra(MiniBridgeContract.EXTRA_APP_PRIORITY);
            if (priority != null && priority.size() <= 64) appPriority = new ArrayList<>(priority);
            if (MiniBridgeContract.COMMAND_PRIORITY.equals(cmd)) { updateSide(); return; }
            if (MiniBridgeContract.COMMAND_SELECTION.equals(cmd)) {
                ArrayList<String> selected = intent.getStringArrayListExtra(MiniBridgeContract.EXTRA_SELECTED_APPS);
                if (selected == null || selected.size() > 2048) return;
                Set<String> allowed = new HashSet<>(selected);
                for (Notice notice : new ArrayList<>(ACTIVE.values())) {
                    if ((MiniBridgeContract.COMMAND_OTHER.equals(notice.command)
                            || MiniBridgeContract.COMMAND_WECHAT.equals(notice.command)
                            || MiniBridgeContract.COMMAND_QQ.equals(notice.command))
                            && !allowed.contains(notice.sourcePackage)) removeSource(notice.key);
                }
                return;
            }
            swipeMode = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_SWIPE_MODE,
                            SystemUiIslandPrefs.SWIPE_RIGHT),
                    SystemUiIslandPrefs.SWIPE_OFF, SystemUiIslandPrefs.SWIPE_BOTH);
            if (MiniBridgeContract.COMMAND_REMOVE.equals(cmd)) {
                if (sourceKey != null) removeSource(sourceKey);
                return;
            }
            int pillW = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_W,
                            SystemUiIslandPrefs.PILL_W_DEFAULT),
                    SystemUiIslandPrefs.PILL_W_MIN, SystemUiIslandPrefs.PILL_W_MAX);
            int pillH = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PILL_H,
                            SystemUiIslandPrefs.PILL_H_DEFAULT),
                    SystemUiIslandPrefs.PILL_H_MIN, SystemUiIslandPrefs.PILL_H_MAX);
            int top = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_TOP,
                            SystemUiIslandPrefs.TOP_DEFAULT),
                    SystemUiIslandPrefs.TOP_MIN, SystemUiIslandPrefs.TOP_MAX);
            int panelW = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_W,
                            SystemUiIslandPrefs.PANEL_W_DEFAULT),
                    SystemUiIslandPrefs.PANEL_W_MIN, SystemUiIslandPrefs.PANEL_W_MAX);
            int panelH = SystemUiIslandPrefs.clamp(
                    intent.getIntExtra(MiniBridgeContract.EXTRA_ISLAND_PANEL_H,
                            SystemUiIslandPrefs.PANEL_H_DEFAULT),
                    SystemUiIslandPrefs.PANEL_H_MIN, SystemUiIslandPrefs.PANEL_H_MAX);

            String senderTitle = scrub(intent.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_TITLE), 64);
            String previewBody = scrub(intent.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_BODY), 180);
            PendingIntent sourceAction = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(MiniBridgeContract.EXTRA_ISLAND_CHAT_ACTION,
                            PendingIntent.class)
                    : intent.getParcelableExtra(MiniBridgeContract.EXTRA_ISLAND_CHAT_ACTION);
            // Never accept arbitrary PendingIntent creators or capabilities for demos.
            String requiredSource = MiniBridgeContract.COMMAND_WECHAT.equals(cmd)
                    ? MessageCapsulePublisher.WECHAT
                    : MiniBridgeContract.COMMAND_QQ.equals(cmd)
                    ? MessageCapsulePublisher.QQ
                    : (MiniBridgeContract.COMMAND_OTHER.equals(cmd) || MiniBridgeContract.COMMAND_CALL.equals(cmd)) ? sourcePackageFromKey(sourceKey)
                    : MiniBridgeContract.COMMAND_MUSIC.equals(cmd)
                    ? sourcePackageFromKey("0|" + intent.getStringExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE) + "|0")
                    : MiniBridgeContract.COMMAND_CHARGE.equals(cmd) ? "island.charge"
                    : MiniBridgeContract.COMMAND_TEST.equals(cmd) ? testPackage(sourceKey) : "";
            if (MiniBridgeContract.COMMAND_OTHER.equals(cmd) && requiredSource.isEmpty()) return;
            if (MiniBridgeContract.COMMAND_CALL.equals(cmd) && !"com.android.incallui".equals(requiredSource)) return;
            if (MiniBridgeContract.COMMAND_CALL.equals(cmd)) {
                previousCallConnected = callConnected();
                callCaps = intent.getIntExtra(MiniBridgeContract.EXTRA_CALL_CAPS, 0) & 31;
                int[] sizes=intent.getIntArrayExtra(MiniBridgeContract.EXTRA_CALL_SIZES);
                if (sizes != null && sizes.length == 6) for (int i=0;i<6;i++) callSizes[i]=SystemUiIslandPrefs.clamp(sizes[i],CallSizePrefs.MIN[i],CallSizePrefs.MAX[i]);
            }
            if (MiniBridgeContract.COMMAND_MUSIC.equals(cmd)
                    && (!MiniBridgeContract.MUSIC_KEY.equals(sourceKey) || requiredSource.isEmpty())) return;
            if (MiniBridgeContract.COMMAND_MUSIC.equals(cmd)) {
                mediaPlaying = intent.getBooleanExtra(MiniBridgeContract.EXTRA_MEDIA_PLAYING, false);
                mediaActions = intent.getLongExtra(MiniBridgeContract.EXTRA_MEDIA_ACTIONS, 0L);
                ArrayList<String> ids=intent.getStringArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_IDS);
                ArrayList<String> labels=intent.getStringArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_LABELS);
                ArrayList<Integer> icons = intent.getIntegerArrayListExtra(MiniBridgeContract.EXTRA_MEDIA_CUSTOM_ICONS);
                mediaCustomIds=new ArrayList<>(); mediaCustomLabels=new ArrayList<>(); mediaCustomIcons=new ArrayList<>();
                if(ids!=null && labels!=null && ids.size()==labels.size() && ids.size()<=2)
                    for(int i=0;i<ids.size();i++) if(ids.get(i)!=null && ids.get(i).length()<=180) {
                        mediaCustomIds.add(ids.get(i));mediaCustomLabels.add(scrub(labels.get(i),12));
                        mediaCustomIcons.add(icons != null && icons.size() == ids.size() && icons.get(i) != null ? icons.get(i) : 0);
                    }
            }
            if (MiniBridgeContract.COMMAND_CHARGE.equals(cmd) && !MiniBridgeContract.CHARGE_KEY.equals(sourceKey)) return;
            if (MiniBridgeContract.COMMAND_TEST.equals(cmd) && requiredSource.isEmpty()) return;
            if (sourceAction != null) {
                try {
                    String creator = sourceAction.getCreatorPackage();
                    if (!(MiniBridgeContract.COMMAND_MUSIC.equals(cmd)
                            ? (MiniBridgeContract.APP.equals(creator) || requiredSource.equals(creator)) : requiredSource.equals(creator)))
                        sourceAction = null;
                } catch (RuntimeException error) {
                    sourceAction = null;
                }
            }
            byte[] avatar = intent.getByteArrayExtra(MiniBridgeContract.EXTRA_ISLAND_AVATAR);
            if (avatar != null && avatar.length > NotificationAvatarExtractor.MAX_BYTES)
                avatar = null;
            // Only real, specifically whitelisted notification commands can carry previews.
            if (MiniBridgeContract.COMMAND_DEMO.equals(cmd)
                    || MiniBridgeContract.COMMAND_HIDE.equals(cmd)) {
                senderTitle = "";
                previewBody = "";
                avatar = null;
                sourceAction = null;
            }
            String outcome;
            if (MiniBridgeContract.COMMAND_HIDE.equals(cmd)) {
                SUPPRESSED_KEYS.clear();
                animateExit(SystemUiAicyIsland::close);
                outcome = "已请求关闭 SystemUI 顶部窗口";
            } else {
                outcome = show(c.getApplicationContext(), cmd,
                        senderTitle, previewBody, avatar, sourceAction, requiredSource,
                        sourceKey, pillW, pillH, top, panelW, panelH, restore);
            }
            if (MiniBridgeContract.COMMAND_DEMO.equals(cmd)
                    || MiniBridgeContract.COMMAND_HIDE.equals(cmd)) {
                ResultReceiver reply = Build.VERSION.SDK_INT >= 33
                        ? intent.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY,
                                ResultReceiver.class)
                        : intent.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY);
                if (reply != null) {
                    Bundle b = new Bundle();
                    b.putString(MiniBridgeContract.EXTRA_REPORT, outcome);
                    try { reply.send(0, b); } catch (RuntimeException ignored) {}
                }
            }
        } catch (Throwable error) {
            log("request exception " + error.getClass().getSimpleName());
        }
    }

    private static String show(Context app, String command,
                               String senderTitle, String previewBody, byte[] avatar,
                               PendingIntent sourceAction, String sourcePackage, String sourceKey,
                               int pillWidth, int pillHeight, int top,
                               int panelWidth, int panelHeight, boolean restore) {
        if (Build.VERSION.SDK_INT < 34) return "只支持 Android 14+";
        if (!MiniBridgeContract.SYSTEM_UI.equals(app.getPackageName()))
            return "拒绝：不在 SystemUI 内";
        if (isLocked(app)) {
            close();
            return "锁屏，已拒绝显示";
        }
        if (app.getPackageManager().checkPermission(
                "android.permission.INTERNAL_SYSTEM_WINDOW",
                MiniBridgeContract.SYSTEM_UI) != PackageManager.PERMISSION_GRANTED) {
            return "SystemUI 未获 INTERNAL_SYSTEM_WINDOW；未尝试系统窗口";
        }

        String title;
        String shortName;
        switch (command) {
            case MiniBridgeContract.COMMAND_WECHAT:
                title = "微信新消息";
                shortName = "微信";
                break;
            case MiniBridgeContract.COMMAND_QQ:
                title = "QQ 新消息";
                shortName = "QQ";
                break;
            case MiniBridgeContract.COMMAND_OTHER:
                shortName = appLabel(app, sourcePackage);
                title = shortName + "新通知";
                break;
            case MiniBridgeContract.COMMAND_CALL:
                title = "来电"; shortName = "电话"; break;
            case MiniBridgeContract.COMMAND_MUSIC:
                shortName = appLabel(app, sourcePackage);
                title = shortName + " · 媒体播放";
                break;
            case MiniBridgeContract.COMMAND_CHARGE:
                title = "充电状态";
                shortName = "充电";
                break;
            case MiniBridgeContract.COMMAND_TEST:
                title = appLabel(app, sourcePackage);
                shortName = title;
                break;
            default:
                title = "系统级浮窗演示";
                shortName = "演示";
                break;
        }

        boolean demo = MiniBridgeContract.COMMAND_DEMO.equals(command);
        boolean existingCall = MiniBridgeContract.COMMAND_CALL.equals(command) && ACTIVE.containsKey(sourceKey);
        boolean existingMusic = MiniBridgeContract.COMMAND_MUSIC.equals(command) && ACTIVE.containsKey(sourceKey);
        boolean existingCharge = MiniBridgeContract.COMMAND_CHARGE.equals(command) && ACTIVE.containsKey(sourceKey);
        if (!demo) {
            if (sourceKey == null || sourceKey.isEmpty()) return "缺少通知来源标识";
            if (restore && SUPPRESSED_KEYS.contains(sourceKey))
                return "用户已隐藏此通知对应的小岛";
            if (!restore) SUPPRESSED_KEYS.remove(sourceKey);
            // Re-posting same key is an update, not a second active notification.
            ACTIVE.remove(sourceKey);
            ACTIVE.put(sourceKey, new Notice(sourceKey, command, senderTitle, previewBody,
                    avatar, sourceAction, sourcePackage, pillWidth, pillHeight));
            while (ACTIVE.size() > MAX_NOTICES) {
                String oldest = null;
                for (String key : ACTIVE.keySet()) {
                    if (!key.equals(visibleKey)) { oldest = key; break; }
                }
                if (oldest == null) break;
                ACTIVE.remove(oldest);
            }
        } else {
            ACTIVE.clear();
        }
        if (launchInProgress || pendingExit != null) return "退场中，已暂存新消息";
        demoOnly = demo;
        // Demo uses a synthetic, non-chat sample. Real content is never logged.
        String cleanedSender = senderTitle.replaceFirst("^(微信|QQ)\\s*[·•:：]\\s*\\d+\\s*$", "");
        String heading = !cleanedSender.isEmpty() ? cleanedSender : title;
        String detail = !previewBody.isEmpty() ? cleanMessage(previewBody)
                : (MiniBridgeContract.COMMAND_DEMO.equals(command)
                    ? "在下方设置调整胶囊尺寸和顶部位置"
                    : "新消息 · 通知未提供正文或预览未开启");
        String smallText = demo ? "演示中"
                : MiniBridgeContract.COMMAND_CALL.equals(command) ? (callConnected() ? "正在通话" : "来电")
                : (MiniBridgeContract.COMMAND_MUSIC.equals(command)
                   || MiniBridgeContract.COMMAND_CHARGE.equals(command)) ? heading : "新消息";

        // Restoring many notifications after unlock must only populate the
        // registry, not repeatedly replace/restart the visible island.
        if (restore && !MiniBridgeContract.COMMAND_MUSIC.equals(command) && root != null && lp != null) {
            if (visibleKey == null) bindNotice(ACTIVE.get(sourceKey));
            updateSide();
            if (!isExpanded) schedule(false);
            return "已静默同步有效通知";
        }

        // Aicy has priority: preserve/update queued messages silently and
        // do not animate another window across the assistant's island.
        if (aicyShowing && root != null && lp != null) {
            updateSide();
            return "Aicy 显示中，已暂存新消息";
        }

        // Keep the active call in focus while queuing all other sources.
        Notice visible = ACTIVE.get(visibleKey);
        if (root != null && visible != null && MiniBridgeContract.COMMAND_CALL.equals(visible.command)
                && !MiniBridgeContract.COMMAND_CALL.equals(command)) {
            updateSide();
            return "通话优先，其他来源已暂存";
        }
        // Charging is compact-only: never schedule the expanded message card.
        if (root != null && MiniBridgeContract.COMMAND_CHARGE.equals(command)) {
            if (!existingCharge || sourceKey.equals(visibleKey)) {
                bindNotice(ACTIVE.get(sourceKey));
                updateSide();
                morph(false);
            } else updateSide();
            return "充电状态已短暂显示";
        }

        if (root != null && MiniBridgeContract.COMMAND_CALL.equals(command)) {
            bindNotice(ACTIVE.get(sourceKey));
            updateSide();
            if (callConnected() && (!existingCall || !previousCallConnected)) showCallMedium();
            else if (!callConnected() && !existingCall) animatePillFirst();
            return "来电已显示";
        }
        MediaRouting.Route route = MediaRouting.choose(root != null, demo,
                MiniBridgeContract.COMMAND_MUSIC.equals(command), existingMusic,
                ACTIVE.containsKey(visibleKey), sourceKey != null && sourceKey.equals(visibleKey),
                MiniBridgeContract.MUSIC_KEY.equals(visibleKey), restore);
        if (route == MediaRouting.Route.UPDATE_MEDIA) {
            if (sourceKey.equals(visibleKey)) bindNotice(ACTIVE.get(sourceKey));
            updateSide();
            return "媒体状态已静默更新";
        }
        if (route == MediaRouting.Route.PROMOTE_MEDIA) {
            bindNotice(ACTIVE.get(sourceKey));
            updateSide();
            if (!restore) animatePillFirst();
            return "媒体已成为主胶囊";
        }
        if (route == MediaRouting.Route.INTERRUPT_WITH_NOTICE) {
            if (root instanceof CameraIslandLayout) ((CameraIslandLayout) root).deferSideBirth();
            bindNotice(ACTIVE.get(sourceKey));
            updateSide();
            animatePillFirst();
            return "通知临时展开，收起后恢复媒体";
        }
        if (route == MediaRouting.Route.QUEUE) {
            updateSide();
            return "新通知已加入旁侧候选";
        }

        // Keep an existing window alive during rapid updates; refresh visible views.
        int requestedTop = Math.round(top * app.getResources().getDisplayMetrics().density);
        if (root != null && lp != null && lp.y != requestedTop) {
            // An explicit top-setting change creates a new anchored host;
            // it must not re-position the existing surface during a morph.
            LinkedHashMap<String, Notice> retained = new LinkedHashMap<>(ACTIVE);
            close();
            ACTIVE.putAll(retained);
            demoOnly = demo;
        }
        visibleKey = sourceKey;
        chatAction = sourceAction;
        chatPackage = sourcePackage;
        actionReceivedAt = SystemClock.elapsedRealtime();
        if (root != null && lp != null) {
            applySizing(app, pillWidth, pillHeight, top, panelWidth, panelHeight);
            if (root instanceof IslandGestureLayout)
                ((IslandGestureLayout) root).setSwipeMode(swipeMode);
            fullTitle.setText(heading);
            fullSubtitle.setText(shortName + " · 独立提示");
            messageText.setText(detail);
            updateContactAvatar(app, command, avatar);
            if (MiniBridgeContract.COMMAND_OTHER.equals(command) && avatar == null)
                updateIconForPackage(contactAvatar, app, sourcePackage);
            calculateExpandedHeight(detail);
            setCompactMode(command, smallText);
            if (root instanceof CameraIslandLayout) ((CameraIslandLayout) root).setCharging(MiniBridgeContract.COMMAND_CHARGE.equals(command));
            updatePillIcon(app, command);
            if (!demo) updateIconForPackage(pillIcon, app, sourcePackage);
            refreshMediaAppearance();
            refreshMediaControls();
            if (restore) {
                // Recovery must never replace an already-visible notification
                // and must never replay its arrival animation.
                if (visibleKey == null) visibleKey = sourceKey;
                if (!isExpanded) schedule(false);
            } else {
                visibleKey = sourceKey;
                if (isExpanded) {
                    // Another notification arrived while already expanded:
                    // change its content in-place; don't collapse, wait, and re-expand.
                    MAIN.removeCallbacks(EXPAND);
                    // New text may need a taller card. Recompute/morph from the
                    // current frame without restarting the compact intro.
                    morph(true);
                } else {
                    animatePillFirst();
                }
            }
            return restore ? "已静默恢复来源记录" : "已更新消息岛";
        }

        context = app;
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return "WindowManager 不可用";

        applySizing(app, pillWidth, pillHeight, top, panelWidth, panelHeight);
        if (MiniBridgeContract.COMMAND_CALL.equals(command)) { smallW=dp(callSizes[0]);smallH=dp(callSizes[1]);bigW=Math.min(dp(callSizes[4]),app.getResources().getDisplayMetrics().widthPixels-dp(16));panelHeightLimit=dp(callSizes[5]); }
        radius = dp(27);
        isExpanded = false;
        root = makeContent(app, command, heading, shortName, detail, smallText, avatar);
        fullContent.setVisibility(View.INVISIBLE);
        fullContent.setAlpha(0f);
        pillContent.setVisibility(View.VISIBLE);
        pillContent.setAlpha(1f);
        calculateExpandedHeight(detail);
        if (!demo) updateIconForPackage(pillIcon, app, sourcePackage);
        refreshMediaAppearance();
        refreshMediaControls();
        if (MiniBridgeContract.COMMAND_OTHER.equals(command) && avatar == null)
            updateIconForPackage(contactAvatar, app, sourcePackage);
        updateSide();

        StringBuilder failures = new StringBuilder();
        for (int type : WINDOW_TYPES) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    app.getResources().getDisplayMetrics().widthPixels,
                    dp(SystemUiIslandPrefs.PANEL_H_MAX + 8), type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = smallY;
            params.windowAnimations = 0;
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setFitInsetsTypes(0);
            params.setTitle("Flyme Island independent SystemUI black morph");
            // Only this module-owned SystemUI window is marked trusted. Without
            // this flag the transparent host may block underlying app touches.
            if (!markTrustedOverlay(params)) { close(); return "无法安全创建可穿透的系统窗口"; }
            try {
                wm.addView(root, params);
                ((CameraIslandLayout) root).setCharging(MiniBridgeContract.COMMAND_CHARGE.equals(command));
                root.post(SystemUiAicyIsland::refreshCompactSafeArea);
                lp = params;
                liveWindowType = type;
                final CameraIslandLayout added = (CameraIslandLayout) root;
                added.whenInputReady(() -> {
                    if (root != added || lp != params || wm == null || aicyShowing) return;
                    if ((params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) return;
                    params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    try {
                        wm.updateViewLayout(added, params);
                        log("fixed host input region ready");
                    } catch (Throwable denied) { close(); }
                });
                try { installScreenOffListener(app); }
                catch (Throwable e) { log("screen receiver failed " + e.getClass().getSimpleName()); }
                root.setAlpha(1f);
                if (aicyShowing) {
                    root.setVisibility(View.INVISIBLE);
                    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    try { wm.updateViewLayout(root, params); }
                    catch (Throwable ignored) {}
                    return "Aicy 正在使用顶部位置，通知已保留";
                }
                // First render the compact island, then unfold into the message
                // card so the transition originates at the camera anchor.
                MAIN.removeCallbacks(EXPAND);
                android.util.Log.i("FlymeIslandUnlock", "create restore=" + restore + " screenOff=" + closedForScreenOff + " pending=" + (pendingUnlockCycle > lastUnlockCycle) + " charge=" + MiniBridgeContract.COMMAND_CHARGE.equals(command));
                if (!restore && MiniBridgeContract.COMMAND_CHARGE.equals(command)) animateCompactArrival();
                else if (!restore && closedForScreenOff && MiniBridgeContract.COMMAND_MUSIC.equals(command)) {
                    closedForScreenOff = false;
                    animateCompactArrival();
                }
                else if (!restore && MiniBridgeContract.COMMAND_CALL.equals(command) && callConnected()) { closedForScreenOff=false; showCallMedium(); }
                else if (!restore) { closedForScreenOff = false; animateArrival(); }
                else if (!MiniBridgeContract.COMMAND_CHARGE.equals(command)
                        && (closedForScreenOff || (pendingUnlockCycle > lastUnlockCycle
                        && SystemClock.elapsedRealtime() - pendingUnlockCycle <= 10000L))) {
                    if (pendingUnlockCycle > lastUnlockCycle) lastUnlockCycle = pendingUnlockCycle;
                    pendingUnlockCycle = -1L;
                    closedForScreenOff = false;
                    animateCompactArrival();
                } else schedule(false);
                log("shown compact-first type=" + type + " y=" + params.y);
                return "SystemUI 小胶囊已创建；先显示摘要，再展开，通知未移除则收起态常驻";
            } catch (Throwable e) {
                failures.append(type).append(':').append(e.getClass().getSimpleName()).append(' ');
                log("type " + type + " rejected: " + e.getClass().getSimpleName());
                // A failed addView can still attach a partial parent. Recreate
                // the View before trying the alternate window type.
                try { if (root.getParent() != null) wm.removeViewImmediate(root); }
                catch (Throwable ignored) {}
                root = makeContent(app, command, heading, shortName, detail, smallText, avatar);
                calculateExpandedHeight(detail);
                fullContent.setVisibility(View.INVISIBLE);
                fullContent.setAlpha(0f);
                pillContent.setVisibility(View.VISIBLE);
                pillContent.setAlpha(1f);
            }
        }
        close();
        return "SystemUI 系统窗口创建失败：" + failures;
    }

    private static void applySizing(Context app, int pillWidth, int pillHeight,
                                    int top, int panelWidth, int panelHeight) {
        int screenPx = app.getResources().getDisplayMetrics().widthPixels;
        int usablePx = Math.max(dp(200), screenPx - dp(16));
        bigW = Math.min(dp(panelWidth), usablePx);
        panelHeightLimit = dp(panelHeight);
        baseBigW = bigW; basePanelHeightLimit = panelHeightLimit;
        bigH = panelHeightLimit; // actual height computed from message lines below
        smallW = Math.min(dp(pillWidth), usablePx);
        smallH = dp(pillHeight);
        smallY = dp(top);
        bigY = dp(Math.max(8, top));
        if (root != null) refreshCompactSafeArea();
    }

    /**
     * Fit the expanded window to actual rendered text lines, capped by
     * the user's "expanded height limit" slider, not a fixed oversized card.
     */
    private static void calculateExpandedHeight(String detail) {
        if (context == null || messageText == null) return;
        int maxTextWidth = Math.max(dp(120), bigW - dp(36));
        String value = detail == null ? "" : detail;
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(),
                messageText.getPaint(), maxTextWidth)
                .setIncludePad(true)
                .setMaxLines(6)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        // top 11 + header 44 + divider 8 + text padding 17 + footer 36 + bottom 11.
        // The text View receives leftover space with weight=1, so even when a
        // user picks a smaller cap, the footer always remains visible.
        boolean media = ACTIVE.get(visibleKey) != null && (MiniBridgeContract.COMMAND_MUSIC.equals(ACTIVE.get(visibleKey).command) || MiniBridgeContract.COMMAND_CALL.equals(ACTIVE.get(visibleKey).command));
        int inset = ExpandedSafeArea.appliedInset(dp(requestedPanelSafeTop), panelHeightLimit, dp(media ? 194 : 152));
        if (fullContent != null) fullContent.setPadding(dp(17), dp(11) + inset, dp(16), dp(11));
        int desired = dp(media ? 172 : 130) + inset + layout.getHeight();
        bigH = Math.max(dp(146), Math.min(panelHeightLimit, desired));
        int usable = Math.max(dp(22), bigH - dp(media ? 172 : 130) - inset - dp(8));
        int lineHeight = Math.max(dp(18), messageText.getLineHeight() + dp(3));
        messageText.setMaxLines(Math.max(1, Math.min(6, usable / lineHeight)));
        if (root instanceof CameraIslandLayout)
            ((CameraIslandLayout) root).setContentSizes(bigW, bigH, smallW, smallH);
    }

    private static FrameLayout makeContent(Context c, String command, String title,
                                           String origin, String detail, String compact,
                                           byte[] avatar) {
        CameraIslandLayout card = new CameraIslandLayout(c);
        card.setContentSizes(bigW, bigH, smallW, smallH);
        card.setFrame(smallW, smallH, 0f, radius);
        card.setSwipeMode(swipeMode);
        card.setGestureCallback(new IslandGestureLayout.Callback() {
            @Override public boolean isExpanded() { return SystemUiAicyIsland.isExpanded || callMediumVisible; }
            @Override public void retract() { if (isCurrentCall()) { hideCallMedium(); morph(false); } else collapseAndRestoreMusic(); }
            @Override public void dismiss() { dismissIsland(); }
            @Override public String mediaGestureKey() { return gestureIdentity(visibleKey); }
            @Override public void mediaSwipe(String identity, boolean next) {
                String key = gestureKeyFromIdentity(identity); if (key != null) sendMediaGesture(key, next);
            }
            @Override public void mediaLongPress(String identity) {
                String key = gestureKeyFromIdentity(identity); if (key != null) dismissGestureSource(key);
            }
        });
        card.setSideGestureHandler(new CameraIslandLayout.SideGestureHandler() {
            @Override public boolean enabled(String key) { return gestureEnabled(key); }
            @Override public String identity(String key) { return gestureIdentity(key); }
            @Override public void swipe(String key, boolean next) { sendMediaGesture(key, next); }
            @Override public void longPress(String key) { dismissGestureSource(key); }
        });
        // Child close / mini buttons own their click; everywhere else opens
        // the original source notification (or expands when collapsed).
        card.setOnClickListener(v -> onMainAreaClick());


        fullContent = new LinearLayout(c);
        fullContent.setOrientation(LinearLayout.VERTICAL);
        fullContent.setPadding(dp(17), dp(11), dp(16), dp(11));
        card.addView(fullContent, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        fullContent.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        contactAvatar = new ImageView(c);
        contactAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        contactAvatar.setContentDescription("消息联系人头像");
        GradientDrawable avatarShape = new GradientDrawable();
        avatarShape.setShape(GradientDrawable.OVAL);
        avatarShape.setColor(0xff282828);
        contactAvatar.setBackground(avatarShape);
        contactAvatar.setClipToOutline(true);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        avatarLp.rightMargin = dp(11);
        header.addView(contactAvatar, avatarLp);

        LinearLayout senderColumn = new LinearLayout(c);
        senderColumn.setOrientation(LinearLayout.VERTICAL);
        senderColumn.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(senderColumn, new LinearLayout.LayoutParams(0, -1, 1f));
        fullTitle = label(c, title, 15, Color.WHITE, true);
        fullTitle.setSingleLine(true);
        fullTitle.setEllipsize(TextUtils.TruncateAt.END);
        senderColumn.addView(fullTitle, new LinearLayout.LayoutParams(-1, -2));
        fullSubtitle = label(c, origin + (MiniBridgeContract.COMMAND_MUSIC.equals(command) ? " · 媒体播放" : " · 新消息"), 11, 0xffa7a7a7, false);
        fullSubtitle.setSingleLine(true);
        fullSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        senderColumn.addView(fullSubtitle, new LinearLayout.LayoutParams(-1, -2));

        TextView x = label(c, "×", 23, 0xffe5e5e5, false);
        x.setGravity(Gravity.CENTER);
        x.setContentDescription("关闭消息窗口");
        header.addView(x, new LinearLayout.LayoutParams(dp(35), -1));
        x.setOnClickListener(v -> dismissIsland());

        View separator = new View(c);
        separator.setBackgroundColor(0xff232323);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(-1, dp(1));
        sepLp.topMargin = dp(7);
        fullContent.addView(separator, sepLp);

        messageText = label(c, detail, 13, 0xffededed, false);
        messageText.setLineSpacing(dp(3), 1f);
        messageText.setMaxLines(6);
        messageText.setEllipsize(TextUtils.TruncateAt.END);
        messageText.setPadding(dp(1), dp(10), dp(2), dp(7));
        fullContent.addView(messageText, new LinearLayout.LayoutParams(-1, 0, 1f));

        mediaControls = new LinearLayout(c);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        fullContent.addView(mediaControls, new LinearLayout.LayoutParams(-1, dp(42)));
        mediaControls.setVisibility(View.GONE);

        FrameLayout footer = new FrameLayout(c);
        fullContent.addView(footer, new LinearLayout.LayoutParams(-1, dp(36)));
        TextView collapse = label(c, "⌃  收起", 12, 0xff929292, false);
        collapse.setGravity(Gravity.CENTER_VERTICAL);
        collapse.setContentDescription("收起消息卡片为顶部胶囊");
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                dp(90), dp(36), Gravity.START | Gravity.CENTER_VERTICAL);
        footer.addView(collapse, colLp);
        collapse.setOnClickListener(v -> collapseAndRestoreMusic());

        TextView mini = label(c, "小窗打开", 12, Color.WHITE, true);
        mini.setGravity(Gravity.CENTER);
        mini.setContentDescription("尝试 Flyme 原生小窗打开来源聊天");
        GradientDrawable miniBackground = new GradientDrawable();
        miniBackground.setColor(0xff2b2b2b);
        miniBackground.setCornerRadius(dp(14));
        mini.setBackground(miniBackground);
        FrameLayout.LayoutParams miniLp = new FrameLayout.LayoutParams(
                dp(82), dp(30), Gravity.END | Gravity.CENTER_VERTICAL);
        footer.addView(mini, miniLp);
        mini.setOnClickListener(v -> launchOriginal(true));

        pillContent = new LinearLayout(c);
        pillContent.setOrientation(LinearLayout.HORIZONTAL);
        pillContent.setGravity(Gravity.CENTER_VERTICAL);
        pillContent.setVisibility(View.INVISIBLE);
        pillContent.setAlpha(0f);
        pillContent.setPadding(dp(9), 0, dp(11), 0);
        card.addView(pillContent, new FrameLayout.LayoutParams(-1, -1));
        callMedium=new LinearLayout(c); callMedium.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        callMedium.setPadding(dp(8),0,dp(8),0); callMedium.setVisibility(View.INVISIBLE);callMedium.setAlpha(0f);
        card.addView(callMedium,new FrameLayout.LayoutParams(-1,-1));
        card.setCallMediumSize(dp(callSizes[2]),dp(callSizes[3]));

        pillIcon = new ImageView(c);
        pillIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable pillIconShape = new GradientDrawable();
        pillIconShape.setShape(GradientDrawable.OVAL);
        pillIconShape.setColor(Color.WHITE);
        pillIcon.setBackground(pillIconShape);
        pillIcon.setClipToOutline(true);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(17), dp(17));
        pillContent.addView(pillIcon, iconLp);
        chargeLabel = label(c, "充电中", 11, Color.WHITE, true);
        chargeLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        chargeLabel.setSingleLine(true); chargeLabel.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams chargeLp = new LinearLayout.LayoutParams(0, -1);
        chargeLp.leftMargin = dp(4); pillContent.addView(chargeLabel, chargeLp);

        pillTitle = label(c, compact, 11, Color.WHITE, true);
        pillTitle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        pillTitle.setSingleLine(true);
        pillTitle.setEllipsize(TextUtils.TruncateAt.END);
        // Reserve the camera area; only the rightmost safe column contains text or wave.
        View cameraSpacer = new View(c);
        pillContent.addView(cameraSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout.LayoutParams pillTitleLp = new LinearLayout.LayoutParams(0, -1);
        pillContent.addView(pillTitle, pillTitleLp);
        mediaWave = new MediaWaveView(c);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(dp(38), dp(16));
        waveLp.leftMargin = dp(8);
        pillContent.addView(mediaWave, waveLp);
        refreshCompactSafeArea();
        applyWaveStyle();
        setCompactMode(command, compact);

        updatePillIcon(c, command);
        updateContactAvatar(c, command, avatar);
        return card;
    }

    private static void updateContactAvatar(Context c, String command, byte[] png) {
        if (contactAvatar == null) return;
        if (png != null && png.length > 0
                && png.length <= NotificationAvatarExtractor.MAX_BYTES) {
            try {
                BitmapFactory.Options meta = new BitmapFactory.Options();
                meta.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(png, 0, png.length, meta);
                if (meta.outWidth > 0 && meta.outHeight > 0
                        && meta.outWidth <= 256 && meta.outHeight <= 256) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (bitmap != null) {
                        contactAvatar.setImageBitmap(bitmap);
                        return;
                    }
                }
            } catch (Throwable ignored) {
                // Fail closed to a generic application icon.
            }
        }
        String pkg = MiniBridgeContract.COMMAND_WECHAT.equals(command)
                ? MessageCapsulePublisher.WECHAT
                : MiniBridgeContract.COMMAND_QQ.equals(command)
                ? MessageCapsulePublisher.QQ : null;
        if (pkg != null) {
            try {
                contactAvatar.setImageDrawable(c.getPackageManager().getApplicationIcon(pkg));
                return;
            } catch (Throwable ignored) {}
        }
        GradientDrawable fallback = new GradientDrawable();
        fallback.setShape(GradientDrawable.OVAL);
        fallback.setColor(0xff454545);
        contactAvatar.setImageDrawable(fallback);
    }

    /** Remove only known source summary prefixes, never arbitrary chat punctuation. */
    private static String cleanMessage(String source) {
        if (source == null) return "";
        String clean = source.replaceFirst("^\\[\\d+条\\]\\s*", "");
        return clean.replaceFirst("^\\d{1,3}\\s*[:：]\\s*", "");
    }

    private static void cancelFrame() {
        ++generation;
        if (pendingFrame != null && root != null)
            root.getViewTreeObserver().removeOnPreDrawListener(pendingFrame);
        pendingFrame = null;
        if (running != null) {
            running.removeAllListeners();
            running.removeAllUpdateListeners();
            running.cancel();
            running = null;
        }
    }

    private static void hideCallMedium() {
        callMediumVisible=false;
        if (callMedium!=null) { callMedium.setAlpha(0f);callMedium.setVisibility(View.INVISIBLE); }
    }
    private static void showCallMedium() {
        if (root==null || lp==null || callMedium==null || !isCurrentCall() || isLocked(context)) return;
        MAIN.removeCallbacks(EXPAND); MAIN.removeCallbacks(COLLAPSE); MAIN.removeCallbacks(ROTATE);
        callMediumVisible=true; isExpanded=true;
        ((CameraIslandLayout)root).setCallMediumSize(dp(callSizes[2]),dp(callSizes[3]));
        callMedium.setAlpha(0f); callMedium.setVisibility(View.INVISIBLE);
        transition(dp(callSizes[2]),dp(callSizes[3]),0f,dp(callSizes[3])/2f,0f,0f,360L,() -> {
            if (callMediumVisible && callMedium!=null && isCurrentCall()) { callMedium.setVisibility(View.VISIBLE);callMedium.animate().alpha(1f).setDuration(120).start(); }
        });
    }
    private static void morph(boolean expand) { morph(expand, null); }

    private static void morph(boolean expand, Runnable completed) {
        if (pendingExit != null && completed == null) return;
        hideCallMedium();
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(ROTATE);
        if (root == null || aicyShowing) {
            if (completed != null) completed.run();
            return;
        }
        if (isLocked(context)) { close(); return; }
        isExpanded = expand;
        CameraIslandLayout view = (CameraIslandLayout) root;
        if (running == null && pendingFrame == null
                && view.visualWidth() == (expand ? bigW : smallW)
                && view.visualHeight() == (expand ? bigH : smallH)
                && view.visualTop() == (expand ? Math.max(0, bigY - smallY) : 0f)) {
            if (!launchInProgress && pendingExit == null) schedule(expand);
            if (completed != null) completed.run();
            if (!expand) releaseSideWhenSettled(view);
            return;
        }
        transition(expand ? bigW : smallW, expand ? bigH : smallH,
                expand ? Math.max(0, bigY - smallY) : 0f,
                dp(expand ? 27 : 16), expand ? 1f : 0f, expand ? 0f : 1f,
                MORPH_MS, () -> {
                    if (!launchInProgress && pendingExit == null) schedule(expand);
                    if (completed != null) completed.run();
                    if (!expand) releaseSideWhenSettled(view);
                });
    }

    private static void releaseSideWhenSettled(CameraIslandLayout view) {
        if (root != view || isExpanded || launchInProgress || pendingExit != null
                || running != null || pendingFrame != null) return;
        if (Math.abs(view.visualWidth()-smallW) > 1f || Math.abs(view.visualHeight()-smallH) > 1f) return;
        view.releaseDeferredSide();
    }

    /** All positions are LOCAL to a host whose y remains at the camera anchor. */
    private static void transition(float width, float height, float top,
                                   float corner, float fullAlpha, float pillAlpha,
                                   long duration, Runnable completed) {
        if (root == null || lp == null || wm == null) {
            if (completed != null) completed.run();
            return;
        }
        final CameraIslandLayout view = (CameraIslandLayout) root;
        final float fw = view.visualWidth(), fh = view.visualHeight();
        final float fy = view.visualTop(), fr = radius;
        final float fa = fullContent.getAlpha(), pa = pillContent.getAlpha();
        cancelFrame();
        final int token = generation;
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setContentSizes(bigW, bigH, smallW, smallH);
        fullContent.setVisibility(View.VISIBLE);
        pillContent.setVisibility(View.VISIBLE);
        final WindowManager.LayoutParams params = lp;
        final int hostW = params.width;
        final int hostH = params.height;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        // Critically damped, monotone response: no spatial overshoot or bounce.
        animator.setInterpolator(t -> (float) ((1d - (1d + 8d * t) * Math.exp(-8d * t))
                / (1d - 9d * Math.exp(-8d))));
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            if (token != generation || root != view) return;
            float t = (Float) animation.getAnimatedValue();
            radius = mix(fr, corner, t);
            view.setFrame(mix(fw, width, t), mix(fh, height, t), mix(fy, top, t), radius);
            fullContent.setAlpha(mix(fa, fullAlpha, t));
            pillContent.setAlpha(mix(pa, pillAlpha, t));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (token != generation || root != view) return;
                running = null;
                radius = corner;
                view.setFrame(width, height, top, corner);
                fullContent.setAlpha(fullAlpha);
                pillContent.setAlpha(pillAlpha);
                fullContent.setVisibility(fullAlpha > 0f ? View.VISIBLE : View.INVISIBLE);
                pillContent.setVisibility(pillAlpha > 0f ? View.VISIBLE : View.INVISIBLE);
                // The host surface remains unchanged for its whole lifetime.
                // Shrink the drawing and input region, never the WM window.
                if (completed != null) completed.run();
                if (root == view && fullAlpha == 0f && pillAlpha == 1f && !isExpanded && pendingExit == null && !launchInProgress
                        && running == null && pendingFrame == null
                        && (Math.abs(view.visualWidth()-smallW) > .5f || Math.abs(view.visualHeight()-smallH) > .5f))
                    morph(false);
            }
        });
        pendingFrame = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (token != generation || root != view) return true;
                if (view.getWidth() != hostW || view.getHeight() != hostH) return true;
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                pendingFrame = null;
                running = animator;
                animator.start();
                return true;
            }
        };
        view.getViewTreeObserver().addOnPreDrawListener(pendingFrame);
        view.invalidate();
    }

    private static void animateCompactArrival() {
        if (!(root instanceof CameraIslandLayout)) return;
        radius = smallH * .5f;
        ((CameraIslandLayout) root).setFrame(smallH, smallH, 0f, radius);
        pillContent.setAlpha(0f);
        transition(smallW, smallH, 0f, dp(16), 0f, 1f, 240L, () -> schedule(false));
    }

    private static void animateArrival() {
        if (!(root instanceof CameraIslandLayout)) return;
        radius = smallH * .5f;
        ((CameraIslandLayout) root).setFrame(smallH, smallH, 0f, radius);
        pillContent.setAlpha(0f);
        transition(smallW, smallH, 0f, dp(16), 0f, 1f, 240L,
                () -> MAIN.postDelayed(EXPAND, PILL_INTRO_MS));
    }

    private static void finishPendingExit() {
        Runnable done = pendingExit;
        pendingExit = null;
        if (done != null) done.run();
    }

    /** Card -> pill -> symmetric camera-width contraction -> detach. */
    private static void animateExit(Runnable completed) {
        if (pendingExit != null) return;
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(ROTATE);
        MAIN.removeCallbacks(HIDE);
        if (root == null || aicyShowing) {
            completed.run();
            return;
        }
        pendingExit = completed;
        morph(false, () -> {
            if (root == null || aicyShowing) { finishPendingExit(); return; }
            transition(smallH, smallH, 0f, smallH * .5f, 0f, 0f,
                    220L, SystemUiAicyIsland::finishPendingExit);
        });
    }

    /** Finish only the clicked source; preserve notices received during exit. */
    private static void finishOpened(Context app, String key, boolean submitted, Notice clicked) {
        LinkedHashMap<String, Notice> remaining = new LinkedHashMap<>(ACTIVE);
        // A new message may reuse the same Android notification key during exit.
        // Do not cancel or suppress that newer notification.
        boolean unchanged = remaining.get(key) == null || remaining.get(key) == clicked;
        boolean keepMusic = clicked != null && MiniBridgeContract.COMMAND_MUSIC.equals(clicked.command);
        if (unchanged && !keepMusic) {
            remaining.remove(key);
            if (SUPPRESSED_KEYS.size() >= 64) SUPPRESSED_KEYS.clear();
            SUPPRESSED_KEYS.add(key);
        }
        float density = app.getResources().getDisplayMetrics().density;
        int pw = Math.round(smallW / density), ph = Math.round(smallH / density);
        int top = Math.round(smallY / density), bw = Math.round(bigW / density);
        int bh = Math.round(panelHeightLimit / density);
        close();
        if (submitted && unchanged && !keepMusic) notifyOriginalOpened(app, key);
        if (remaining.isEmpty()) return;
        ACTIVE.putAll(remaining);
        Notice latest = remaining.get(selectCandidate(null, false));
        if (latest == null) return;
        show(app, latest.command, latest.sender, latest.body, latest.avatar,
                latest.action, latest.sourcePackage,
                latest.key, latest.pillWidth, latest.pillHeight, top, bw, bh, true);
    }

    /** A notification received while the last island exits must not be lost. */
    private static void closeOrRestoreQueued() {
        if (ACTIVE.isEmpty() || context == null) { close(); return; }
        Context app = context;
        LinkedHashMap<String, Notice> remaining = new LinkedHashMap<>(ACTIVE);
        float density = app.getResources().getDisplayMetrics().density;
        int pw = Math.round(smallW / density), ph = Math.round(smallH / density);
        int top = Math.round(smallY / density), bw = Math.round(bigW / density);
        int bh = Math.round(panelHeightLimit / density);
        close();
        ACTIVE.putAll(remaining);
        Notice next = ACTIVE.get(selectCandidate(null, false));
        if (next != null) show(app, next.command, next.sender, next.body, next.avatar,
                next.action, next.sourcePackage, next.key, next.pillWidth, next.pillHeight, top, bw, bh, true);
    }

    private static void onMainAreaClick() {
        if (pendingExit != null || launchInProgress) return;
        if (!isExpanded) {
            if (isCurrentCall() && callConnected()) showCallMedium(); else morph(true);
            return;
        }
        launchOriginal(false);
    }

    /**
     * Original Aicy-style exit requested by user:
     * - the card visibly collapses into the camera pill;
     * - the pill then fades out and detaches;
     * - the original chat/small-window launch starts IN PARALLEL.
     * No extra Toast. Geometry deliberately follows the stable v0.30 WM path.
     */
    private static void launchOriginal(boolean miniRequested) {
        if (launchInProgress || context == null || isLocked(context)) return;
        final PendingIntent target = chatAction;
        final Context app = context;
        final String pkg = chatPackage;
        final String sourceKey = visibleKey;
        final Notice clicked = ACTIVE.get(sourceKey);
        if (target == null || pkg == null || sourceKey == null) {
            log("no original notification action");
            dismissIsland();
            return;
        }

        launchInProgress = true;
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(HIDE);
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(ROTATE);
        boolean miniPrepared = false;
        if (miniRequested) {
            try {
                if (app.getPackageManager().checkPermission(
                        "android.permission.START_TASKS_FROM_RECENTS",
                        MiniBridgeContract.SYSTEM_UI) == PackageManager.PERMISSION_GRANTED) {
                    Class<?> api = Class.forName("flyme.view.WindowManagerExt");
                    Object instance = api.getMethod("getInstance", Context.class)
                            .invoke(null, app);
                    if (instance != null) {
                        api.getMethod("setStartWindowMode", String.class)
                                .invoke(instance, pkg);
                        miniPrepared = true;
                    }
                }
            } catch (Throwable error) {
                log("Flyme mini mode unavailable: " + error.getClass().getSimpleName());
            }
        }

        // Submit the original action and start retracting in the same main-loop
        // turn. Submission state must be known even when animations are disabled.
        final boolean[] submitted = {false};
        try {
            ActivityOptions launchOptions = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 34)
                launchOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            Bundle options = launchOptions.toBundle();
            if (miniPrepared) options.putBoolean("start_windowmode", true);
            target.send(app, 0, null, null, null, null, options);
            submitted[0] = true;
        } catch (PendingIntent.CanceledException | RuntimeException failure) {
            log("original source click rejected: " + failure.getClass().getSimpleName());
            // Still finish the exit. Do not delete the source notification.
        }
        animateExit(() -> finishOpened(app, sourceKey, submitted[0], clicked));

    }

    /**
     * Ask our own notification-listener service to clear this exact source.
     * SystemUI cannot directly delete a random foreign notification; the
     * listener verifies UID, timestamp, source package and active key.
     */
    private static void notifyOriginalOpened(Context app, String sourceKey) {
        if (sourceKey == null || sourceKey.isEmpty()) return;
        try {
            Intent request = new Intent(MiniBridgeContract.ACTION_OPENED_DISMISS);
            request.setPackage(MiniBridgeContract.APP);
            request.putExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY, sourceKey);
            request.putExtra(MiniBridgeContract.EXTRA_SENT_AT,
                    SystemClock.elapsedRealtime());
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.BroadcastOptions opts =
                        android.app.BroadcastOptions.makeBasic()
                                .setShareIdentityEnabled(true);
                app.sendBroadcast(request, null, opts.toBundle());
            }
        } catch (RuntimeException failure) {
            log("source cleanup not delivered: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * Aicy wins the shared camera region. Keep our notification registry and
     * attached root, but release its input region and make it invisible.
     * This is triggered only by authenticated Aicy window-presence signals.
     */
    public static void setAicyShowing(boolean showing) {
        if (aicyShowing == showing) return;
        aicyShowing = showing;
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(ROTATE);
        if (root == null || lp == null || wm == null) return;
        if (showing) {
            stopCoverRotation();
            cancelFrame();
            root.animate().cancel();
            root.setVisibility(View.INVISIBLE);
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(root, lp); }
            catch (Throwable denied) {
                log("Aicy suspension failed: " + denied.getClass().getSimpleName());
                close();
            }
            finishPendingExit();
            return;
        }
        // Assistant has closed. Restore *only* the compact island silently.
        if (ACTIVE.isEmpty() && !demoOnly) {
            close();
            return;
        }
        isExpanded = false;
        root.setScaleX(1f);
        root.setScaleY(1f);
        root.setTranslationY(0f);
        root.setLayerType(View.LAYER_TYPE_NONE, null);
        root.setAlpha(1f);
        radius = dp(16);
        ((CameraIslandLayout) root).setContentSizes(bigW, bigH, smallW, smallH);
        ((CameraIslandLayout) root).setFrame(smallW, smallH, 0f, radius);
        updateSide();
        fullContent.setVisibility(View.INVISIBLE);
        fullContent.setAlpha(0f);
        pillContent.setVisibility(View.VISIBLE);
        pillContent.setAlpha(1f);
        if (((CameraIslandLayout) root).isInputRegionReady())
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            wm.updateViewLayout(root, lp);
            root.setVisibility(View.VISIBLE);
            refreshMediaAppearance();
            schedule(false);
        } catch (Throwable denied) {
            log("Aicy restore failed: " + denied.getClass().getSimpleName());
            close();
        }
    }

    private static boolean gestureEnabled(String key) {
        Notice notice = ACTIVE.get(key);
        return notice != null && (MiniBridgeContract.COMMAND_MUSIC.equals(notice.command)
                ? mediaGestureEnabled : gestureApps.contains(notice.sourcePackage))
                && pendingExit == null && !launchInProgress && !aicyShowing;
    }

    private static String gestureIdentity(String key) {
        if (!gestureEnabled(key)) return null;
        return key + "\n" + ACTIVE.get(key).sourcePackage;
    }
    private static String gestureKeyFromIdentity(String identity) {
        if (identity == null) return null;
        int end = identity.indexOf('\n');
        if (end <= 0) return null;
        String key = identity.substring(0, end);
        return identity.equals(gestureIdentity(key)) ? key : null;
    }

    private static void sendMediaGesture(String key, boolean next) {
        if (!gestureEnabled(key) || context == null) return;
        Notice notice = ACTIVE.get(key);
        try {
            Intent intent = new Intent(MiniBridgeContract.ACTION_MEDIA_GESTURE).setPackage(MiniBridgeContract.APP);
            intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
            intent.putExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE, notice.sourcePackage);
            intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_DIRECTION, next ? "next" : "previous");
            intent.putExtra(MiniBridgeContract.EXTRA_MEDIA_GESTURE_SOURCE, MiniBridgeContract.COMMAND_MUSIC.equals(notice.command));
            android.app.BroadcastOptions options = android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true);
            context.sendBroadcast(intent, null, options.toBundle());
        } catch (RuntimeException ignored) {}
    }

    private static void dismissGestureSource(String key) {
        if (!gestureEnabled(key)) return;
        if (key.equals(visibleKey)) { dismissIsland(); return; }
        removeSource(key);
        if (SUPPRESSED_KEYS.size() >= 64) SUPPRESSED_KEYS.clear();
        SUPPRESSED_KEYS.add(key);
    }

    private static void dismissIsland() {
        if (visibleKey == null || demoOnly || !ACTIVE.containsKey(visibleKey)) {
            animateExit(SystemUiAicyIsland::dismissNow);
            return;
        }
        String key = visibleKey;
        String successor = sideKey();
        ACTIVE.remove(key);
        if (SUPPRESSED_KEYS.size() >= 64) SUPPRESSED_KEYS.clear();
        SUPPRESSED_KEYS.add(key);
        if (ACTIVE.isEmpty()) { animateExit(SystemUiAicyIsland::closeOrRestoreQueued); return; }
        Notice next = ACTIVE.get(successor);
        if (next == null) next = ACTIVE.get(selectCandidate(key, false));
        bindNotice(next);
        updateSide();
        morph(false);
    }

    private static void dismissNow() {
        SUPPRESSED_KEYS.addAll(ACTIVE.keySet());
        if (SUPPRESSED_KEYS.size() > 64) SUPPRESSED_KEYS.clear();
        close();
    }

    private static void collapseAndRestoreMusic() {
        morph(false, () -> {
            Notice media = ACTIVE.get(MiniBridgeContract.MUSIC_KEY);
            Notice current = ACTIVE.get(visibleKey);
            if (current != null && MiniBridgeContract.COMMAND_CALL.equals(current.command)) return;
            if (media != null && root != null && !launchInProgress && pendingExit == null) {
                bindNotice(media);
                updateSide();
            }
        });
    }

    private static boolean markTrustedOverlay(WindowManager.LayoutParams params) {
        try {
            java.lang.reflect.Field flag = WindowManager.LayoutParams.class.getDeclaredField("PRIVATE_FLAG_TRUSTED_OVERLAY");
            java.lang.reflect.Field bits = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            flag.setAccessible(true);
            bits.setAccessible(true);
            int value = flag.getInt(null);
            if (value == 0) return false;
            bits.setInt(params, bits.getInt(params) | value);
            return (bits.getInt(params) & value) == value;
        } catch (Throwable unavailable) {
            log("trusted window flag unavailable: " + unavailable.getClass().getSimpleName());
            return false;
        }
    }

    private static String limitCompactText(String value) {
        if (value == null) return "";
        int length = value.codePointCount(0, value.length());
        return length <= 8 ? value : value.substring(0, value.offsetByCodePoints(0, 8)) + "…";
    }

    private static int compactRightWidth() {
        if (context == null) return 0;
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int reserve = dp(28); // Conservative fallback when window insets are unavailable.
        try {
            android.view.WindowInsets insets = root == null ? null : root.getRootWindowInsets();
            android.view.DisplayCutout cutout = insets == null ? null : insets.getDisplayCutout();
            if (cutout != null) for (android.graphics.Rect r : cutout.getBoundingRects()) {
                if (r.top < smallY + smallH && r.bottom > smallY
                        && r.left < screen/2 + dp(100) && r.right > screen/2 - dp(100))
                    reserve = Math.max(reserve, r.right - screen/2 + dp(4));
            }
        } catch (RuntimeException ignored) {}
        return CompactSafeArea.rightWidth(smallW, reserve, dp(11));
    }
    private static void refreshCompactSafeArea() {
        if (pillTitle == null) return;
        android.view.ViewGroup.LayoutParams lp = pillTitle.getLayoutParams();
        int width = compactRightWidth();
        if (chargeLabel != null) {
            android.view.ViewGroup.LayoutParams cl = chargeLabel.getLayoutParams();
            int leftWidth = Math.max(0, Math.min(dp(48), smallW/2 - dp(9+17+4+8)));
            if (cl != null && cl.width != leftWidth) { cl.width=leftWidth; chargeLabel.setLayoutParams(cl); }
        }
        if (lp != null && lp.width != width) { lp.width = width; pillTitle.setLayoutParams(lp); }
        applyWaveStyle();
    }

    private static void applyWaveStyle() {
        if (mediaWave == null || waveStyle == null || waveStyle.length != WaveStylePrefs.DEFAULTS.length) return;
        mediaWave.configure(waveStyle);
        android.view.ViewGroup.LayoutParams lp = mediaWave.getLayoutParams();
        if (lp != null) {
            int desired = waveStyle[0]*waveStyle[1] + Math.max(0,waveStyle[0]-1)*waveStyle[2];
            int available = compactRightWidth();
            lp.width = Math.min(available, dp(Math.min(80, Math.max(14, desired))));
            lp.height = Math.max(dp(8), Math.min(dp(waveStyle[3]), Math.max(dp(8), smallH-dp(4))));
            mediaWave.setLayoutParams(lp);
        }
    }

    private static void stopCoverRotation() {
        if (coverAnimator != null) { coverAnimator.cancel(); coverAnimator = null; }
        if (pillIcon != null) pillIcon.setRotation(0f);
    }

    private static void refreshMediaAppearance() {
        if (pillIcon == null || context == null) return;
        Notice notice = ACTIVE.get(visibleKey);
        if (notice == null || !MiniBridgeContract.COMMAND_MUSIC.equals(notice.command)) {
            if (mediaWave != null) mediaWave.setAccentColor(0);
            stopCoverRotation(); return;
        }
        Bitmap cover = null;
        if ((waveStyle[6] != 0 || waveStyle[8] != 0) && notice.avatar != null && notice.avatar.length <= NotificationAvatarExtractor.MAX_BYTES) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(notice.avatar, 0, notice.avatar.length, opts);
                if (opts.outWidth > 0 && opts.outHeight > 0 && opts.outWidth <= 256 && opts.outHeight <= 256)
                    cover = BitmapFactory.decodeByteArray(notice.avatar, 0, notice.avatar.length);
            } catch (RuntimeException | OutOfMemoryError ignored) {}
        }
        if (mediaWave != null) {
            int accent=0;
            if (waveStyle[8] != 0 && cover != null) {
                try { accent = AlbumPalette.extract(cover); } catch (RuntimeException ignored) {}
            }
            mediaWave.setAccentColor(accent);
        }
        boolean hasCover = cover != null && waveStyle[6] != 0;
        if (hasCover) pillIcon.setImageBitmap(cover);
        else updateIconForPackage(pillIcon, context, notice.sourcePackage);
        if (hasCover && waveStyle[7] != 0 && mediaPlaying) {
            if (coverAnimator == null) {
                ImageView target = pillIcon;
                coverAnimator = ValueAnimator.ofFloat(0f, 360f);
                coverAnimator.setDuration(9000L); coverAnimator.setRepeatCount(ValueAnimator.INFINITE);
                coverAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                coverAnimator.addUpdateListener(a -> { if (pillIcon == target) target.setRotation((Float) a.getAnimatedValue()); });
                coverAnimator.start();
            }
        } else stopCoverRotation();
    }

    private static void refreshMediaControls() {
        if (mediaControls == null || context == null) return;
        Notice notice=ACTIVE.get(visibleKey);
        boolean media=notice != null && MiniBridgeContract.COMMAND_MUSIC.equals(notice.command);
        boolean call=notice != null && MiniBridgeContract.COMMAND_CALL.equals(notice.command);
        mediaControls.removeAllViews(); mediaControls.setVisibility(media || call ? View.VISIBLE : View.GONE);
        if (callMedium != null) {
            callMedium.removeAllViews();
            if (call) {
                View spacer = new View(context); callMedium.addView(spacer,new LinearLayout.LayoutParams(0,1,1f));
                if ((callCaps & 8)!=0) addMediumCallControl("免提","speaker_on",notice.key);
                if ((callCaps & 16)!=0) addMediumCallControl("听筒","speaker_off",notice.key);
                if ((callCaps & 4)!=0) addMediumCallControl("挂断","hangup",notice.key);
            }
        }
        if (call) {
            if ((callCaps & 2) != 0) addCallControl("拒绝", "reject", notice.key);
            if ((callCaps & 1) != 0) addCallControl("接听", "answer", notice.key);
            if ((callCaps & 8) != 0) addCallControl("打开免提", "speaker_on", notice.key);
            if ((callCaps & 16) != 0) addCallControl("关闭免提", "speaker_off", notice.key);
            if ((callCaps & 4) != 0) addCallControl("挂断", "hangup", notice.key);
            return;
        }
        if (!media) return;
        for (int i=0;i<mediaCustomIds.size();i++) addMediaControl(mediaCustomLabels.get(i), "custom:"+mediaCustomIds.get(i), true, mediaCustomIcons.get(i), notice.sourcePackage);
        addMediaControl("上一首", "previous", (mediaActions & android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0, 0, notice.sourcePackage);
        long toggle=mediaPlaying ? android.media.session.PlaybackState.ACTION_PAUSE : android.media.session.PlaybackState.ACTION_PLAY;
        addMediaControl(mediaPlaying ? "暂停" : "播放", "toggle", (mediaActions & (toggle|android.media.session.PlaybackState.ACTION_PLAY_PAUSE))!=0, 0, notice.sourcePackage);
        addMediaControl("下一首", "next", (mediaActions & android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)!=0, 0, notice.sourcePackage);
    }
    private static void addMediumCallControl(String title,String action,String key) {
        TextView button=label(context,title,12,Color.WHITE,true);button.setGravity(Gravity.CENTER);button.setContentDescription(title);
        callMedium.addView(button,new LinearLayout.LayoutParams(dp(52),-1));
        button.setOnClickListener(v -> sendCallAction(key,action));
    }
    private static void sendCallAction(String key,String action) {
        Notice current=ACTIVE.get(visibleKey);
        if (current==null || !key.equals(current.key) || !MiniBridgeContract.COMMAND_CALL.equals(current.command)) return;
        try { Intent i=new Intent(MiniBridgeContract.ACTION_CALL_CONTROL).setPackage(MiniBridgeContract.APP);
            i.putExtra(MiniBridgeContract.EXTRA_SENT_AT,SystemClock.elapsedRealtime());i.putExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY,key);
            i.putExtra(MiniBridgeContract.EXTRA_MEDIA_DIRECTION,action);
            context.sendBroadcast(i,null,android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
        } catch(RuntimeException ignored) {}
    }
    private static void addCallControl(String title, String action, String key) {
        TextView button = label(context, title, 16, Color.WHITE, true);
        button.setGravity(Gravity.CENTER); button.setContentDescription(title);
        mediaControls.addView(button, new LinearLayout.LayoutParams(0, -1, 1f));
        button.setOnClickListener(v -> {
            Notice current = ACTIVE.get(visibleKey);
            if (current == null || !key.equals(current.key) || !MiniBridgeContract.COMMAND_CALL.equals(current.command)) return;
            try {
                Intent i = new Intent(MiniBridgeContract.ACTION_CALL_CONTROL).setPackage(MiniBridgeContract.APP);
                i.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
                i.putExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY, key);
                i.putExtra(MiniBridgeContract.EXTRA_MEDIA_DIRECTION, action);
                context.sendBroadcast(i, null, android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
            } catch (RuntimeException ignored) {}
        });
    }
    private static void addMediaControl(String title, String action, boolean enabled, int iconResource, String pkg) {
        Drawable icon = null;
        if (mediaIconMode) {
            if (action.startsWith("custom:")) {
                try { if (iconResource != 0) icon = context.getPackageManager().getResourcesForApplication(pkg).getDrawable(iconResource, null); }
                catch (RuntimeException | PackageManager.NameNotFoundException ignored) {}
                if (icon == null) {
                    String hint = (title + " " + action).toLowerCase(java.util.Locale.ROOT);
                    if (hint.contains("like") || hint.contains("favorite") || hint.contains("收藏") || hint.contains("喜欢") || hint.contains("喜歡")) icon = new MediaControlIcon("favorite");
                    else if (hint.contains("lyric") || hint.contains("歌词") || hint.contains("歌詞")) icon = new MediaControlIcon("lyrics");
                    else if (hint.contains("shuffle") || hint.contains("随机") || hint.contains("隨機")) icon = new MediaControlIcon("shuffle");
                }
            } else if (!action.startsWith("custom:")) {
                icon = new MediaControlIcon(action.equals("toggle") ? (mediaPlaying ? "pause" : "play") : action);
            }
        }
        View button;
        if (icon != null) {
            ImageView image = new ImageView(context);
            if (action.startsWith("custom:")) { icon = icon.mutate(); icon.setTint(Color.WHITE); }
            image.setImageDrawable(icon); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(10), dp(9), dp(10), dp(9));
            image.setAlpha(enabled ? 1f : .4f); button = image;
        } else {
            TextView text = label(context, title, 12, enabled ? Color.WHITE : 0xff666666, true);
            text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END); text.setGravity(Gravity.CENTER);
            button = text;
        }
        button.setEnabled(enabled); button.setContentDescription(title);
        mediaControls.addView(button,new LinearLayout.LayoutParams(0,-1,1f));
        if (enabled) button.setOnClickListener(v -> sendMediaControl(action));
    }
    private static void sendMediaControl(String action) {
        Notice n=ACTIVE.get(visibleKey);
        if (context==null || n==null || !MiniBridgeContract.COMMAND_MUSIC.equals(n.command) || action==null) return;
        try {
            Intent i=new Intent(MiniBridgeContract.ACTION_MEDIA_CONTROL).setPackage(MiniBridgeContract.APP);
            i.putExtra(MiniBridgeContract.EXTRA_SENT_AT,SystemClock.elapsedRealtime());
            i.putExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE,n.sourcePackage);
            i.putExtra(MiniBridgeContract.EXTRA_MEDIA_DIRECTION,action);
            context.sendBroadcast(i,null,android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
        } catch (RuntimeException ignored) {}
    }

    private static void setCompactMode(String command, String value) {
        boolean music = MiniBridgeContract.COMMAND_MUSIC.equals(command);
        if (MiniBridgeContract.COMMAND_CHARGE.equals(command) && value != null) {
            java.util.regex.Matcher percent = java.util.regex.Pattern.compile("\\d{1,3}%").matcher(value);
            if (chargeLabel != null) chargeLabel.setText(value.startsWith("已充满") ? "已充满" : "充电中");
            if (percent.find()) value = percent.group();
        }
        if (chargeLabel != null) chargeLabel.setVisibility(MiniBridgeContract.COMMAND_CHARGE.equals(command) ? View.VISIBLE : View.GONE);
        if (pillTitle != null) { pillTitle.setText(limitCompactText(value)); pillTitle.setVisibility(music ? View.GONE : View.VISIBLE); }
        if (mediaWave != null) { mediaWave.setPlaying(mediaPlaying); mediaWave.setVisibility(music ? View.VISIBLE : View.GONE); }
    }

    private static void schedule(boolean expand) {
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(HIDE);
        MAIN.removeCallbacks(ROTATE);
        if (expand && !isCurrentCall()) MAIN.postDelayed(COLLAPSE, EXPANDED_MS);
        else if (demoOnly) MAIN.postDelayed(HIDE, DEMO_PILL_MS);
        // Real notifications persist; rotation changes only which source is in focus.
    }

    /**
     * Compact-to-expanded is continuous: no forced collapse on existing islands,
     * no 580+360ms double wait. The same WindowManager view morphs in place.
     */
    private static void animatePillFirst() {
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(ROTATE);
        if (root == null || lp == null) return;
        root.animate().cancel();
        root.setTranslationX(0f);
        root.setTranslationY(0f);
        root.setAlpha(1f);
        if (running == null && pendingFrame == null && !isExpanded) {
            // Compact view is already attached. The brief intro is intentional.
            MAIN.postDelayed(EXPAND, PILL_INTRO_MS);
        } else {
            // Reverse any in-flight collapse directly from the current frame.
            morph(true);
        }
    }

    /** Rotate between still-active notifications while keeping the pill visible. */
    private static void showNextCompact() {
        if (root == null || isExpanded || ACTIVE.size() < 2) return;
        String next = null;
        boolean afterCurrent = visibleKey == null;
        for (String key : ACTIVE.keySet()) {
            if (afterCurrent) { next = key; break; }
            if (key.equals(visibleKey)) afterCurrent = true;
        }
        if (next == null) next = ACTIVE.keySet().iterator().next();
        Notice notice = ACTIVE.get(next);
        if (notice != null) bindNotice(notice);
        schedule(false);
    }

    private static void bindNotice(Notice notice) {
        if (notice == null || context == null || fullTitle == null) return;
        String name = appLabel(context, notice.sourcePackage);
        String title = notice.sender.isEmpty() ? name + "新消息" : notice.sender;
        String detail = notice.body.isEmpty()
                ? "新消息 · 通知未提供正文或预览未开启"
                : cleanMessage(notice.body);
        fullTitle.setText(title);
        fullSubtitle.setText(name + (MiniBridgeContract.COMMAND_MUSIC.equals(notice.command) ? " · 媒体播放" : " · 新消息"));
        messageText.setText(detail);
        updateContactAvatar(context, notice.command, notice.avatar);
        if (MiniBridgeContract.COMMAND_OTHER.equals(notice.command) && notice.avatar == null)
            updateIconForPackage(contactAvatar, context, notice.sourcePackage);
        updatePillIcon(context, notice.command);
        updateIconForPackage(pillIcon, context, notice.sourcePackage);
        setCompactMode(notice.command, MiniBridgeContract.COMMAND_CHARGE.equals(notice.command)
                ? title : MiniBridgeContract.COMMAND_CALL.equals(notice.command) ? (callConnected() ? "正在通话" : "来电") : "新消息");
        chatAction = notice.action;
        chatPackage = notice.sourcePackage;
        visibleKey = notice.key;
        if (root instanceof CameraIslandLayout) ((CameraIslandLayout) root).setCharging(MiniBridgeContract.COMMAND_CHARGE.equals(notice.command));
        int usable = Math.max(dp(200), context.getResources().getDisplayMetrics().widthPixels - dp(16));
        boolean call = MiniBridgeContract.COMMAND_CALL.equals(notice.command);
        int newWidth = Math.min(dp(call ? callSizes[0] : notice.pillWidth), usable), newHeight = dp(call ? callSizes[1] : notice.pillHeight);
        if (call) { bigW=Math.min(dp(callSizes[4]),usable); panelHeightLimit=dp(callSizes[5]); }
        else { bigW=baseBigW; panelHeightLimit=basePanelHeightLimit; }
        boolean sizeChanged = smallW != newWidth || smallH != newHeight;
        smallW = newWidth; smallH = newHeight;
        calculateExpandedHeight(detail);
        refreshCompactSafeArea();
        refreshMediaAppearance();
        refreshMediaControls();
        if (sizeChanged && !isExpanded && running == null && pendingFrame == null && pendingExit == null && !launchInProgress)
            morph(false);
    }

    private static String sourcePackageFromKey(String key) {
        if (key == null) return "";
        int first = key.indexOf('|'), second = first < 0 ? -1 : key.indexOf('|', first + 1);
        if (first < 0 || second < 0) return "";
        String pkg = key.substring(first + 1, second);
        return pkg.length() <= 200 && ("android".equals(pkg)
                || pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
                && !MiniBridgeContract.APP.equals(pkg) ? pkg : "";
    }

    private static String testPackage(String key) {
        for (int i = 0; i < 3; i++) if (("island:test:" + i).equals(key)) return "island.test." + i;
        return "";
    }

    private static int testPackageIndex(String pkg) {
        for (int i = 0; i < 3; i++) if (("island.test." + i).equals(pkg)) return i;
        return -1;
    }

    private static String appLabel(Context app, String pkg) {
        if (MessageCapsulePublisher.WECHAT.equals(pkg)) return "微信";
        if (MessageCapsulePublisher.QQ.equals(pkg)) return "QQ";
        if ("island.charge".equals(pkg)) return "充电";
        if (testPackageIndex(pkg) >= 0)
            return "模拟应用 " + (char) ('A' + testPackageIndex(pkg));
        try {
            PackageManager pm = app.getPackageManager();
            return scrub(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), 32);
        } catch (Throwable ignored) { return "应用"; }
    }

    private static void updateIconForPackage(ImageView image, Context app, String pkg) {
        if (image == null || app == null || pkg == null || pkg.isEmpty()) return;
        if ("island.charge".equals(pkg)) {
            image.setImageDrawable(new ChargingGlyphDrawable()); return;
        }
        if (testPackageIndex(pkg) >= 0) {
            int i = testPackageIndex(pkg);
            image.setImageResource(i == 0 ? android.R.drawable.ic_dialog_email
                    : i == 1 ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_menu_today);
            return;
        }
        try { image.setImageDrawable(app.getPackageManager().getApplicationIcon(pkg)); }
        catch (Throwable ignored) { }
    }

    private static String sizeSource(Notice notice) {
        return MiniBridgeContract.COMMAND_MUSIC.equals(notice.command) ? "island.media" : notice.sourcePackage;
    }

    private static String selectCandidate(String primary, boolean otherAppsOnly) {
        LinkedHashMap<String, String> packages = new LinkedHashMap<>();
        for (Notice n : ACTIVE.values()) packages.put(n.key, sizeSource(n));
        for (Notice n : ACTIVE.values()) {
            if (MiniBridgeContract.COMMAND_CALL.equals(n.command) && !n.key.equals(primary)
                    && (!otherAppsOnly || !java.util.Objects.equals(packages.get(primary), sizeSource(n)))) return n.key;
        }
        if (packages.containsKey(MiniBridgeContract.MUSIC_KEY)
                && !MiniBridgeContract.MUSIC_KEY.equals(primary)
                && (!otherAppsOnly || !java.util.Objects.equals(packages.get(primary), packages.get(MiniBridgeContract.MUSIC_KEY))))
            return MiniBridgeContract.MUSIC_KEY;
        return IslandPriority.select(packages, primary, appPriority, otherAppsOnly);
    }

    private static String sideKey() { return selectCandidate(visibleKey, true); }

    private static void updateSide() {
        if (!(root instanceof CameraIslandLayout) || context == null) return;
        String key = sideKey();
        Notice side = ACTIVE.get(key);
        Drawable icon = null;
        if (side != null) {
            try {
                if ("island.charge".equals(side.sourcePackage))
                    icon = new ChargingGlyphDrawable();
                else if (testPackageIndex(side.sourcePackage) >= 0) {
                    int i = testPackageIndex(side.sourcePackage);
                    icon = context.getDrawable(i == 0 ? android.R.drawable.ic_dialog_email
                            : i == 1 ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_menu_today);
                } else icon = context.getPackageManager().getApplicationIcon(side.sourcePackage);
            }
            catch (Throwable ignored) {
                GradientDrawable fallback = new GradientDrawable();
                fallback.setColor(Color.WHITE);
                fallback.setShape(GradientDrawable.OVAL);
                icon = fallback;
            }
        }
        ((CameraIslandLayout) root).setSide(key, icon, side == null ? null : () -> {
            Notice selected = ACTIVE.get(key);
            Notice current = ACTIVE.get(visibleKey);
            if (selected == null || pendingExit != null || aicyShowing
                    || (current != null && MiniBridgeContract.COMMAND_CALL.equals(current.command))) return;
            // Hold the old primary as the next side until the expanded card has
            // collapsed and the final primary has been selected.
            ((CameraIslandLayout) root).deferSideBirth();
            bindNotice(selected);
            updateSide();
            morph(true);
        });
    }

    /** Called for the exact source key Android reports as removed. */
    private static void removeSource(String key) {
        if (key == null) return;
        String successor = sideKey();
        SUPPRESSED_KEYS.remove(key);
        Notice removed = ACTIVE.remove(key);
        // Do not interrupt an in-flight exit morph and app launch.
        if (launchInProgress) return;
        if (removed == null) return;
        if (ACTIVE.isEmpty()) {
            animateExit(SystemUiAicyIsland::closeOrRestoreQueued);
            return;
        }
        // Removing a covered message must not interrupt the visible one.
        if (key.equals(visibleKey)) {
            Notice latest = ACTIVE.get(successor);
            if (latest == null) {
                String next = selectCandidate(key, false);
                latest = ACTIVE.get(next);
            }
            bindNotice(latest);
            updateSide();
            morph(false);
        } else {
            updateSide();
            if (!isExpanded) schedule(false);
        }
    }

    private static void installScreenOffListener(Context c) {
        screenOff = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                android.util.Log.i("FlymeIslandUnlock", "systemui screen off close");
                closedForScreenOff = true;
                pendingUnlockCycle = -1L;
                close();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        c.registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private static void close() {
        if (pendingExit != null) { finishPendingExit(); return; }
        MAIN.removeCallbacks(COLLAPSE);
        MAIN.removeCallbacks(HIDE);
        MAIN.removeCallbacks(EXPAND);
        MAIN.removeCallbacks(ROTATE);
        cancelFrame();
        stopCoverRotation();
        if (root != null) {
            root.animate().cancel();
            root.setTranslationX(0f);
            root.setTranslationY(0f);
            root.setAlpha(1f);
        }
        if (screenOff != null && context != null) {
            try { context.unregisterReceiver(screenOff); }
            catch (Throwable ignored) {}
        }
        screenOff = null;
        if (root != null && wm != null) {
            try { wm.removeViewImmediate(root); }
            catch (Throwable ignored) {}
        }
        root = null;
        wm = null;
        lp = null;
        context = null;
        fullContent = null;
        pillContent = null;
        mediaControls = null;
        callMedium = null;
        callMediumVisible = false;
        fullTitle = null;
        fullSubtitle = null;
        pillTitle = null;
        chargeLabel = null;
        messageText = null;
        pillIcon = null;
        contactAvatar = null;
        mediaWave = null;
        isExpanded = false;
        liveWindowType = 0;
        chatAction = null;
        chatPackage = null;
        actionReceivedAt = 0L;
        demoOnly = false;
        launchInProgress = false;
        visibleKey = null;
        ACTIVE.clear();
    }

    /** Defense-in-depth: reject control and bidirectional formatting. No logging. */
    private static String scrub(String input, int limit) {
        if (input == null) return "";
        String clean = input.replaceAll("[\\p{Cntrl}\\p{Cf}]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > limit) clean = clean.substring(0, limit);
        return clean;
    }

    private static void updatePillIcon(Context c, String command) {
        if (pillIcon == null) return;
        String pkg = MiniBridgeContract.COMMAND_WECHAT.equals(command)
                ? MessageCapsulePublisher.WECHAT
                : MiniBridgeContract.COMMAND_QQ.equals(command)
                ? MessageCapsulePublisher.QQ : null;
        if (pkg != null) {
            try {
                Drawable icon = c.getPackageManager().getApplicationIcon(pkg);
                pillIcon.setImageDrawable(icon);
                return;
            } catch (Throwable ignored) {
                // Missing/unavailable app icon: fall back to generic white circle.
            }
        }
        GradientDrawable fallback = new GradientDrawable();
        fallback.setColor(Color.WHITE);
        fallback.setShape(GradientDrawable.OVAL);
        pillIcon.setImageDrawable(fallback);
    }

    private static String compactText(String origin) {
        return "演示".equals(origin) ? "演示中" : origin + "新消息";
    }

    private static boolean isLocked(Context c) {
        if (c == null) return true;
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && (km.isKeyguardLocked() || km.isDeviceLocked());
    }

    private static TextView label(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private static int dp(int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void log(String msg) {
        try { XposedBridge.log(TAG + msg); }
        catch (Throwable ignored) {}
    }
}

