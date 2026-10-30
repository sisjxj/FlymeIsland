package io.github.flymeisland.diagnostic;
/** Pure validation of an authenticated, fresh, opt-in SystemUI gesture. */
public final class MediaGestureAuthorization {
    private MediaGestureAuthorization() {}
    public static boolean allowed(int senderUid, int expectedUid, int invalidUid, String senderPackage,
                                  String systemUiPackage, long now, long sent, String sourcePackage,
                                  String direction, boolean selected) {
        long age = now - sent;
        return senderUid != invalidUid && senderUid == expectedUid
                && (senderPackage == null || systemUiPackage.equals(senderPackage))
                && sent >= 0 && age >= 0 && age <= 5000L
                && sourcePackage != null && !sourcePackage.isEmpty() && sourcePackage.length() <= 200
                && ("next".equals(direction) || "previous".equals(direction)) && selected;
    }
}
