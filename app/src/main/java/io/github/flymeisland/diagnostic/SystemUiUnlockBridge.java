package io.github.flymeisland.diagnostic;

import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

/** Forward system screen events to the module after system delivery into SystemUI. */
final class SystemUiUnlockBridge {
    private static BroadcastReceiver receiver;
    static void register(Context app) {
        if (receiver != null) return;
        BroadcastReceiver next = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent source) {
                if (source == null) return;
                String state = source.getAction();
                if (!Intent.ACTION_SCREEN_OFF.equals(state) && !Intent.ACTION_SCREEN_ON.equals(state)
                        && !Intent.ACTION_USER_PRESENT.equals(state)) return;
                try {
                    Intent relay = new Intent(MiniBridgeContract.ACTION_SCREEN_RELAY).setPackage(MiniBridgeContract.APP);
                    relay.putExtra(MiniBridgeContract.EXTRA_SCREEN_STATE, state);
                    relay.putExtra(MiniBridgeContract.EXTRA_SENT_AT, SystemClock.elapsedRealtime());
                    context.sendBroadcast(relay, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
                    Log.i("FlymeIslandUnlock", "systemui screen event relayed");
                } catch (RuntimeException error) {
                    Log.w("FlymeIslandUnlock", "screen relay failed " + error.getClass().getSimpleName());
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            app.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED);
            receiver = next;
        } catch (RuntimeException error) {
            Log.w("FlymeIslandUnlock", "screen bridge registration failed " + error.getClass().getSimpleName());
        }
    }
}
