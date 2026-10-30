package io.github.flymeisland.diagnostic;
import android.content.Context;
/** Local per-app compact capsule dimensions, falling back to global dimensions. */
public final class AppSizePrefs {
    private AppSizePrefs() {}
    private static String key(String pkg, String axis) { return "app_pill_" + axis + "_v040_" + pkg; }
    public static int width(Context c, String pkg) {
        return pkg == null || pkg.isEmpty() ? SystemUiIslandPrefs.pillWidth(c)
                : SystemUiIslandPrefs.clamp(MusicPrefs.get(c).getInt(key(pkg,"w"),"island.charge".equals(pkg) ? Math.max(180,SystemUiIslandPrefs.pillWidth(c)) : SystemUiIslandPrefs.pillWidth(c)), SystemUiIslandPrefs.PILL_W_MIN,SystemUiIslandPrefs.PILL_W_MAX);
    }
    public static int height(Context c, String pkg) {
        return pkg == null || pkg.isEmpty() ? SystemUiIslandPrefs.pillHeight(c)
                : SystemUiIslandPrefs.clamp(MusicPrefs.get(c).getInt(key(pkg,"h"),"island.charge".equals(pkg) ? Math.max(36,SystemUiIslandPrefs.pillHeight(c)) : SystemUiIslandPrefs.pillHeight(c)), SystemUiIslandPrefs.PILL_H_MIN,SystemUiIslandPrefs.PILL_H_MAX);
    }
    public static void save(Context c,String pkg,int w,int h) {
        MusicPrefs.get(c).edit().putInt(key(pkg,"w"),SystemUiIslandPrefs.clamp(w,SystemUiIslandPrefs.PILL_W_MIN,SystemUiIslandPrefs.PILL_W_MAX))
                .putInt(key(pkg,"h"),SystemUiIslandPrefs.clamp(h,SystemUiIslandPrefs.PILL_H_MIN,SystemUiIslandPrefs.PILL_H_MAX)).apply();
        SystemUiAicyClient.syncAppSize(c,pkg);
    }
    public static void reset(Context c,String pkg) {
        MusicPrefs.get(c).edit().remove(key(pkg,"w")).remove(key(pkg,"h")).apply();
        SystemUiAicyClient.syncAppSize(c,pkg);
    }
}
