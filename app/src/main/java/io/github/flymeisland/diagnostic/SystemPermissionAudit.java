package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

/**
 * Read-only Android PackageManager assessment; no privileged requests,
 * broadcasts, signature-permission grants, or SystemUI hooks.
 */
public final class SystemPermissionAudit {
    private static final String PERMISSION =
            "android.permission.START_TASKS_FROM_RECENTS";
    private static final String[] PACKAGES = {
            "io.github.flymeisland.diagnostic",
            "com.android.systemui",
            "com.flyme.systemuitools",
            "com.meizu.flyme.launcher"
    };

    private SystemPermissionAudit() {}

    public static String report(Context context) {
        PackageManager pm = context.getPackageManager();
        StringBuilder b = new StringBuilder();
        b.append("Flyme Island v0.16 只读权限核验");
        b.append("\n本应用进程 UID=").append(Process.myUid());
        b.append("\n查询权限：").append(PERMISSION);
        for (String pkg : PACKAGES) {
            b.append("\n\n").append(pkg);
            try {
                int uid = pm.getPackageUid(pkg, 0);
                b.append("\nUID=").append(uid);
                int state = pm.checkPermission(PERMISSION, pkg);
                b.append("\n运行时授权=")
                        .append(state == PackageManager.PERMISSION_GRANTED ? "GRANTED" :
                                state == PackageManager.PERMISSION_DENIED ? "DENIED" : "UNKNOWN");
            } catch (PackageManager.NameNotFoundException e) {
                b.append("\n系统无法查询该包（未安装或包可见性限制）");
            } catch (RuntimeException e) {
                b.append("\n查询失败：").append(e.getClass().getSimpleName());
            }
        }
        try {
            int s = pm.checkSignatures("com.android.systemui", "com.flyme.systemuitools");
            b.append("\n\nSystemUI / SystemUITools 签名：")
                    .append(s == PackageManager.SIGNATURE_MATCH ? "MATCH" :
                            s == PackageManager.SIGNATURE_NO_MATCH ? "NO_MATCH" : "UNKNOWN(" + s + ")");
        } catch (RuntimeException e) {
            b.append("\n\n签名核验失败：").append(e.getClass().getSimpleName());
        }
        b.append("\n\n只读检查：没有触发小窗操作，没有修改权限或系统设置。");
        b.append("\nGRANTED 只证明该包获授权，不说明其内部入口对第三方开放。");
        return b.toString();
    }
}
