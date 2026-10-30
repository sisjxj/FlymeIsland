package io.github.flymeisland.diagnostic;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import de.robv.android.xposed.XposedBridge;

/**
 * One-shot, opt-in POSITION-ONLY probe running inside SystemUI.
 * Does not display a chat message, handle clicks, launch tasks or modify
 * native Flyme live-notification views.
 *
 * Safety: non-touchable, no focus, 5s TTL, clean up at
 * screen-off, fail-closed UID validation, no persistent changes.
 */
public final class SystemUiCameraAnchorProbe {
    private static final String TAG = "[FlymeIsland/CameraAnchor v0.22] ";
    private static final int SYSTEM_TYPE_STATUS_BAR_SUB_PANEL = 2017; // platform @hide
    private static final long VISIBLE_MS = 5000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager manager;
    private static FrameLayout view;
    private static Context application;
    private static BroadcastReceiver screenReceiver;
    private static final Runnable REMOVE = SystemUiCameraAnchorProbe::remove;

    private SystemUiCameraAnchorProbe() {}

    public static void onRequest(Context context, BroadcastReceiver receiver, Intent intent) {
        if (intent == null
                || !MiniBridgeContract.ACTION_CAMERA_ANCHOR.equals(intent.getAction()))
            return;
        if (!MiniBridgeContract.SYSTEM_UI.equals(context.getPackageName())) return;
        try {
            int senderUid = receiver.getSentFromUid();
            int expectedUid = context.getPackageManager()
                    .getPackageUid(MiniBridgeContract.APP, 0);
            if (senderUid == Process.INVALID_UID || senderUid != expectedUid) {
                log("UID rejected");
                return;
            }
            String senderPkg = receiver.getSentFromPackage();
            if (senderPkg != null && !MiniBridgeContract.APP.equals(senderPkg)) {
                log("package rejected");
                return;
            }
            long timestamp = intent.getLongExtra(MiniBridgeContract.EXTRA_SENT_AT, -1);
            long age = SystemClock.elapsedRealtime() - timestamp;
            if (timestamp < 0 || age < 0 || age > 10000L) {
                log("stale request rejected");
                return;
            }
            ResultReceiver reply = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY, ResultReceiver.class)
                    : intent.getParcelableExtra(MiniBridgeContract.EXTRA_REPLY);
            if (reply == null) return;

            String result = execute(context.getApplicationContext());
            Bundle bundle = new Bundle();
            bundle.putString(MiniBridgeContract.EXTRA_REPORT, result);
            try { reply.send(0, bundle); }
            catch (RuntimeException ignored) {}
        } catch (Throwable error) {
            log("receiver error " + error.getClass().getSimpleName());
        }
    }

    private static String execute(Context c) {
        if (Build.VERSION.SDK_INT < 34) return "仅 Android 14+ 可测试";
        if (!MiniBridgeContract.SYSTEM_UI.equals(c.getPackageName()))
            return "拒绝：非 SystemUI 进程";
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        if (km != null && (km.isDeviceLocked() || km.isKeyguardLocked()))
            return "拒绝：已锁屏";
        if (c.getPackageManager().checkPermission(
                "android.permission.INTERNAL_SYSTEM_WINDOW",
                MiniBridgeContract.SYSTEM_UI) != PackageManager.PERMISSION_GRANTED) {
            return "SystemUI 当前未获 INTERNAL_SYSTEM_WINDOW 授权，未尝试特权窗口。";
        }

        remove();
        application = c;
        manager = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return "WindowManager 不可用";

        int height = dp(c, 38);
        int width = dp(c, 164);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                width, height, SYSTEM_TYPE_STATUS_BAR_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = 0;
        p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        p.setTitle("FlymeIsland Camera Anchor One-Shot Diagnostic");

        FrameLayout root = new FrameLayout(c);
        GradientDrawable black = new GradientDrawable();
        black.setColor(Color.BLACK);
        black.setCornerRadius(dp(c, 22));
        root.setBackground(black);
        TextView text = new TextView(c);
        text.setText("●  顶部位置测试");
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        root.addView(text, new FrameLayout.LayoutParams(-1, -1));
        try {
            manager.addView(root, p);
            view = root;
            screenReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    remove();
                }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            c.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            MAIN.postDelayed(REMOVE, VISIBLE_MS);
            log("added 2017 position probe y=0 for 5 seconds");
            return "已添加 SystemUI 临时测试窗口（类型 2017，y=0，5 秒自动消失）。"
                    + "\n请观察黑色小胶囊是否出现在前摄高度。"
                    + "\n这仅表示窗口创建成功，不代表实际像素位置已验证。";
        } catch (Throwable fail) {
            String name = fail.getClass().getSimpleName();
            remove();
            log("addView failed: " + name);
            return "SystemUI 窗口创建被拒绝：" + name
                    + "。未修改系统状态栏；可回退原浮窗。";
        }
    }

    private static void remove() {
        MAIN.removeCallbacks(REMOVE);
        if (screenReceiver != null && application != null) {
            try { application.unregisterReceiver(screenReceiver); }
            catch (Throwable ignored) {}
        }
        screenReceiver = null;
        if (view != null && manager != null) {
            try { manager.removeViewImmediate(view); }
            catch (Throwable ignored) {}
        }
        view = null;
        manager = null;
        application = null;
    }

    private static int dp(Context c, int value) {
        return (int) (c.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private static void log(String message) {
        try { XposedBridge.log(TAG + message); }
        catch (Throwable ignored) {}
    }
}
