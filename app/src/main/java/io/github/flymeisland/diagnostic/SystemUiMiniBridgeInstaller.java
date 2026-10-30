package io.github.flymeisland.diagnostic;

import android.app.Application;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Install the authenticated in-process SystemUI receiver. */
public final class SystemUiMiniBridgeInstaller {
    private SystemUiMiniBridgeInstaller() {}
    public static void install(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("com.android.systemui.SystemUIApplication", false, loader),
                    "onCreate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable() || !(p.thisObject instanceof Application)) return;
                            try {
                                XposedBridge.log("[FlymeIsland] receiver: "
                                        + SystemUiMiniBridgeServer.register((Application) p.thisObject));
                            } catch (Throwable error) {
                                XposedBridge.log("[FlymeIsland] receiver unavailable: "
                                        + error.getClass().getSimpleName());
                            }
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log("[FlymeIsland] SystemUI hook unavailable: "
                    + error.getClass().getSimpleName());
        }
    }
}
