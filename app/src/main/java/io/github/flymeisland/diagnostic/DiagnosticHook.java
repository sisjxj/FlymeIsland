package io.github.flymeisland.diagnostic;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Install only the SystemUI bridge and the optional Aicy presence observer. */
public final class DiagnosticHook implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (AicyPresenceBridge.AICY_PACKAGE.equals(param.packageName)) {
            AicyPresenceBridge.installInAicy(param);
            return;
        }
        if ("com.android.systemui".equals(param.packageName)
                && "com.android.systemui".equals(param.processName) && param.classLoader != null)
            SystemUiMiniBridgeInstaller.install(param.classLoader);
    }
}
