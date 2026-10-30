package io.github.flymeisland.diagnostic;

import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Optional mutual exclusion with the REAL Aicy voice assistant process.
 * Requires the user to explicitly add com.meizu.voiceassistant to LSPosed
 * scope, in addition to com.android.systemui.
 *
 * It observes only window add/remove events inside Aicy's own process.
 * It does not change Aicy views, input handling, behavior or window type.
 */
public final class AicyPresenceBridge {
    static final String AICY_PACKAGE = "com.meizu.voiceassistant";
    private static final long HEARTBEAT_MS = 2000L;
    private static final long LEASE_MS = 6500L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<View> ACTIVE_WINDOWS =
            Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
    private static boolean lastReported;
    private static Context aicyContext;
    private static final Runnable HEARTBEAT = new Runnable() {
        @Override public void run() {
            if (aicyContext == null || ACTIVE_WINDOWS.isEmpty()) return;
            reportState(anyVisibleWindow());
            MAIN.postDelayed(this, HEARTBEAT_MS);
        }
    };
    private static final Runnable LEASE_EXPIRED = () -> {
        SystemUiAicyIsland.setAicyShowing(false);
    };
    private AicyPresenceBridge() {}

    public static void installInAicy(XC_LoadPackage.LoadPackageParam param) {
        if (!AICY_PACKAGE.equals(param.packageName)
                || param.classLoader == null) return;
        try {
            Class<?> manager = Class.forName(
                    "android.view.WindowManagerImpl", false, param.classLoader);
            // Project's slim Xposed API stubs expose findAndHookMethod, not
            // hookAllMethods. These are the concrete ViewManager signatures.
            XposedHelpers.findAndHookMethod(manager, "addView",
                    View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || p.args.length < 2 ||
                            !(p.args[0] instanceof View) ||
                            !(p.args[1] instanceof WindowManager.LayoutParams)) return;
                    View view = (View) p.args[0];
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) p.args[1];
                    XposedBridge.log("[FlymeIsland/AicyPresence] window type=" + lp.type);
                    if (!isVoiceWindow(lp)) return;
                    synchronized (ACTIVE_WINDOWS) {
                        ACTIVE_WINDOWS.add(view);
                    }
                    aicyContext = view.getContext().getApplicationContext();
                    MAIN.removeCallbacks(HEARTBEAT);
                    // Aicy may keep an attached root and toggle its visibility.
                    view.getViewTreeObserver().addOnPreDrawListener(() -> {
                        boolean visible = anyVisibleWindow();
                        if (visible != lastReported) reportState(visible);
                        return true;
                    });
                    reportState(true);
                    MAIN.postDelayed(HEARTBEAT, HEARTBEAT_MS);
                }
            });
            XC_MethodHook remove = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof View)) return;
                    View view = (View) p.args[0];
                    synchronized (ACTIVE_WINDOWS) {
                        if (!ACTIVE_WINDOWS.remove(view)) return;
                        if (!ACTIVE_WINDOWS.isEmpty()) return;
                    }
                    MAIN.removeCallbacks(HEARTBEAT);
                    MAIN.postDelayed(() -> {
                        synchronized (ACTIVE_WINDOWS) {
                            if (!ACTIVE_WINDOWS.isEmpty()) return;
                        }
                        reportState(false);
                    }, 240L); // Aicy sometimes replaces its window in one frame.
                }
            };
            XposedHelpers.findAndHookMethod(manager, "removeView",
                    View.class, remove);
            XposedHelpers.findAndHookMethod(manager, "removeViewImmediate",
                    View.class, remove);
            XposedHelpers.findAndHookMethod(manager, "updateViewLayout",
                    View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    boolean visible = anyVisibleWindow();
                    if (visible != lastReported) reportState(visible);
                }
            });
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class,
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (ACTIVE_WINDOWS) {
                        if (!ACTIVE_WINDOWS.contains(p.thisObject)) return;
                    }
                    boolean visible = anyVisibleWindow();
                    if (visible != lastReported) reportState(visible);
                }
            });
            XposedBridge.log("[FlymeIsland/AicyPresence] nonmutating observer installed");
        } catch (Throwable e) {
            XposedBridge.log("[FlymeIsland/AicyPresence] unavailable: "
                    + e.getClass().getSimpleName());
        }
    }

    private static boolean isVoiceWindow(WindowManager.LayoutParams p) {
        // Types observed/researched for Aicy's independent top-level UI.
        if (p.type == 2041 || p.type == 2033 || p.type == 2017) return true;
        CharSequence title = p.getTitle();
        String lower = title == null ? "" : title.toString().toLowerCase(
                java.util.Locale.ROOT);
        return lower.contains("quickvoice") || lower.contains("aicy");
    }

    private static boolean anyVisibleWindow() {
        synchronized (ACTIVE_WINDOWS) {
            for (View view : ACTIVE_WINDOWS)
                if (view.isAttachedToWindow() && view.isShown()) return true;
        }
        return false;
    }

    private static void reportState(boolean showing) {
        lastReported = showing;
        sendPresence(showing);
    }

    private static void sendPresence(boolean showing) {
        if (aicyContext == null || Build.VERSION.SDK_INT < 34) return;
        try {
            Intent event = new Intent(MiniBridgeContract.ACTION_AICY_PRESENCE);
            event.setPackage(MiniBridgeContract.SYSTEM_UI);
            event.putExtra(MiniBridgeContract.EXTRA_AICY_ACTIVE, showing);
            event.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
            BroadcastOptions opts = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true);
            aicyContext.sendBroadcast(event, null, opts.toBundle());
        } catch (Throwable ignored) {
            // Never affect actual Aicy window behavior.
        }
    }

    /** Receives only Android-authenticated broadcasts from Aicy's own UID. */
    public static void onSystemUiSignal(Context context, BroadcastReceiver receiver,
                                        Intent intent) {
        if (intent == null
                || !MiniBridgeContract.ACTION_AICY_PRESENCE.equals(intent.getAction())
                || !MiniBridgeContract.SYSTEM_UI.equals(context.getPackageName())) return;
        try {
            int expectedUid = context.getPackageManager()
                    .getPackageUid(AICY_PACKAGE, 0);
            int actualUid = receiver.getSentFromUid();
            if (actualUid == Process.INVALID_UID || actualUid != expectedUid) return;
            String actualPackage = receiver.getSentFromPackage();
            if (actualPackage != null && !AICY_PACKAGE.equals(actualPackage)) return;
            long timestamp = intent.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1L);
            long elapsed = SystemClock.elapsedRealtime() - timestamp;
            if (timestamp < 0 || elapsed < 0 || elapsed > 10000L) return;

            boolean present = intent.getBooleanExtra(
                    MiniBridgeContract.EXTRA_AICY_ACTIVE, false);
            if (present != lastReported) {
                XposedBridge.log("[FlymeIsland/AicyPresence] authenticated active=" + present);
                lastReported = present;
            }
            MAIN.removeCallbacks(LEASE_EXPIRED);
            SystemUiAicyIsland.setAicyShowing(present);
            if (present) MAIN.postDelayed(LEASE_EXPIRED, LEASE_MS);
        } catch (Throwable ignored) {
            // If the assistant is missing, keep the original island behavior.
        }
    }
}
