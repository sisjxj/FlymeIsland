package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local explicit user click -> narrowly-scoped SystemUI position probe.
 * No message content, pending intents or privileged launch commands.
 */
public final class CameraAnchorProbeClient {
    private CameraAnchorProbeClient() {}

    public static void request(Activity activity, MiniBridgeClient.Callback callback) {
        if (Build.VERSION.SDK_INT < 34) {
            callback.onResult("需要 Android 14 以上系统。");
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean returned = new AtomicBoolean(false);
        ResultReceiver reply = new ResultReceiver(handler) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                if (!returned.compareAndSet(false, true)) return;
                String message = data == null ? null
                        : data.getString(MiniBridgeContract.EXTRA_REPORT);
                callback.onResult(message == null ? "SystemUI 回复为空" : message);
            }
        };
        Intent intent = new Intent(MiniBridgeContract.ACTION_CAMERA_ANCHOR);
        intent.setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_REPLY, reply);
        try {
            BroadcastOptions options = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true);
            activity.sendBroadcast(intent, null, options.toBundle());
        } catch (RuntimeException failure) {
            returned.set(true);
            callback.onResult("发送测试请求失败：" + failure.getClass().getSimpleName());
            return;
        }
        handler.postDelayed(() -> {
            if (returned.compareAndSet(false, true)) {
                callback.onResult("8 秒内未收到 SystemUI 回应。"
                        + "请确认 LSPosed 仍仅勾选 SystemUI，安装新版后已重启系统；"
                        + "也可以检查日志 [FlymeIsland/CameraAnchor v0.22]。");
            }
        }, 8000L);
    }
}
