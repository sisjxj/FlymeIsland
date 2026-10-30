package io.github.flymeisland.diagnostic;

/** Pure routing policy for arrival versus silent media updates. */
public final class MediaRouting {
    public enum Route { NORMAL, UPDATE_MEDIA, PROMOTE_MEDIA, INTERRUPT_WITH_NOTICE, QUEUE }
    private MediaRouting() {}
    public static Route choose(boolean attached, boolean demo, boolean media, boolean existingMedia,
                               boolean visibleActive, boolean sameKey, boolean visibleIsMedia,
                               boolean restore) {
        if (attached && media && existingMedia) return Route.UPDATE_MEDIA;
        if (!attached || demo || !visibleActive || sameKey) return Route.NORMAL;
        if (media) return Route.PROMOTE_MEDIA;
        if (visibleIsMedia && !restore) return Route.INTERRUPT_WITH_NOTICE;
        return Route.QUEUE;
    }
}
