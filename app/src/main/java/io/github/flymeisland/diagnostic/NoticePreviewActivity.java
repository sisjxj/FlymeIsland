package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

/**
 * Harmless coexistence-only visual preview: no LSPosed registration,
 * no notification listener and no SystemUI broadcast.
 */
public final class NoticePreviewActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff111827);
        getWindow().setNavigationBarColor(0xff111827);
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackgroundColor(0xff111827);
        ScrollView scroll = new ScrollView(this);
        base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(24));
        scroll.addView(page);
        label(page, "Flyme Island", 29, 0xfff9fafb, true);
        label(page, "关于 · 文案预览", 14, 0xff9eafc6, false);
        label(page, "这是一份独立共存的展示版。只预览版权提示，"
                + "不申请通知权限，也不会修改正式版的胶囊或 LSPosed 设置。",
                13, 0xffffd27d, false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(18));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(0xff1c2535);
        surface.setCornerRadius(dp(18));
        surface.setStroke(dp(1), 0xff344156);
        card.setBackground(surface);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(22);
        page.addView(card, cp);

        label(card, "版本与开源", 20, 0xfff9fafb, true);
        label(card, "Flyme Island · 声明预览版\n"
                + "官方仓库：sisjxj/FlymeIsland\n"
                + "许可证：GPL-3.0-only", 14, 0xffcbd5e1, false);
        label(card, "这个项目，免费分享给大家", 16, 0xffffd27d, true);
        label(card, ProjectNotice.SUMMARY, 14, 0xfff5e8cf, false);
        action(card, "看看作者想说的话", () -> ProjectNotice.show(this));
        label(card, "© 2026 sisjxj（原创部分） · GPL-3.0-only。"
                + "项目不是魅族官方产品，转载和改版请遵守开源协议。",
                12, 0xff9ca3af, false);

        action(card, "打开官方 GitHub", () ->
                UpdateChecker.open(this, UpdateChecker.REPOSITORY));
        label(page, "提示：打开这个共存版后，首次弹窗只显示一次。"
                + "以后仍可通过「看看作者想说的话」重复查看。",
                12, 0xff9ca3af, false);

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(14), dp(9), dp(14), dp(10));
        footer.setBackgroundColor(0xff192335);
        base.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        TextView selected = new TextView(this);
        selected.setText("关于 · 免费开源说明预览");
        selected.setTextSize(14);
        selected.setTextColor(0xfff9fafb);
        selected.setGravity(Gravity.CENTER);
        footer.addView(selected, new LinearLayout.LayoutParams(-1, dp(46)));

        setContentView(base);
        ProjectNotice.showOnce(this);
    }

    private TextView label(LinearLayout parent, String value, int size, int color,
                           boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(7), 0, dp(9));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void action(LinearLayout parent, String text, Runnable run) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xfff1f5f9);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff30415d);
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(5);
        p.bottomMargin = dp(8);
        parent.addView(button, p);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
