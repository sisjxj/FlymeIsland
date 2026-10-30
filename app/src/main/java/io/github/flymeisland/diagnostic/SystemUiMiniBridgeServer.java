package io.github.flymeisland.diagnostic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XposedBridge;

/**
 * Receiver inside the SystemUI process. Its read-only UID probe remains intact;
 * additionally dispatches a separate, UID-authenticated, explicitly opt-in
 * system-window renderer with fixed commands and bounded lifetime.
 */
public final class SystemUiMiniBridgeServer {
    private static final AtomicBoolean registered = new AtomicBoolean(false);
    private static BroadcastReceiver receiver;
    private static final AtomicInteger probeCount = new AtomicInteger();
    private static final String TAG = "[FlymeIsland/Bridge v0.17.1] ";

    private SystemUiMiniBridgeServer() {}

    public static String register(Context application) {
        if (!MiniBridgeContract.SYSTEM_UI.equals(application.getPackageName()))
            return "wrong package";
        if (!registered.compareAndSet(false, true)) return "already registered";
        try {
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    if (intent != null
                            && MiniBridgeContract.ACTION_AICY_PRESENCE.equals(intent.getAction())) {
                        AicyPresenceBridge.onSystemUiSignal(c, this, intent);
                    } else if (intent != null
                            && MiniBridgeContract.ACTION_SYSTEM_ISLAND.equals(intent.getAction())) {
                        SystemUiAicyIsland.onRequest(c, this, intent);
                    } else if (intent != null
                            && MiniBridgeContract.ACTION_CAMERA_ANCHOR.equals(intent.getAction())) {
                        SystemUiCameraAnchorProbe.onRequest(c, this, intent);
                    } else {
                        onProbe(c, this, intent);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(MiniBridgeContract.ACTION_PROBE);
            filter.addAction(MiniBridgeContract.ACTION_CAMERA_ANCHOR);
            filter.addAction(MiniBridgeContract.ACTION_SYSTEM_ISLAND);
            filter.addAction(MiniBridgeContract.ACTION_AICY_PRESENCE);
            if (Build.VERSION.SDK_INT < 34) {
                registered.set(false);
                return "API lower than 34";
            }
            application.registerReceiver(receiver, filter, null,
                    new Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED);
            SystemUiUnlockBridge.register(application);
            return "registered; uid=" + Process.myUid();
        } catch (Throwable failed) {
            registered.set(false);
            receiver = null;
            return "registration failed: " + failed.getClass().getSimpleName();
        }
    }

    private static void onProbe(Context c, BroadcastReceiver br, Intent i) {
        if (i == null || !MiniBridgeContract.ACTION_PROBE.equals(i.getAction()))
            return;
        try {
            int sender = br.getSentFromUid();
            int authorized = c.getPackageManager().getPackageUid(MiniBridgeContract.APP, 0);
            // Sender UID is furnished by Android. INVALID_UID is never accepted.
            if (sender == Process.INVALID_UID) {
                trace("probe rejected: Android did not provide sender UID");
                return;
            }
            if (sender != authorized) {
                trace("probe rejected: sender UID not Flyme Island");
                return;
            }

            String fromPackage = br.getSentFromPackage();
            if (fromPackage != null && !MiniBridgeContract.APP.equals(fromPackage)) {
                trace("probe rejected: sender package mismatch");
                return;
            }

            long sent = i.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1);
            long age = SystemClock.elapsedRealtime() - sent;
            if (sent < 0 || age < 0 || age > 10_000) {
                trace("probe rejected: timestamp invalid/expired");
                return;
            }

            ResultReceiver reply;
            if (Build.VERSION.SDK_INT >= 33) {
                reply = i.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY,
                        ResultReceiver.class);
            } else {
                reply = i.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY);
            }
            if (reply == null) {
                trace("probe rejected: reply missing");
                return;
            }

            int myUid = Process.myUid();
            int hasPrivileged = c.getPackageManager().checkPermission(
                    "android.permission.START_TASKS_FROM_RECENTS",
                    MiniBridgeContract.SYSTEM_UI);
            boolean classVisible;
            try {
                Class<?> ext = Class.forName("flyme.view.WindowManagerExt");
                ext.getMethod("getInstance", Context.class);
                ext.getMethod("setStartWindowMode", String.class);
                classVisible = true;
            } catch (Throwable ignored) {
                classVisible = false;
            }

            String report = "Flyme Island v0.17.1 · SystemUI 通信核验"
                    + "\n请求来源 UID=" + sender
                    + "\n已核对 Flyme Island UID=" + authorized
                    + "\nSystemUI 执行 UID=" + myUid
                    + "\nSTART_TASKS_FROM_RECENTS="
                    + (hasPrivileged == PackageManager.PERMISSION_GRANTED
                        ? "GRANTED" : "DENIED")
                    + "\nWindowManagerExt 方法可见=" + classVisible
                    + "\n认证=系统 UID 匹配、请求未过期"
                    + "\n动作=仅只读核验；未执行任何小窗启动";
            Bundle result = new Bundle();
            result.putString(MiniBridgeContract.EXTRA_REPORT, report);
            reply.send(0, result);
            trace("authenticated read-only response sent");
        } catch (Throwable error) {
            // Never crash SystemUI or grant an action on malformed inputs.
            trace("probe error: " + error.getClass().getSimpleName());
        }
    }

    private static void trace(String state) {
        int n = probeCount.incrementAndGet();
        if (n > 12 && n % 32 != 0) return;
        try { XposedBridge.log(TAG + state + " #" + n); }
        catch (Throwable ignored) {}
    }
}
