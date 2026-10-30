package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AppSelectionDialog {
    private AppSelectionDialog() {}
    private static final class App {
        final String pkg, title;
        App(String pkg, String title) { this.pkg = pkg; this.title = title; }
    }
    public static void show(Activity activity) { show(activity, false); }
    public static void showGestureApps(Activity activity) { show(activity, true); }
    private static void show(Activity activity, boolean gestureMode) {
        PackageManager pm = activity.getPackageManager();
        List<App> apps = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (MiniBridgeContract.APP.equals(info.packageName)) continue;
            try { apps.add(new App(info.packageName, pm.getApplicationLabel(info).toString())); }
            catch (RuntimeException ignored) {}
        }
        apps.sort(Comparator.comparing(a -> a.title, String.CASE_INSENSITIVE_ORDER));
        Set<String> selected = gestureMode ? GestureAppPrefs.get(activity) : AppSelectionPrefs.get(activity);
        ArrayList<App> visible = new ArrayList<>(apps);
        int density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16 * density, 0, 16 * density, 0);
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("搜索应用名称或包名");
        content.addView(search);
        TextView count = new TextView(activity);
        count.setText("已选择 " + selected.size() + " 个应用");
        content.addView(count);
        LinearLayout actions = new LinearLayout(activity);
        Button all = new Button(activity); all.setText("全选");
        Button none = new Button(activity); none.setText("全不选");
        actions.addView(all, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(none, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(actions);
        ListView list = new ListView(activity);
        ArrayAdapter<App> adapter = new ArrayAdapter<App>(activity, android.R.layout.simple_list_item_1, visible) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                App app = getItem(position);
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                ImageView icon = new ImageView(activity);
                try { icon.setImageDrawable(pm.getApplicationIcon(app.pkg)); } catch (PackageManager.NameNotFoundException ignored) {}
                row.addView(icon, new LinearLayout.LayoutParams(32 * density, 32 * density));
                LinearLayout labels = new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL);
                labels.setPadding(10 * density, 8 * density, 0, 8 * density);
                TextView title = new TextView(activity); title.setText(app.title); title.setTextSize(16);
                TextView pkg = new TextView(activity); pkg.setText(app.pkg); pkg.setTextSize(11);
                labels.addView(title); labels.addView(pkg);
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
                CheckBox check = new CheckBox(activity); check.setChecked(selected.contains(app.pkg));
                row.addView(check);
                check.setOnCheckedChangeListener((button, enabled) -> {
                    if (enabled) selected.add(app.pkg); else selected.remove(app.pkg);
                    count.setText("已选择 " + selected.size() + " 个应用");
                });
                row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
                return row;
            }
        };
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(-1, 360 * density));
        all.setOnClickListener(v -> {
            for (App app : apps) selected.add(app.pkg);
            count.setText("已选择 " + selected.size() + " 个应用");
            adapter.notifyDataSetChanged();
        });
        none.setOnClickListener(v -> {
            selected.clear();
            count.setText("已选择 0 个应用");
            adapter.notifyDataSetChanged();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                String query = text.toString().trim().toLowerCase(Locale.ROOT);
                visible.clear();
                for (App app : apps)
                    if (app.title.toLowerCase(Locale.ROOT).contains(query)
                            || app.pkg.toLowerCase(Locale.ROOT).contains(query)) visible.add(app);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        new AlertDialog.Builder(activity).setTitle(gestureMode ? "选择切歌手势应用" : "选择接入灵动岛的应用")
                .setView(content).setPositiveButton("保存", (d, x) -> {
                    if (gestureMode) GestureAppPrefs.save(activity, selected);
                    else AppSelectionPrefs.save(activity, selected);
                    if (activity instanceof MainActivity) ((MainActivity) activity).updateStatus();
                }).setNegativeButton("取消", null).show();
    }
}
