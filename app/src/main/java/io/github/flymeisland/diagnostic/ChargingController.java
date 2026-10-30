package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Main-thread battery state machine. No polling, no background foreground service. */
public final class ChargingController {
    private static final String TAG = "FlymeIslandCharge";
    private static final long WINDOW_MS = 2_000L;
    private static boolean initialized;
    private static boolean previousPlugged;
    private static boolean active;
    private static long expiresAt;
    private static int lastPercent = -1;
    private static boolean lastFull;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable expiry;

    private ChargingController() {}

    public static Intent batterySnapshot(Context context) {
        return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static String describe(Context context) {
        Intent battery = batterySnapshot(context);
        if (battery == null) return "暂时无法取得电池状态";
        int level = percent(battery);
        boolean plugged = isPlugged(battery);
        boolean full = isFull(battery);
        return (plugged ? (full ? "已充满" : "已连接电源") : "未连接电源")
                + " · 电量 " + (level < 0 ? "未知" : level + "%");
    }

    /**
     * Called for sticky ACTION_BATTERY_CHANGED and registered ACTION_POWER events.
     * Duplicate broadcasts won't retrigger the capsule within the same charging session.
     */
    public static void onBatteryEvent(Context context, Intent event) {
        if (event == null) return;
        if (Intent.ACTION_POWER_DISCONNECTED.equals(event.getAction())) {
            initialized = true;
            previousPlugged = false;
            close(context);
            return;
        }
        if (!MusicPrefs.chargingEnabled(context)) {
            initialized = false;
            previousPlugged = false;
            close(context);
            return;
        }
        Intent battery = Intent.ACTION_BATTERY_CHANGED.equals(event.getAction())
                ? event : batterySnapshot(context);
        if (battery == null) return;
        boolean plugged = isPlugged(battery);
        int level = percent(battery);
        boolean full = isFull(battery);

        if (!plugged) {
            initialized = true;
            previousPlugged = false;
            close(context);
            return;
        }
        boolean newConnection = !initialized || !previousPlugged;
        initialized = true;
        previousPlugged = true;
        long now = SystemClock.elapsedRealtime();
        if (newConnection) {
            expiresAt = now + WINDOW_MS;
            active = true;
            lastPercent = level;
            lastFull = full;
            post(context, level, full, WINDOW_MS);
        } else if (active && now < expiresAt
                && (lastPercent != level || lastFull != full)) {
            lastPercent = level;
            lastFull = full;
            post(context, level, full, expiresAt - now);
        } else if (active && now >= expiresAt) {
            active = false;
        }
    }

    public static String testIfPlugged(Context context) {
        if (!MusicPrefs.chargingEnabled(context))
            return "请先开启充电胶囊开关";
        Intent battery = batterySnapshot(context);
        if (battery == null) return "无法获取电池数据";
        if (!isPlugged(battery)) return "目前未连接充电器，不发送测试胶囊";
        initialized = true;
        previousPlugged = true;
        active = true;
        lastPercent = percent(battery);
        lastFull = isFull(battery);
        expiresAt = SystemClock.elapsedRealtime() + WINDOW_MS;
        post(context, lastPercent, lastFull, WINDOW_MS);
        return "已请求短暂显示充电胶囊";
    }

    public static void restoreIfActive(Context context) {
        long remaining = expiresAt - SystemClock.elapsedRealtime();
        if (active && remaining > 0 && MusicPrefs.chargingEnabled(context))
            post(context, lastPercent, lastFull, remaining);
    }

    public static void onDisabled(Context context) {
        initialized = false;
        previousPlugged = false;
        close(context);
    }

    private static void post(Context context, int percent, boolean full, long remain) {
        try {
            SystemUiAicyClient.showCharge(context, percent, full);
            if (expiry != null) MAIN.removeCallbacks(expiry);
            Context app = context.getApplicationContext();
            expiry = () -> {
                active = false;
                SystemUiAicyClient.removeState(app, MiniBridgeContract.CHARGE_KEY);
                expiry = null;
            };
            MAIN.postDelayed(expiry, remain);
        } catch (RuntimeException e) {
            Log.w(TAG, "Charging capsule update failed: " + e.getClass().getSimpleName());
            active = false;
        }
    }

    private static void close(Context context) {
        active = false;
        expiresAt = 0;
        lastPercent = -1;
        if (expiry != null) MAIN.removeCallbacks(expiry);
        expiry = null;
        SystemUiAicyClient.removeState(context, MiniBridgeContract.CHARGE_KEY);
    }

    private static boolean isPlugged(Intent battery) {
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }
    private static boolean isFull(Intent battery) {
        return battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                == BatteryManager.BATTERY_STATUS_FULL;
    }
    private static int percent(Intent battery) {
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return -1;
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }
}
