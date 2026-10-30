package io.github.flymeisland.diagnostic;
import android.app.NotificationManager;
/** Source identities and DND check shared by the SystemUI island. */
public final class MessageCapsulePublisher {
    public static final String WECHAT = "com.tencent.mm";
    public static final String QQ = "com.tencent.mobileqq";
    private MessageCapsulePublisher() {}
    public static boolean inDoNotDisturb(NotificationManager manager) {
        try {
            int filter = manager.getCurrentInterruptionFilter();
            return filter != NotificationManager.INTERRUPTION_FILTER_ALL
                    && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        } catch (RuntimeException ignored) { return false; }
    }
}
