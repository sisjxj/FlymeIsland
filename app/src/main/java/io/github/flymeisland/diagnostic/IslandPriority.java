package io.github.flymeisland.diagnostic;

import java.util.List;
import java.util.Map;

/** Select one candidate without changing the active primary notification. */
public final class IslandPriority {
    private IslandPriority() {}

    /** Iteration order is oldest to newest; unranked ties prefer the newest. */
    public static String select(Map<String, String> entries, String primaryKey,
                                List<String> priority, boolean otherAppsOnly) {
        String primaryPackage = entries.get(primaryKey);
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Map.Entry<String, String> item : entries.entrySet()) {
            if (item.getKey().equals(primaryKey)) continue;
            String pkg = item.getValue();
            if (pkg == null || (otherAppsOnly && pkg.equals(primaryPackage))) continue;
            int rank = priority.indexOf(pkg);
            if (rank < 0) rank = Integer.MAX_VALUE;
            if (best == null || rank <= bestRank) {
                best = item.getKey();
                bestRank = rank;
            }
        }
        return best;
    }
}
