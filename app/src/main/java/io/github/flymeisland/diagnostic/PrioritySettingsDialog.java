package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** On-device app ordering; application list and priorities are never uploaded. */
public final class PrioritySettingsDialog {
    private PrioritySettingsDialog() {}
    private static String label(PackageManager pm, String pkg) {
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return pkg; }
    }
    public static void show(Activity activity) {
        ArrayList<String> priority = PriorityPrefs.get(activity);
        PackageManager pm = activity.getPackageManager();
        String[] entries = new String[priority.size() + 1];
        entries[0] = "＋ 添加应用";
        for (int i = 0; i < priority.size(); i++)
            entries[i + 1] = (i + 1) + ". " + label(pm, priority.get(i));
        new AlertDialog.Builder(activity).setTitle("应用优先级（从高到低）")
                .setItems(entries, (dialog, index) -> {
                    if (index == 0) { chooseApp(activity, priority); return; }
                    int at = index - 1;
                    String name = label(pm, priority.get(at));
                    new AlertDialog.Builder(activity).setTitle(name)
                            .setItems(new String[]{"上移", "下移", "从优先列表移除"}, (d, op) -> {
                                if (op == 0 && at > 0) java.util.Collections.swap(priority, at, at - 1);
                                if (op == 1 && at + 1 < priority.size()) java.util.Collections.swap(priority, at, at + 1);
                                if (op == 2) priority.remove(at);
                                PriorityPrefs.save(activity, priority);
                                SystemUiAicyClient.refreshPriority(activity);
                                show(activity);
                            }).setNegativeButton("返回", (d, x) -> show(activity)).show();
                }).setNegativeButton("完成", null).show();
    }
    private static void chooseApp(Activity activity, ArrayList<String> priority) {
        PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> apps = new ArrayList<>(pm.getInstalledApplications(0));
        apps.removeIf(a -> priority.contains(a.packageName)
                || MiniBridgeContract.APP.equals(a.packageName));
        apps.sort(Comparator.comparing(a -> pm.getApplicationLabel(a).toString(),
                String.CASE_INSENSITIVE_ORDER));
        String[] titles = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++)
            titles[i] = pm.getApplicationLabel(apps.get(i)) + "  (" + apps.get(i).packageName + ")";
        new AlertDialog.Builder(activity).setTitle("添加到优先列表")
                .setItems(titles, (d, index) -> {
                    if (priority.size() < 64) priority.add(apps.get(index).packageName);
                    PriorityPrefs.save(activity, priority);
                    SystemUiAicyClient.refreshPriority(activity);
                    show(activity);
                }).setNegativeButton("返回", (d, x) -> show(activity)).show();
    }
}
