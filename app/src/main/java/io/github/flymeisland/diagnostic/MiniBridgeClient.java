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

/** Sends a UID probe, never a chat PendingIntent or privileged window command. */
public final class MiniBridgeClient {
    public interface Callback {
        void onResult(String text);
    }

    private MiniBridgeClient() {}

    public static void request(Activity activity, Callback callback) {
        if (Build.VERSION.SDK_INT < 34) {
            callback.onResult("该通信诊断需要 Android 14 以上系统");
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean done = new AtomicBoolean(false);
        ResultReceiver reply = new ResultReceiver(main) {
            @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (!done.compareAndSet(false, true)) return;
                String report = resultData == null ? null :
                        resultData.getString(MiniBridgeContract.EXTRA_REPORT);
                callback.onResult(report == null ? "SystemUI 返回了空结果" : report);
            }
        };
        Intent intent = new Intent(MiniBridgeContract.ACTION_PROBE);
        intent.setPackage(MiniBridgeContract.SYSTEM_UI);
        intent.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
        intent.putExtra(MiniBridgeContract.EXTRA_REPLY, reply);
        try {
            // Android 14+: share the OS-authenticated sender UID explicitly.
            // Receiver-side UID authentication remains fail-closed.
            BroadcastOptions opts = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true);
            activity.sendBroadcast(intent, null, opts.toBundle());
        } catch (RuntimeException failure) {
            done.set(true);
            callback.onResult("向 SystemUI 发送只读请求失败："
                    + failure.getClass().getSimpleName());
            return;
        }
        main.postDelayed(() -> {
            if (done.compareAndSet(false, true)) {
                callback.onResult("6 秒内未收到 SystemUI 应答。已启用 Android 发送者身份共享，"
                        + "请到 LSPosed 日志搜索 [FlymeIsland/Bridge v0.17.1]。"
                        + "若没有对应日志，可能是 SystemUI Hook 未注册或广播未送达。"
                        + "本次没有执行小窗操作。");
            }
        }, 6000);
    }
}
