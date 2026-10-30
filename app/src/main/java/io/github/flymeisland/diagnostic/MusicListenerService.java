package io.github.flymeisland.diagnostic;

import android.content.ComponentName;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Active media session observer plus opt-in charging and message event observer.
 * Selected notification text and playback metadata stay on the device.
 * Publication is coalesced (160 ms); pause/temporary loss receives a 500 ms
 * grace period to avoid flicker during track changes.
 */
public final class MusicListenerService extends NotificationListenerService {
    private static final String TAG = "FlymeIslandMusic";

    private static final long COALESCE_MS = 160L;
    private static final long STOP_GRACE_MS = 500L;
    private Handler main;
    private MediaSessionManager sessions;
    private MediaController selected;
    private boolean mediaPublished;
    private AudioWaveCapture waveCapture;
    private static MusicListenerService liveInstance;
    static void stopMixedWaveNow() {
        if (liveInstance != null && liveInstance.waveCapture != null) liveInstance.waveCapture.stop();
    }
    private final java.util.Map<android.media.session.MediaSession.Token, MediaController> observed = new java.util.HashMap<>();
    private boolean connected;
    private boolean chargeReceiverRegistered;
    private boolean unlockReceiverRegistered;
    private boolean screenRelayRegistered;
    private boolean openedDismissReceiverRegistered;
    private boolean mediaGestureReceiverRegistered;
    private final BroadcastReceiver mediaGestureReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent request) {
            if (!connected || request == null || (!MiniBridgeContract.ACTION_MEDIA_GESTURE.equals(request.getAction())
                    && !MiniBridgeContract.ACTION_MEDIA_CONTROL.equals(request.getAction())
                    && !MiniBridgeContract.ACTION_CALL_CONTROL.equals(request.getAction()))) return;
            try {
                int expected = getPackageManager().getPackageUid(MiniBridgeContract.SYSTEM_UI, 0);
                String sender = getSentFromPackage();
                long sent = request.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1L);
                String pkg = request.getStringExtra(MiniBridgeContract.EXTRA_SOURCE_PACKAGE);
                String direction = request.getStringExtra(MiniBridgeContract.EXTRA_MEDIA_DIRECTION);
                if (MiniBridgeContract.ACTION_CALL_CONTROL.equals(request.getAction())) {
                    long age = SystemClock.elapsedRealtime() - sent;
                    String key = request.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY);
                    if (getSentFromUid()!=expected || getSentFromUid()==Process.INVALID_UID
                            || (sender!=null && !MiniBridgeContract.SYSTEM_UI.equals(sender))
                            || sent<0 || age<0 || age>5000 || !MusicPrefs.callEnabled(MusicListenerService.this)
                            || !SystemUiAicyClient.enabled(MusicListenerService.this) || key==null
                            || !("answer".equals(direction) || "reject".equals(direction) || "hangup".equals(direction) || "speaker_on".equals(direction) || "speaker_off".equals(direction))) return;
                    performCallAction(key, direction);
                    return;
                }
                if (MiniBridgeContract.ACTION_MEDIA_CONTROL.equals(request.getAction())) {
                    long age=SystemClock.elapsedRealtime()-sent;
                    if (getSentFromUid()!=expected || getSentFromUid()==Process.INVALID_UID
                            || (sender!=null && !MiniBridgeContract.SYSTEM_UI.equals(sender))
                            || sent<0 || age<0 || age>5000 || !SystemUiAicyClient.enabled(MusicListenerService.this)
                            || selected==null || pkg==null || !pkg.equals(selected.getPackageName())) return;
                    controlSelected(direction); return;
                }
                if (!SystemUiAicyClient.enabled(MusicListenerService.this)
                        || !MediaGestureAuthorization.allowed(getSentFromUid(), expected, Process.INVALID_UID,
                        sender, MiniBridgeContract.SYSTEM_UI, SystemClock.elapsedRealtime(), sent, pkg,
                        direction, request.getBooleanExtra(MiniBridgeContract.EXTRA_MEDIA_GESTURE_SOURCE, false)
                                ? GestureAppPrefs.mediaEnabled(MusicListenerService.this)
                                  && selected != null && pkg != null && pkg.equals(selected.getPackageName())
                                : GestureAppPrefs.enabled(MusicListenerService.this, pkg))) return;
                controlMediaForApp(pkg, "next".equals(direction));
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException failure) {
                Log.w(TAG, "Media gesture rejected: " + failure.getClass().getSimpleName());
            }
        }
    };
    /** Only a real SystemUI-originated user click may cancel one original item. */
    private final BroadcastReceiver openedDismissReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent request) {
            if (request == null ||
                    !MiniBridgeContract.ACTION_OPENED_DISMISS.equals(request.getAction())
                    || !connected) return;
            try {
                int expectedUid = getPackageManager()
                        .getPackageUid(MiniBridgeContract.SYSTEM_UI, 0);
                int senderUid = getSentFromUid();
                if (senderUid == Process.INVALID_UID || senderUid != expectedUid) return;
                String senderPkg = getSentFromPackage();
                if (senderPkg != null &&
                        !MiniBridgeContract.SYSTEM_UI.equals(senderPkg)) return;
                long timestamp = request.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1L);
                long age = SystemClock.elapsedRealtime() - timestamp;
                if (timestamp < 0 || age < 0 || age > 10000L) return;
                String key = request.getStringExtra(MiniBridgeContract.EXTRA_ISLAND_SOURCE_KEY);
                if (key == null || key.isEmpty() || key.length() > 500) return;
                StatusBarNotification[] all = getActiveNotifications();
                if (all == null) return;
                for (StatusBarNotification item : all) {
                    if (item == null || !item.isClearable() ||
                            !key.equals(item.getKey())) continue;
                    String pkg = item.getPackageName();
                    if (!MessageCapsulePublisher.WECHAT.equals(pkg)
                            && !MessageCapsulePublisher.QQ.equals(pkg)) return;
                    // NotificationListenerService can only cancel an item that
                    // is still active; its remove callback updates our island.
                    cancelNotification(key);
                    return;
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException
                    | RuntimeException error) {
                Log.w(TAG, "Opened-message cleanup unavailable: "
                        + error.getClass().getSimpleName());
            }
        }
    };
    private long lastUnlockRestoreAt = -1L;
    private long unlockWaitStartedAt = -1L;
    private final Runnable unlockRestore = this::restoreAfterUnlock;
    private void restoreAfterUnlock() {
        if (!connected || !SystemUiAicyClient.enabled(this)) { Log.i("FlymeIslandUnlock", "restore skipped disconnected or disabled"); return; }
        KeyguardManager km = getSystemService(KeyguardManager.class);
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        if (pm == null || !pm.isInteractive()) { Log.i("FlymeIslandUnlock", "restore skipped screen off"); return; }
        if (km == null || km.isKeyguardLocked() || km.isDeviceLocked()) {
            if (unlockWaitStartedAt >= 0 && SystemClock.elapsedRealtime() - unlockWaitStartedAt < 15000L)
                main.postDelayed(unlockRestore, 350L);
            return;
        }
        unlockWaitStartedAt = -1L;
        long cycle = SystemClock.elapsedRealtime();
        if (lastUnlockRestoreAt >= 0 && cycle - lastUnlockRestoreAt < 2000L) return;
        lastUnlockRestoreAt = cycle;
        Log.i("FlymeIslandUnlock", "unlocked restore start");
        SystemUiAicyClient.replayAfterUnlock(this, cycle);
        restoreActiveIslands();
        requestScan();
        ChargingController.restoreIfActive(this);
        main.postDelayed(() -> { if (connected && lastUnlockRestoreAt == cycle) SystemUiAicyClient.replayAfterUnlock(this, cycle); }, 950L);
        main.postDelayed(() -> { if (connected && lastUnlockRestoreAt == cycle) SystemUiAicyClient.replayAfterUnlock(this, cycle); }, 1900L);
    }
    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent event) {
            if (event == null || main == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(event.getAction())) {
                Log.i("FlymeIslandUnlock", "service screen off");
                lastUnlockRestoreAt = -1L;
                unlockWaitStartedAt = -1L;
                main.removeCallbacks(unlockRestore);
                return;
            }
            if (Intent.ACTION_USER_PRESENT.equals(event.getAction()) || Intent.ACTION_SCREEN_ON.equals(event.getAction())) {
                Log.i("FlymeIslandUnlock", "service " + (Intent.ACTION_USER_PRESENT.equals(event.getAction()) ? "user present" : "screen on"));
                main.removeCallbacks(unlockRestore);
                unlockWaitStartedAt = SystemClock.elapsedRealtime();
                main.postDelayed(unlockRestore, 350L);
            }
        }
    };
    private final BroadcastReceiver screenRelay = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent event) {
            if (!connected || event == null || !MiniBridgeContract.ACTION_SCREEN_RELAY.equals(event.getAction())) return;
            try {
                int uid = getPackageManager().getPackageUid(MiniBridgeContract.SYSTEM_UI, 0);
                String sender = getSentFromPackage();
                long sent = event.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1L);
                long age = SystemClock.elapsedRealtime() - sent;
                String state = event.getStringExtra(MiniBridgeContract.EXTRA_SCREEN_STATE);
                if (!ScreenRelayPolicy.accept(getSentFromUid(), uid, Process.INVALID_UID, sender,
                        MiniBridgeContract.SYSTEM_UI, sent, age, state)) return;
                Log.i("FlymeIslandUnlock", "trusted systemui screen relay received");
                unlockReceiver.onReceive(c, new Intent(state));
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) {}
        }
    };
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            ChargingController.onBatteryEvent(context, intent);
        }
    };


    private final Runnable rescan = this::scanSessions;
    private final Runnable publish = this::publishCurrent;
    private final Runnable conditionalStop = () -> {
        if (!connected || !MusicPrefs.enabled(this) || selected == null) {
            mediaPublished = false;
            SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (prefs, key) -> {
        if (main == null) return;
        if ("spotify_enabled".equals(key) || "systemui_island_enabled_v023".equals(key)
                || "real_audio_wave_v037".equals(key) || GestureAppPrefs.KEY.equals(key)) {
            main.post(this::requestScan);
        }
        if ("charging_enabled".equals(key) || "systemui_island_enabled_v023".equals(key)) {
            main.post(() -> {
                if (MusicPrefs.chargingEnabled(this) && SystemUiAicyClient.enabled(this))
                    ChargingController.onBatteryEvent(this, ChargingController.batterySnapshot(this));
                else ChargingController.onDisabled(this);
            });
        }
        if (key != null && key.startsWith("wave_style_v040_")) main.post(this::requestPublish);
        if (AppSelectionPrefs.KEY.equals(key) || "systemui_island_enabled_v023".equals(key))
            main.postDelayed(this::restoreActiveIslands, 100L);
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionListener =
            controllers -> requestScan();

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            requestScan();
        }
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            requestPublish();
        }
        @Override public void onSessionDestroyed() {
            requestScan();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        liveInstance = this;
        main = new Handler(Looper.getMainLooper());
        waveCapture = new AudioWaveCapture(this);
        MusicPrefs.get(this).registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        main.post(() -> {
            registerChargeReceiver();
            registerUnlockReceiver();
            registerScreenRelay();
            registerOpenedDismissReceiver();
            registerMediaGestureReceiver();
            LegacyCleanup.removeOldOwnCapsules(this);
            try {
                sessions = getSystemService(MediaSessionManager.class);
                if (sessions != null) {
                    sessions.addOnActiveSessionsChangedListener(sessionListener,
                            new ComponentName(this, MusicListenerService.class), main);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Media session access denied");
            } catch (RuntimeException e) {
                Log.w(TAG, "Media session registration failed");
            }
            requestScan();
            main.postDelayed(this::restoreActiveIslands, 1200L);
        });
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        if (main != null) main.post(() -> {
            main.removeCallbacks(rescan);
            main.removeCallbacks(publish);
            main.removeCallbacks(conditionalStop);
            detach();
            unregisterChargeReceiver();
            unregisterUnlockReceiver();
            unregisterScreenRelay();
            unregisterOpenedDismissReceiver();
            unregisterMediaGestureReceiver();
            SystemUiAicyClient.stop(this, null);
            ChargingController.onDisabled(this);
            SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
        });
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (CallNotification.matches(sbn.getPackageName(), sbn.getNotification())) {
            if (SystemUiAicyClient.enabled(this) && MusicPrefs.callEnabled(this)) SystemUiAicyClient.notifyMessage(this, sbn);
            return;
        }
        if (sbn.getNotification() != null && hasMediaSession(sbn.getNotification())) requestScan();
        if (!SystemUiAicyClient.enabled(this) || !AppSelectionPrefs.enabled(this, sbn.getPackageName())
                || MiniBridgeContract.APP.equals(sbn.getPackageName()) || !eligibleOther(sbn)) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!MusicPrefs.respectDnd(this) || manager == null
                || !MessageCapsulePublisher.inDoNotDisturb(manager))
            SystemUiAicyClient.notifyMessage(this, sbn);
    }

    private static boolean hasMediaSession(Notification n) {
        return n != null && n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION);
    }

    private boolean eligibleOther(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return false;
        // The media-session entry represents media notifications while enabled.
        return !(MusicPrefs.enabled(this) && hasMediaSession(n));
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && sbn.getNotification() != null && hasMediaSession(sbn.getNotification())) requestScan();
        if (sbn != null) SystemUiAicyClient.notificationRemoved(this, sbn);
    }

    private void performCallAction(String key, String direction) {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification item : active) {
                if (item == null || !key.equals(item.getKey()) || !"com.android.incallui".equals(item.getPackageName())) continue;
                Notification n = item.getNotification();
                if (!CallNotification.matches(item.getPackageName(), n) || n.actions == null) return;
                CallActionPolicy.Kind wanted = "answer".equals(direction) ? CallActionPolicy.Kind.ANSWER
                        : "reject".equals(direction) ? CallActionPolicy.Kind.REJECT
                        : "hangup".equals(direction) ? CallActionPolicy.Kind.HANGUP
                        : "speaker_on".equals(direction) ? CallActionPolicy.Kind.SPEAKER_ON : CallActionPolicy.Kind.SPEAKER_OFF;
                for (Notification.Action action : n.actions) {
                    if (action == null || action.actionIntent == null || action.title == null) continue;
                    if (CallActionPolicy.classify(action.title.toString()) != wanted) continue;
                    if (!"com.android.incallui".equals(action.actionIntent.getCreatorPackage())) continue;
                    action.actionIntent.send();
                    return;
                }
            }
        } catch (android.app.PendingIntent.CanceledException | RuntimeException ignored) { }
    }

    private void registerMediaGestureReceiver() {
        if (mediaGestureReceiverRegistered || Build.VERSION.SDK_INT < 34) return;
        try {
            IntentFilter filter = new IntentFilter(MiniBridgeContract.ACTION_MEDIA_GESTURE);
            filter.addAction(MiniBridgeContract.ACTION_MEDIA_CONTROL);
            filter.addAction(MiniBridgeContract.ACTION_CALL_CONTROL);
            registerReceiver(mediaGestureReceiver, filter, Context.RECEIVER_EXPORTED);
            mediaGestureReceiverRegistered = true;
        } catch (RuntimeException failure) { Log.w(TAG, "Media gesture receiver unavailable"); }
    }
    private void unregisterMediaGestureReceiver() {
        if (!mediaGestureReceiverRegistered) return;
        try { unregisterReceiver(mediaGestureReceiver); } catch (RuntimeException ignored) {}
        mediaGestureReceiverRegistered = false;
    }
    private void controlMediaForApp(String pkg, boolean next) {
        MediaController target = null;
        for (MediaController controller : observed.values()) {
            if (!pkg.equals(controller.getPackageName())) continue;
            if (target == null || isPlaying(controller)) target = controller;
            if (isPlaying(controller)) break;
        }
        if (target == null) return;
        try {
            PlaybackState state = target.getPlaybackState();
            long action = next ? PlaybackState.ACTION_SKIP_TO_NEXT : PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            if (state == null || (state.getActions() & action) == 0) return;
            if (next) target.getTransportControls().skipToNext();
            else target.getTransportControls().skipToPrevious();
        } catch (RuntimeException failure) { Log.w(TAG, "Media gesture action unavailable"); }
    }

    private void controlSelected(String action) {
        if (selected == null || action == null || action.length() > 200) return;
        try {
            PlaybackState state = selected.getPlaybackState();
            if (state == null) return;
            long flags=state.getActions(); MediaController.TransportControls controls=selected.getTransportControls();
            if ("previous".equals(action) && (flags & PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0) controls.skipToPrevious();
            else if ("next".equals(action) && (flags & PlaybackState.ACTION_SKIP_TO_NEXT)!=0) controls.skipToNext();
            else if ("toggle".equals(action)) {
                if (isPlaying(selected) && (flags & (PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0) controls.pause();
                else if (!isPlaying(selected) && (flags & (PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0) controls.play();
            } else if (action.startsWith("custom:")) {
                String id=action.substring(7);
                for (PlaybackState.CustomAction item:state.getCustomActions())
                    if (id.equals(item.getAction())) { controls.sendCustomAction(item,item.getExtras()); break; }
            }
        } catch (RuntimeException failure) { Log.w(TAG,"Media control unavailable"); }
    }

    private void registerOpenedDismissReceiver() {
        if (openedDismissReceiverRegistered || Build.VERSION.SDK_INT < 34) return;
        try {
            IntentFilter filter =
                    new IntentFilter(MiniBridgeContract.ACTION_OPENED_DISMISS);
            registerReceiver(openedDismissReceiver, filter, Context.RECEIVER_EXPORTED);
            openedDismissReceiverRegistered = true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "Opened-message cleanup receiver unavailable");
        }
    }

    private void unregisterOpenedDismissReceiver() {
        if (!openedDismissReceiverRegistered) return;
        try { unregisterReceiver(openedDismissReceiver); }
        catch (RuntimeException ignored) {}
        openedDismissReceiverRegistered = false;
    }

    private void registerScreenRelay() {
        if (screenRelayRegistered || Build.VERSION.SDK_INT < 34) return;
        try {
            registerReceiver(screenRelay, new IntentFilter(MiniBridgeContract.ACTION_SCREEN_RELAY), Context.RECEIVER_EXPORTED);
            screenRelayRegistered = true;
        } catch (RuntimeException error) { Log.w(TAG, "screen relay receiver unavailable"); }
    }
    private void unregisterScreenRelay() {
        if (!screenRelayRegistered) return;
        try { unregisterReceiver(screenRelay); } catch (RuntimeException ignored) {}
        screenRelayRegistered = false;
    }

    private void registerUnlockReceiver() {
        if (unlockReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(unlockReceiver, filter);
            unlockReceiverRegistered = true;
        } catch (RuntimeException ignored) {}
    }

    private void unregisterUnlockReceiver() {
        if (!unlockReceiverRegistered) return;
        try { unregisterReceiver(unlockReceiver); }
        catch (RuntimeException ignored) {}
        unlockReceiverRegistered = false;
    }

    /** Recreate only the compact island after unlock / listener reconnect. */
    private void restoreActiveIslands() {
        if (!connected || !SystemUiAicyClient.enabled(this)) return;
        KeyguardManager lock = getSystemService(KeyguardManager.class);
        if (lock != null && (lock.isDeviceLocked() || lock.isKeyguardLocked())) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (MusicPrefs.respectDnd(this) && manager != null
                && MessageCapsulePublisher.inDoNotDisturb(manager)) return;
        StatusBarNotification[] all;
        try { all = getActiveNotifications(); }
        catch (RuntimeException denied) { return; }
        if (all == null) return;
        java.util.List<String> priorities = PriorityPrefs.get(this);
        Arrays.sort(all, (a, b) -> {
            if (a == null) return b == null ? 0 : 1;
            if (b == null) return -1;
            int ar = priorities.indexOf(a.getPackageName());
            int br = priorities.indexOf(b.getPackageName());
            if (ar < 0) ar = Integer.MAX_VALUE;
            if (br < 0) br = Integer.MAX_VALUE;
            int ranked = Integer.compare(ar, br);
            return ranked != 0 ? ranked : Long.compare(b.getPostTime(), a.getPostTime());
        });
        int count = 0;
        for (StatusBarNotification sbn : all) {
            if (sbn == null || MiniBridgeContract.APP.equals(sbn.getPackageName())
                    || !AppSelectionPrefs.enabled(this, sbn.getPackageName())
                    || !eligibleOther(sbn)) continue;
            SystemUiAicyClient.notifyMessage(this, sbn, true);
            if (++count == 32) break;
        }
        Log.i("FlymeIslandUnlock", "restored selected notification count=" + count);
    }

    private void registerChargeReceiver() {
        if (chargeReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(batteryReceiver, filter);
            chargeReceiverRegistered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Charging events unavailable: " + e.getClass().getSimpleName());
        }
    }

    private void unregisterChargeReceiver() {
        if (!chargeReceiverRegistered) return;
        try { unregisterReceiver(batteryReceiver); }
        catch (RuntimeException ignored) {}
        chargeReceiverRegistered = false;
    }

    private void requestScan() {
        if (main == null) return;
        main.removeCallbacks(rescan);
        main.postDelayed(rescan, COALESCE_MS);
    }

    private void requestPublish() {
        if (main == null) return;
        main.removeCallbacks(publish);
        main.postDelayed(publish, COALESCE_MS);
    }

    private void scanSessions() {
        if (!connected || (!MusicPrefs.enabled(this) && GestureAppPrefs.get(this).isEmpty())) {
            main.removeCallbacks(conditionalStop);
            detach();
            SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
            return;
        }
        List<MediaController> controllers = Collections.emptyList();
        try {
            if (sessions == null) sessions = getSystemService(MediaSessionManager.class);
            if (sessions != null) {
                List<MediaController> found = sessions.getActiveSessions(
                        new ComponentName(this, MusicListenerService.class));
                if (found != null) controllers = found;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Media sessions unavailable");
        } catch (RuntimeException e) {
            Log.w(TAG, "Media session query failed");
        }

        java.util.Set<android.media.session.MediaSession.Token> live = new java.util.HashSet<>();
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            android.media.session.MediaSession.Token token = controller.getSessionToken();
            live.add(token);
            if (!observed.containsKey(token)) {
                try { controller.registerCallback(callback, main); observed.put(token, controller); }
                catch (RuntimeException e) { Log.w(TAG, "Media callback registration failed"); }
            }
        }
        for (android.media.session.MediaSession.Token token : new java.util.ArrayList<>(observed.keySet())) {
            if (!live.contains(token)) {
                try { observed.get(token).unregisterCallback(callback); } catch (RuntimeException ignored) {}
                observed.remove(token);
            }
        }
        MediaController candidate = null;
        for (MediaController controller : controllers) {
            if (controller != null && isPlaying(controller)) { candidate = controller; break; }
        }
        if (candidate == null && mediaPublished && selected != null) {
            for (MediaController controller : controllers)
                if (controller != null && sameSession(controller, selected)) { candidate = controller; break; }
        }
        selected = candidate;
        requestPublish();
    }

    private static boolean sameSession(MediaController a, MediaController b) {
        if (a == null || b == null) return a == b;
        return a.getSessionToken().equals(b.getSessionToken());
    }

    private static boolean isPlaying(MediaController controller) {
        if (controller == null) return false;
        try {
            PlaybackState state = controller.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void publishCurrent() {
        if (!connected || !MusicPrefs.enabled(this)) {
            main.removeCallbacks(conditionalStop);
            if (waveCapture != null) waveCapture.stop();
            AppAudioWaveService.updatePlayer(null, false);
            mediaPublished = false;
            SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
            return;
        }
        if (selected == null) {
            AppAudioWaveService.updatePlayer(null, false);
            if (waveCapture != null) waveCapture.stop();
            main.removeCallbacks(conditionalStop);
            main.postDelayed(conditionalStop, STOP_GRACE_MS);
            return;
        }
        boolean playing = isPlaying(selected);
        AppAudioWaveService.updatePlayer(selected.getPackageName(), playing && SystemUiAicyClient.enabled(this));
        if (waveCapture != null) waveCapture.update(playing && SystemUiAicyClient.enabled(this));
        if (!playing && !mediaPublished) return;
        main.removeCallbacks(conditionalStop);
        try {
            MediaMetadata metadata = selected.getMetadata();
            String title = metadata == null ? null :
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null && metadata != null) {
                title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            }
            String artist = metadata == null ? null :
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            byte[] artwork = MediaArtworkExtractor.get(this, metadata, selected.getPackageName());
            PlaybackState state = selected.getPlaybackState();
            long actions = state == null ? 0L : state.getActions();
            java.util.ArrayList<String> ids = new java.util.ArrayList<>(), labels = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> icons = new java.util.ArrayList<>();
            if (state != null) for (PlaybackState.CustomAction item : state.getCustomActions()) {
                String id=item.getAction(), label=item.getName()==null ? "" : item.getName().toString();
                if (id == null || id.isEmpty() || id.length()>180 || label.isEmpty()) continue;
                ids.add(id); labels.add(label.length()>12 ? label.substring(0,12) : label); icons.add(item.getIcon());
                if (ids.size()==2) break;
            }
            android.app.PendingIntent target=selected.getSessionActivity();
            if (target == null) try {
                StatusBarNotification[] list=getActiveNotifications();
                if (list != null) for (StatusBarNotification item:list) {
                    if (item != null && selected.getPackageName().equals(item.getPackageName())
                            && hasMediaSession(item.getNotification()) && item.getNotification().contentIntent != null) {
                        target=item.getNotification().contentIntent; break;
                    }
                }
            } catch (RuntimeException ignored) {}
            Log.i("FlymeIslandUnlock", "media publish playing=" + playing + " restore=" + mediaPublished);
            SystemUiAicyClient.showMusic(this, selected.getPackageName(), title, artist, artwork, playing, mediaPublished, target, actions, ids, labels, icons);
            mediaPublished = true;
        } catch (SecurityException e) {
            Log.w(TAG, "Media metadata permission denied");
            mediaPublished = false;
            SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
        } catch (RuntimeException e) {
            Log.w(TAG, "Media session temporarily unavailable");
            main.removeCallbacks(conditionalStop);
            main.postDelayed(conditionalStop, STOP_GRACE_MS);
        }
    }

    private void detach() {
        AppAudioWaveService.updatePlayer(null, false);
        if (waveCapture != null) waveCapture.stop();
        for (MediaController controller : observed.values()) {
            try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) {}
        }
        observed.clear();
        selected = null;
        mediaPublished = false;
    }

    @Override public void onDestroy() {
        if (liveInstance == this) liveInstance = null;
        connected = false;
        if (main != null) {
            main.removeCallbacks(rescan);
            main.removeCallbacks(publish);
            main.removeCallbacks(conditionalStop);
        }
        MusicPrefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        detach();
        unregisterChargeReceiver();
        unregisterUnlockReceiver();
        unregisterScreenRelay();
        unregisterOpenedDismissReceiver();
        unregisterMediaGestureReceiver();
        SystemUiAicyClient.stop(this, null);
        ChargingController.onDisabled(this);
        if (sessions != null) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionListener);
            } catch (RuntimeException ignored) {}
        }
        SystemUiAicyClient.removeState(this, MiniBridgeContract.MUSIC_KEY);
        super.onDestroy();
    }
}
