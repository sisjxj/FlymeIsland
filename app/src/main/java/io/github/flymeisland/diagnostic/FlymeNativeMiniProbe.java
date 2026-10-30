package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** The v0.13 native Flyme path; extra diagnostics only, no bypass or fallback. */
public final class FlymeNativeMiniProbe {
    private static final String TAG = "FlymeIslandNativeMini";
    private static final String TARGET_PACKAGE = "com.tencent.mm";
    private static final String EXT_CLASS = "flyme.view.WindowManagerExt";

    private FlymeNativeMiniProbe() {}

    public static String inspectApi() {
        try {
            Class<?> clazz = Class.forName(EXT_CLASS);
            clazz.getMethod("getInstance", Context.class);
            clazz.getMethod("setStartWindowMode", String.class);
            return "Flyme WindowManagerExt 接口和方法存在；权限和实际窗口效果仍需测试。";
        } catch (ClassNotFoundException e) {
            return "系统未提供 Flyme WindowManagerExt 类";
        } catch (NoSuchMethodException e) {
            return "系统存在 WindowManagerExt，但方法签名不匹配";
        } catch (RuntimeException | LinkageError e) {
            return "接口检查失败：" + e.getClass().getSimpleName();
        }
    }

    public static String launch(Activity activity, PendingIntent original) {
        if (original == null)
            return "阶段=检查通知动作；结果=不存在或已超过90秒（不会启动微信）";
        KeyguardManager guard = activity.getSystemService(KeyguardManager.class);
        if (guard == null || guard.isKeyguardLocked())
            return "阶段=检查锁屏；结果=屏幕仍锁定（不会启动微信）";
        String stage = "加载 WindowManagerExt";
        try {
            Class<?> clazz = Class.forName(EXT_CLASS);
            stage = "查找 getInstance";
            Method factory = clazz.getMethod("getInstance", Context.class);
            stage = "执行 getInstance";
            Object manager = factory.invoke(null, activity);
            if (manager == null) return "阶段=执行 getInstance；结果=系统返回空实例";

            stage = "查找 setStartWindowMode";
            Method setStart = clazz.getMethod("setStartWindowMode", String.class);
            stage = "执行 setStartWindowMode";
            setStart.invoke(manager, TARGET_PACKAGE);

            Bundle options = new Bundle();
            options.putBoolean("start_windowmode", true);
            stage = "执行 startIntentSender";
            activity.startIntentSender(original.getIntentSender(),
                    null, 0, 0, 0, options);

            Log.i(TAG, "v0.15 all native call stages accepted; window visibility unverified");
            return "阶段=完成 startIntentSender；结果=未抛异常，启动请求已发送。"
                    + " 但 Flyme 是否实际显示小窗仍须观察屏幕。";
        } catch (InvocationTargetException e) {
            Throwable root = unwrap(e);
            return fail(stage, root);
        } catch (ClassNotFoundException | NoSuchMethodException
                 | IllegalAccessException | IntentSender.SendIntentException e) {
            return fail(stage, e);
        } catch (RuntimeException | LinkageError e) {
            return fail(stage, e);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable root = error;
        for (int i = 0; i < 3; i++) {
            if (root instanceof InvocationTargetException) {
                Throwable next = ((InvocationTargetException) root).getCause();
                if (next != null && next != root) {
                    root = next;
                    continue;
                }
            }
            break;
        }
        return root;
    }

    private static String fail(String stage, Throwable cause) {
        String type = cause.getClass().getSimpleName();
        // Flyme-supplied *exception* detail only, never notification/chat data.
        String detail = cause.getMessage();
        if (detail == null) detail = "";
        detail = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (detail.length() > 160) detail = detail.substring(0, 160) + "…";
        Log.w(TAG, "v0.15 stage=" + stage + " failure=" + type);
        return "阶段=" + stage + "；错误=" + type
                + (detail.isEmpty() ? "" : "；系统说明=" + detail)
                + "；没有执行后续阶段。";
    }
}
