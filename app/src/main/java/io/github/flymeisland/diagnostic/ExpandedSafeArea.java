package io.github.flymeisland.diagnostic;

/** Bounds camera clearance to available card space, retaining controls and a text line. */
final class ExpandedSafeArea {
    private ExpandedSafeArea() {}
    static int appliedInset(int requested, int cardLimit, int minimumContent) {
        return Math.max(0, Math.min(Math.max(0, requested), Math.max(0, cardLimit - minimumContent)));
    }
}
