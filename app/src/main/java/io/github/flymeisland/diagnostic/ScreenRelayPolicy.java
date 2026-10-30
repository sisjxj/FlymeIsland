package io.github.flymeisland.diagnostic;

/** Validate relay origin and bounded age without exposing notification content. */
final class ScreenRelayPolicy {
    private ScreenRelayPolicy() {}
    static boolean accept(int senderUid, int expectedUid, int invalidUid, String sender,
            String systemUi, long sent, long age, String state) {
        return senderUid != invalidUid && senderUid == expectedUid
                && (sender == null || systemUi.equals(sender)) && sent >= 0 && age >= 0 && age <= 5000
                && ("android.intent.action.SCREEN_OFF".equals(state)
                || "android.intent.action.SCREEN_ON".equals(state)
                || "android.intent.action.USER_PRESENT".equals(state));
    }
}
