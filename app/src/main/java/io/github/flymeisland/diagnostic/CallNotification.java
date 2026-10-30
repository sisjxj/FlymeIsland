package io.github.flymeisland.diagnostic;

import android.app.Notification;

/** Match only the system call component and its current incoming/ongoing notification. */
final class CallNotification {
    static final String PACKAGE = "com.android.incallui";
    private CallNotification() {}
    static boolean matches(String pkg, Notification n) {
        if (!PACKAGE.equals(pkg) || n == null) return false;
        if (Notification.CATEGORY_CALL.equals(n.category)) return true;
        if (n.actions == null) return false;
        for (Notification.Action a : n.actions) {
            if (a == null || a.title == null || a.actionIntent == null) continue;
            if (CallActionPolicy.classify(a.title.toString()) != CallActionPolicy.Kind.HANGUP) continue;
            try { if (PACKAGE.equals(a.actionIntent.getCreatorPackage())) return true; }
            catch (RuntimeException ignored) {}
        }
        return false;
    }
}
