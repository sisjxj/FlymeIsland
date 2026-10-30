package io.github.flymeisland.diagnostic;

import android.app.NotificationManager;
import android.content.Context;

/** Remove only the old notifications owned by this module after upgrade. */
public final class LegacyCleanup {
    private LegacyCleanup() {}
    public static void removeOldOwnCapsules(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.cancel(760502); // old Spotify mirror
        manager.cancel(760703); // old charging mirror
        manager.cancel(760804); // old WeChat/QQ mirror
    }
}
