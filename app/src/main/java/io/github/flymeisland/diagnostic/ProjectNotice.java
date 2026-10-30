package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Friendly, truthful attribution and anti-impersonation notice.
 * An unofficial redistribution warning is not an additional GPL restriction.
 */
public final class ProjectNotice {
    private static final String PREFS = "flymeisland_notices";
    private static final String SHOWN_KEY = "friendly_notice_v063_shown";

    public static final String TITLE = "关于 Flyme Island · 说两句";
    public static final String SUMMARY =
            "Flyme Island 是我做的免费开源小项目，官方安装包都放在 GitHub。"
            + "如果有人说要付费激活、购买授权，先别急着付钱，来仓库看看。";

    public static final String DETAILS =
            "嗨，感谢你愿意试试 Flyme Island！\n\n"
            + "这是我自己慢慢做起来的一个开源小项目。"
            + "源代码和我发布的安装包都能在 GitHub 免费获取，"
            + "没有什么付费激活码，也不需要买会员才能用。\n\n"
            + "如果你在其他地方看到了收费的“官方版”或所谓的“授权码”，"
            + "先别急着付钱，最好来我的仓库核对一下。"
            + "也请不要把别人的修改版说成是我发布的原版。\n\n"
            + "官方地址：\nhttps://github.com/sisjxj/FlymeIsland\n\n"
            + "这个项目采用 GPL-3.0-only 开源协议："
            + "遵守协议可以修改、分享、再发布，也可以收费提供分发服务。"
            + "但请按协议保留版权和许可信息，并提供相应源代码。"
            + "我在 GitHub 免费分享，不等于所有第三方都必须免费分发。\n\n"
            + "© 2026 sisjxj（项目原创部分）\n"
            + "这是独立的社区作品，不代表魅族官方，也不提供使用担保。\n\n"
            + "谢谢支持，祝你用得开心！";

    private ProjectNotice() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(TITLE)
                .setMessage(DETAILS)
                .setNeutralButton("去 GitHub 看看", (dialog, which) ->
                        UpdateChecker.open(activity, UpdateChecker.REPOSITORY))
                .setPositiveButton("知道啦", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void showOnce(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SHOWN_KEY, false)) return;
        prefs.edit().putBoolean(SHOWN_KEY, true).apply();
        activity.getWindow().getDecorView().post(() -> show(activity));
    }
}
