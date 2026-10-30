package io.github.flymeisland.diagnostic;

/** Expected call presentation stages; actual Android view integration is pending. */
final class CallStagePolicy {
    enum Stage { COMPACT, CONTROLS, INCOMING }
    enum Event { RINGING, CONNECTED, SWIPE_UP, TAP }
    static Stage next(Stage stage, Event event) {
        switch (event) {
            case RINGING: return Stage.INCOMING;
            case CONNECTED: return Stage.CONTROLS;
            case SWIPE_UP: return Stage.COMPACT;
            case TAP: return stage == Stage.COMPACT ? Stage.CONTROLS : stage;
            default: return stage;
        }
    }
}
