package de.robv.android.xposed;
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static class MethodHookParam {
        public Object[] args;
        public Object thisObject;
        public Object getResult() { throw new UnsupportedOperationException("compile-only stub"); }
        public boolean hasThrowable() { throw new UnsupportedOperationException("compile-only stub"); }
    }
    public class Unhook {}
}
