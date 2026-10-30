package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Official, free-of-charge distribution notice. GPL-3.0-only still permits
 * lawful redistribution, modifications, and charging for distribution.
 * This notice is an identity / anti-impersonation warning, not an additional
 * licensing restriction, and never blocks app functionality.
 */
public final class ProjectNotice {
    private static final String PREFS = "flymeisland_notices";
    private static final String SHOWN_KEY = "official_free_notice_v062_shown";

    public static final String TITLE = "免费开源 · 防冒充收费提醒";
    public static final String SUMMARY =
            "Flyme Island 官方版本免费获取，不收取下载费、激活费或使用费。"
            + "请认准 GitHub：sisjxj/FlymeIsland，警惕冒充官方的收费链接与虚假授权。";
    public static final String DETAILS =
            "项目：Flyme Island\n"
            + "原作者 / 维护者：sisjxj\n"
            + "官方仓库：https://github.com/sisjxj/FlymeIsland\n"
            + "版权所有 © 2026 sisjxj（项目原创部分）。\n\n"
            + "【免费声明】维护者在官方 GitHub 免费提供本项目的源代码和公开发布包，"
            + "不设置付费下载、付费激活或付费解锁。任何自称必须付费才能获得「官方授权」的说法，"
            + "请先到官方仓库核实。\n\n"
            + "【防冒充提醒】请警惕以 Flyme Island 官方、维护者名义收款，"
            + "或擅自删除版权及许可证声明、将修改版冒充官方版本的行为。"
            + "非官方版本可能经过修改，请自行核对来源与安全性。\n\n"
            + "【开源许可】本项目采用 GNU GPL-3.0-only。"
            + "依照该许可证，其他人可以在符合条款的前提下复制、修改和再分发，"
            + "也可能对分发服务收费。本提醒不改变 GPL 赋予的权利。"
            + "再分发应遵守版权、许可及相应源码提供要求。\n\n"
            + "本项目与魅族官方无隶属关系；软件按许可证约定不提供任何担保。";

    private ProjectNotice() {}

    /** Visible from About at any time; has no mandatory accept/deny step. */
    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(TITLE)
                .setMessage(DETAILS)
                .setNeutralButton("打开官方 GitHub", (dialog, which) ->
                        UpdateChecker.open(activity, UpdateChecker.REPOSITORY))
                .setPositiveButton("知道了", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /** One gentle reminder per installation/version; not shown on every launch. */
    public static void showOnce(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SHOWN_KEY, false)) return;
        prefs.edit().putBoolean(SHOWN_KEY, true).apply();
        activity.getWindow().getDecorView().post(() -> show(activity));
    }
}
