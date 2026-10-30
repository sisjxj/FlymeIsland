package io.github.flymeisland.diagnostic;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATION = 7501;
    private static final int REQUEST_REAL_WAVE = 7502;
    private static final int REQUEST_APP_AUDIO = 7503;
    private static final int REQUEST_PROJECTION = 7504;
    private Switch waveSwitch;
    private TextView state;
    private TextView info;
    private Switch musicSwitch;
    private Switch quietSwitch;
    private Switch chargeSwitch;
    private Switch wechatSwitch;
    private Switch qqSwitch;
    private Switch dndSwitch;
    private Switch previewSwitch;
    private Switch miniExperimentSwitch;
    private Switch persistentMirrorSwitch;
    private Switch hotspotSwitch;
    private Switch floatingSwitch;
    private Switch systemIslandSwitch;
    private Spinner gestureSpinner;
    private TextView miniReport;
    private TextView permissionReport;
    private TextView bridgeReport;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(0xff111827);
        getWindow().setNavigationBarColor(0xff111827);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(0xff111827);
        android.widget.FrameLayout pageHost = new android.widget.FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout homePage = createPage(pageHost, "灵动岛", "让重要的提醒留在视线里");
        LinearLayout mediaPage = createPage(pageHost, "音乐与充电", "播放、波形与充电显示");
        LinearLayout appearancePage = createPage(pageHost, "外观与手势", "调整尺寸、位置与交互方式");
        LinearLayout aboutPage = createPage(pageHost, "关于", "权限、测试与版本更新");
        LinearLayout core = settingsSection(homePage, "通知与应用", "开启灵动岛、选择应用、调整优先级", true);
        systemIslandSwitch = toggle(core, "启用灵动岛", SystemUiAicyClient.enabled(this), enabled -> {
            SystemUiAicyClient.setEnabled(this, enabled);
            updateStatus();
        });
        toggle(core, "显示来电胶囊", MusicPrefs.callEnabled(this), enabled -> MusicPrefs.setCallEnabled(this, enabled));
        label(core, "接听与拒接使用系统来电操作；响铃静音请按音量键。", 13, 0xff9ca3af);
        button(core, "调整电话三级尺寸", () -> CallSizeDialog.show(this));
        button(core, "选择接入应用", () -> AppSelectionDialog.show(this));
        label(core, "默认不选择任何应用；可逐个选择，也可以全选。", 13, 0xff9ca3af);
        button(core, "调整应用优先级", () -> PrioritySettingsDialog.show(this));
        label(core, "主通知移除后，右侧优先级最高的应用接替。默认微信、QQ 优先。", 13, 0xff9ca3af);
        previewSwitch = toggle(core, "显示通知内容预览", MusicPrefs.messagePreview(this), enabled -> {
            MusicPrefs.setMessagePreview(this, enabled);
            SystemUiAicyClient.stop(this, null);
        });
        dndSwitch = toggle(core, "遵守系统免打扰", MusicPrefs.respectDnd(this),
                enabled -> MusicPrefs.setRespectDnd(this, enabled));
        label(core, "预览默认关闭；开启后仅在解锁时显示通知提供的标题和正文。", 13, 0xff9ca3af);

        LinearLayout media = settingsSection(mediaPage, "音乐与充电", "媒体播放和充电状态接入同一个灵动岛", true);
        musicSwitch = toggle(media, "显示媒体播放状态", MusicPrefs.enabled(this),
                enabled -> MusicPrefs.setEnabled(this, enabled));
        toggle(media, "媒体控件使用图标", MusicPrefs.mediaIconMode(this), enabled -> MusicPrefs.setMediaIconMode(this, enabled));
        label(media, "关闭后显示文字；额外控件无可用图标时显示名称。", 13, 0xff9ca3af);
        waveSwitch = toggle(media, "混合播放输出波形（可选）", MusicPrefs.realWave(this), enabled -> {
            if (enabled) stopService(new Intent(this, AppAudioWaveService.class));
            if (enabled && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_REAL_WAVE);
            } else MusicPrefs.setRealWave(this, enabled);
        });
        label(media, "混合模式可能包含其他正在播放的应用。", 13, 0xff9ca3af);
        button(media, "开启仅当前播放器的音频波形", () -> {
            if (AppAudioWaveService.isRunning()) { info.setText("当前播放器音频波形已开启。"); return; }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_APP_AUDIO);
            else requestPlayerProjection();
        });
        button(media, "停止当前播放器音频波形", () -> {
            stopService(new Intent(this, AppAudioWaveService.class));
            info.setText("已停止应用音频波形，播放状态动画仍可用。");
        });
        label(media, "仅播放器模式需要音频权限及系统屏幕共享授权，会显示前台通知；只读取该播放器允许捕获的播放音频，不读取屏幕或麦克风，不保存音频。两种真实波形模式互斥。", 13, 0xff9ca3af);
        button(media, "单独调整媒体胶囊尺寸", () -> AppSizeDialog.showMedia(this));
        toggle(media, "媒体胶囊滑动切歌", GestureAppPrefs.mediaEnabled(this), enabled -> GestureAppPrefs.setMediaEnabled(this, enabled));
        label(media, "媒体胶囊与播放器的其他通知分开设置；左滑下一曲，右滑上一曲，长按移除。", 13, 0xff9ca3af);
        button(media, "调整波形和封面", () -> WaveStyleDialog.show(this));
        button(media, "选择切歌手势应用", () -> AppSelectionDialog.showGestureApps(this));
        label(media, "所选应用：左滑下一曲、右滑上一曲，长按移除胶囊；需要该应用提供可控制的媒体会话。", 13, 0xff9ca3af);
        button(media, "单独调整充电胶囊尺寸", () -> AppSizeDialog.showCharge(this));
        chargeSwitch = toggle(media, "显示充电状态", MusicPrefs.chargingEnabled(this),
                enabled -> MusicPrefs.setChargingEnabled(this, enabled));
        label(media, "媒体播放显示波形；接入电源后短暂显示电量。", 13, 0xff9ca3af);

        LinearLayout appearance = settingsSection(appearancePage, "外观与手势", "胶囊尺寸、位置和滑动方向", true);
        String[] swipeModes = {"禁用左右滑删除", "仅左滑删除", "仅右滑删除", "左右都能删除"};
        gestureSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, swipeModes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gestureSpinner.setAdapter(adapter);
        gestureSpinner.setSelection(SystemUiIslandPrefs.swipeMode(this), false);
        appearance.addView(gestureSpinner, new LinearLayout.LayoutParams(-1, dp(50)));
        gestureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SystemUiIslandPrefs.setSwipeMode(MainActivity.this, position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        addDimension(appearance, "胶囊宽度", SystemUiIslandPrefs.PILL_W_MIN, SystemUiIslandPrefs.PILL_W_MAX,
                SystemUiIslandPrefs.pillWidth(this), x -> SystemUiIslandPrefs.setPillWidth(this, x));
        addDimension(appearance, "胶囊高度", SystemUiIslandPrefs.PILL_H_MIN, SystemUiIslandPrefs.PILL_H_MAX,
                SystemUiIslandPrefs.pillHeight(this), x -> SystemUiIslandPrefs.setPillHeight(this, x));
        addDimension(appearance, "距离顶部", SystemUiIslandPrefs.TOP_MIN, SystemUiIslandPrefs.TOP_MAX,
                SystemUiIslandPrefs.top(this), x -> SystemUiIslandPrefs.setTop(this, x));
        addDimension(appearance, "卡片宽度", SystemUiIslandPrefs.PANEL_W_MIN, SystemUiIslandPrefs.PANEL_W_MAX,
                SystemUiIslandPrefs.panelWidth(this), x -> SystemUiIslandPrefs.setPanelWidth(this, x));
        addDimension(appearance, "卡片高度上限", SystemUiIslandPrefs.PANEL_H_MIN, SystemUiIslandPrefs.PANEL_H_MAX,
                SystemUiIslandPrefs.panelHeight(this), x -> SystemUiIslandPrefs.setPanelHeight(this, x));
        addDimension(appearance, "展开卡片顶部避让", SystemUiIslandPrefs.PANEL_SAFE_TOP_MIN, SystemUiIslandPrefs.PANEL_SAFE_TOP_MAX,
                SystemUiIslandPrefs.panelSafeTop(this), x -> SystemUiIslandPrefs.setPanelSafeTop(this, x));
        label(appearance, "将展开卡片内容下移，避开前置摄像头；空间不足时可增大卡片高度上限。", 13, 0xff9ca3af);
        button(appearance, "单独设置应用胶囊尺寸", () -> AppSizeDialog.show(this));
        label(appearance, "未单独设置的应用使用全局胶囊尺寸。", 13, 0xff9ca3af);
        button(appearance, "恢复推荐尺寸", () -> { SystemUiIslandPrefs.reset(this); recreate(); });

        LinearLayout test = settingsSection(aboutPage, "模拟测试", "模拟三个应用，不发送真实聊天", false);
        label(test, "先启用灵动岛；依次发送模拟 A、B、C，再移除主项检查接替。", 13, 0xff9ca3af);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            String name = String.valueOf((char) ('A' + i));
            button(test, "发送模拟应用 " + name, () -> {
                if (!SystemUiAicyClient.enabled(this)) { info.setText("请先启用灵动岛。"); return; }
                SystemUiAicyClient.test(this, index, false);
                info.setText("已请求显示模拟应用 " + name + "。");
            });
            button(test, "移除模拟应用 " + name, () -> {
                SystemUiAicyClient.test(this, index, true);
                info.setText("已请求移除模拟应用 " + name + "。");
            });
        }

        LinearLayout permission = settingsSection(aboutPage, "权限与状态", "通知读取与模块状态", false);
        state = label(permission, "", 14, 0xfffbbf24);
        button(permission, "打开通知读取权限", () -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (RuntimeException e) { info.setText("无法打开通知读取权限设置。"); }
        });
        label(permission, "LSPosed 需启用系统界面作用域；Aicy 互斥仍需要设备验证。", 13, 0xff9ca3af);
        LinearLayout updates = settingsSection(aboutPage, "版本与开源", "查看更新和项目源码", true);
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { version = "未知"; }
        label(updates, "Flyme Island · 开源实验版\n当前版本：" + version + "\n项目：sisjxj/FlymeIsland\n许可证：GPL-3.0-only", 14, 0xffcbd5e1);
        TextView updateStatus = label(updates, "点击检查 GitHub 上的最新正式版本。", 13, 0xff9ca3af);
        button(updates, "检查更新", () -> UpdateChecker.check(this, updateStatus));
        button(updates, "打开发布页面", () -> UpdateChecker.open(this, UpdateChecker.RELEASES));
        button(updates, "查看开源项目", () -> UpdateChecker.open(this, UpdateChecker.REPOSITORY));
        label(updates, "来电三级界面及设备兼容性仍在测试。检查更新仅访问 GitHub；不会自动下载或安装。", 13, 0xff9ca3af);
        info = label(shell, "", 12, 0xffcbd5e1);
        info.setPadding(dp(18), 0, dp(18), 0);
        addNavigation(shell, new LinearLayout[]{homePage, mediaPage, appearancePage, aboutPage});
        setContentView(shell);
        updateStatus();
    }

    private LinearLayout createPage(android.widget.FrameLayout host, String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(content);
        host.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        TextView heading = label(content, title, 28, 0xfff9fafb);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        label(content, subtitle, 14, 0xff9eafc6);
        return content;
    }

    private void addNavigation(LinearLayout shell, LinearLayout[] pages) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(android.view.Gravity.CENTER);
        bar.setPadding(dp(10), dp(6), dp(10), dp(8));
        bar.setBackgroundColor(0xff192335);
        shell.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        String[] names = {"首页", "音乐", "外观", "关于"};
        TextView[] tabs = new TextView[names.length];
        for (int i=0; i<names.length; i++) {
            final int selected = i;
            TextView tab = new TextView(this);
            tab.setText(names[i]); tab.setTextSize(14); tab.setGravity(android.view.Gravity.CENTER);
            tab.setPadding(dp(5), dp(12), dp(5), dp(12));
            tabs[i] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, dp(52), 1f));
            tab.setOnClickListener(v -> selectPage(pages, tabs, selected));
        }
        selectPage(pages, tabs, 0);
    }
    private void selectPage(LinearLayout[] pages, TextView[] tabs, int selected) {
        for (int i=0; i<pages.length; i++) {
            ((View) pages[i].getParent()).setVisibility(i == selected ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == selected ? 0xffffffff : 0xff9eafc6);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(i == selected ? 0xff36527d : 0x00192335); bg.setCornerRadius(dp(18));
            tabs[i].setBackground(bg); tabs[i].setSelected(i == selected);
        }
    }

    /**
     * Collapsible category used to keep primary settings discoverable while
     * leaving original developer diagnostics accessible in two separate areas.
     */
    private LinearLayout settingsSection(LinearLayout parent, String title,
                                         String subtitle, boolean initiallyOpen) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable surface =
                new android.graphics.drawable.GradientDrawable();
        surface.setColor(0xff1c2535);
        surface.setCornerRadius(dp(16));
        surface.setStroke(dp(1), 0xff344156);
        card.setBackground(surface);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.bottomMargin = dp(14);
        parent.addView(card, outer);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView headingText = new TextView(this);
        headingText.setText(title);
        headingText.setTextSize(17);
        headingText.setTextColor(0xfff9fafb);
        headingText.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(headingText);
        TextView description = new TextView(this);
        description.setText(subtitle);
        description.setTextColor(0xff9eafc6);
        description.setTextSize(12);
        heading.addView(description);
        TextView arrow = new TextView(this);
        arrow.setText(initiallyOpen ? "⌃" : "⌄");
        arrow.setTextColor(0xffcbd5e1);
        arrow.setTextSize(24);
        arrow.setGravity(android.view.Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(40)));

        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setPadding(dp(13), 0, dp(13), dp(12));
        contents.setVisibility(initiallyOpen ? View.VISIBLE : View.GONE);
        card.addView(contents, new LinearLayout.LayoutParams(-1, -2));
        header.setClickable(true);
        header.setContentDescription(title + (initiallyOpen ? "，点击折叠" : "，点击展开"));
        header.setOnClickListener(v -> {
            boolean open = contents.getVisibility() != View.VISIBLE;
            contents.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setText(open ? "⌃" : "⌄");
            header.setContentDescription(title + (open ? "，点击折叠" : "，点击展开"));
        });
        return contents;
    }

    private interface IntChange {
        void changed(int value);
    }

    private void addDimension(LinearLayout parent, String title,
                              int min, int max, int current, IntChange onChange) {
        TextView valueLabel = label(parent, title + "： " + current + " dp", 14, 0xffd1d5db);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(current - min);
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(44)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int next = min + progress;
                valueLabel.setText(title + "： " + next + " dp");
                onChange.changed(next);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private interface Change {
        void changed(boolean value);
    }

    private Switch toggle(LinearLayout layout, String name, boolean current, Change listener) {
        Switch toggle = new Switch(this);
        toggle.setText(name);
        toggle.setTextColor(0xfff9fafb);
        toggle.setTextSize(17);
        toggle.setPadding(0, dp(16), 0, dp(16));
        toggle.setChecked(current);
        toggle.setOnCheckedChangeListener((CompoundButton button, boolean value) ->
                listener.changed(value));
        layout.addView(toggle);
        return toggle;
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    void updateStatus() {
        if (state == null) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean access = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, MusicListenerService.class));
        state.setText("灵动岛：" + (SystemUiAicyClient.enabled(this) ? "已开启" : "已关闭")
                + "\n通知读取：" + (access ? "已授权" : "未授权")
                + "\n已选应用：" + AppSelectionPrefs.get(this).size());
    }

    @Override public void onRequestPermissionsResult(int requestCode,
                                                      String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_REAL_WAVE) {
            boolean granted = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
            MusicPrefs.setRealWave(this, granted);
            if (waveSwitch != null && waveSwitch.isChecked() != granted) waveSwitch.setChecked(granted);
            info.setText(granted ? "已允许尝试真实波形。" : "未授权音频权限，使用普通播放动画。");
        }
        if (requestCode == REQUEST_APP_AUDIO) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) requestPlayerProjection();
            else info.setText("未授权音频权限，使用普通播放动画。");
        }
        if (requestCode == REQUEST_NOTIFICATION) {
            updateStatus();
            info.setText(grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                    ? "已授权通知发送权限。" : "通知发送权限未授予。");
        }
    }

    private void requestPlayerProjection() {
        try {
            android.media.projection.MediaProjectionManager manager = getSystemService(android.media.projection.MediaProjectionManager.class);
            if (manager == null) { info.setText("系统不支持播放音频捕获。"); return; }
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION);
        } catch (RuntimeException failure) { info.setText("无法请求系统播放音频授权。"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode != RESULT_OK || data == null) { info.setText("未授予系统播放捕获授权。"); return; }
        MusicPrefs.setRealWave(this, false);
        MusicListenerService.stopMixedWaveNow();
        if (waveSwitch != null) waveSwitch.setChecked(false);
        Intent start = new Intent(this, AppAudioWaveService.class).setAction(AppAudioWaveService.START)
                .putExtra(AppAudioWaveService.RESULT_CODE, resultCode).putExtra(AppAudioWaveService.RESULT_DATA, data);
        try { startForegroundService(start); info.setText("已请求启动当前播放器的音频波形。"); }
        catch (RuntimeException failure) { info.setText("无法启动播放器音频波形服务。"); }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(LinearLayout parent, String text, int sizeSp, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(sizeSp);
        label.setTextColor(color);
        label.setPadding(0, dp(7), 0, dp(11));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }

    private void button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xfff1f5f9);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xff30415d);
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
        buttonLp.bottomMargin = dp(8);
        parent.addView(button, buttonLp);
    }
}
