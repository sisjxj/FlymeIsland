package io.github.flymeisland.diagnostic;

/** Pure gating policy for a bounded, once-per-unlock visual replay. */
final class UnlockReplayPolicy {
    static boolean shouldReplay(long cycle, long previous, long now, boolean present,
            boolean locked, boolean expanded, boolean blocked, boolean animating, boolean charging) {
        return cycle >= 0 && cycle <= now && now - cycle <= 10000L && cycle > previous
                && present && !locked && !expanded && !blocked && !animating && !charging;
    }
}
