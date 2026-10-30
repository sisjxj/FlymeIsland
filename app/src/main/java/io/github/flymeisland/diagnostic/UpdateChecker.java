package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Manual, read-only release check. Downloads and installs require a separate user action. */
final class UpdateChecker {
    static final String REPOSITORY = "https://github.com/sisjxj/FlymeIsland";
    static final String RELEASES = REPOSITORY + "/releases";
    private static final String API = "https://api.github.com/repos/sisjxj/FlymeIsland/releases?per_page=1";
    private UpdateChecker() {}
    static void open(Activity activity, String url) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException e) { android.widget.Toast.makeText(activity, "无法打开浏览器", android.widget.Toast.LENGTH_SHORT).show(); }
    }
    static void check(Activity activity, TextView result) {
        result.setText("正在检查更新…");
        new Thread(() -> {
            String message;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "FlymeIsland-Android");
                int status = c.getResponseCode();
                if (status != 200) throw new java.io.IOException("HTTP " + status);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (bytes.size() + n > 262144) throw new java.io.IOException("response too large");
                        bytes.write(buffer, 0, n);
                    }
                }
                JSONArray releases = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (releases.length() == 0) message = "暂时没有公开版本，可打开发布页面查看。";
                else {
                    JSONObject latest = releases.getJSONObject(0);
                    String tag = latest.optString("tag_name", "未知").replaceAll("[\\p{Cntrl}\\p{Cf}]", " ");
                    if (tag.length() > 60) tag = tag.substring(0,60);
                    message = "最新发布：" + tag + (latest.optBoolean("prerelease", false) ? "（预发布）" : "") + "。打开发布页面查看详情。";
                }
            } catch (Exception error) { message = "检查失败，请检查网络或打开发布页面。"; }
            finally { if (c != null) c.disconnect(); }
            String shown = message;
            activity.runOnUiThread(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) result.setText(shown); });
        }, "FlymeIsland-update-check").start();
    }
}
