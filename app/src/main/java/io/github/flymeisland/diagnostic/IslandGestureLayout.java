package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Intercepts directional gestures above child views without breaking the
 * close and mini-window buttons' ordinary click handling.
 *
 * Swipe up: retract expanded card. Left/right: dismiss ONLY our window.
 */
public class IslandGestureLayout extends FrameLayout {
    public interface Callback {
        boolean isExpanded();
        void retract();
        void dismiss();
        default String mediaGestureKey() { return null; }
        default void mediaSwipe(String key, boolean next) {}
        default void mediaLongPress(String key) {}
    }

    private final int slop;
    private float startX, startY;
    private int activeGesture; // 0=none 1=left 2=right 3=up
    private int swipeMode = SystemUiIslandPrefs.SWIPE_RIGHT;
    private Callback callback;
    private String downMediaKey;
    private boolean longPressFired;
    private final Runnable longPress = () -> {
        if (downMediaKey == null || callback == null || callback.isExpanded()
                || !downMediaKey.equals(callback.mediaGestureKey())) return;
        longPressFired = true;
        callback.mediaLongPress(downMediaKey);
    };
    protected float gestureWidth() { return getWidth(); }
    private void cancelGestureLongPress() { removeCallbacks(longPress); }
    private boolean mediaGestureActive() {
        return downMediaKey != null && callback != null && !callback.isExpanded()
                && downMediaKey.equals(callback.mediaGestureKey());
    }

    public IslandGestureLayout(Context c) {
        super(c);
        setClickable(true);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void setSwipeMode(int value) {
        swipeMode = SystemUiIslandPrefs.clamp(value,
                SystemUiIslandPrefs.SWIPE_OFF, SystemUiIslandPrefs.SWIPE_BOTH);
    }

    public void setGestureCallback(Callback value) {
        callback = value;
    }

    private int recognize(float dx, float dy) {
        if (callback != null && callback.isExpanded() && dy < -slop * 1.7f
                && Math.abs(dy) > Math.abs(dx) * 1.25f) return 3;
        if (Math.abs(dx) < slop * 1.5f || Math.abs(dx) < Math.abs(dy) * 1.25f)
            return 0;
        if (mediaGestureActive()) return dx > 0 ? 2 : 1;
        if (dx > 0 && (swipeMode & SystemUiIslandPrefs.SWIPE_RIGHT) != 0) return 2;
        if (dx < 0 && (swipeMode & SystemUiIslandPrefs.SWIPE_LEFT) != 0) return 1;
        return 0;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startX = event.getRawX();
            startY = event.getRawY();
            activeGesture = 0;
            return super.onInterceptTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (activeGesture == 0) activeGesture = recognize(
                    event.getRawX() - startX, event.getRawY() - startY);
            if (activeGesture != 0) return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                activeGesture = 0;
                longPressFired = false;
                cancelGestureLongPress();
                downMediaKey = callback != null && !callback.isExpanded() ? callback.mediaGestureKey() : null;
                if (downMediaKey != null) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                if (longPressFired) return true;
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) cancelGestureLongPress();
                if (activeGesture == 0) activeGesture = recognize(dx, dy);
                if (activeGesture == 3) {
                    setTranslationY(Math.max(-dp(32), Math.min(0f, dy * .35f)));
                } else if ((activeGesture == 1 || activeGesture == 2) && downMediaKey == null) {
                    setTranslationX(Math.max(-getWidth(), Math.min(getWidth(), dx)));
                    setAlpha(Math.max(.35f, 1f - Math.abs(dx) / Math.max(1, getWidth())));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                cancelGestureLongPress();
                if (longPressFired) { longPressFired = false; downMediaKey = null; activeGesture = 0; return true; }
                if (mediaGestureActive() && (activeGesture == 1 || activeGesture == 2)) {
                    if (Math.abs(dx) > Math.max(dp(32), Math.min(dp(72), gestureWidth() * .3f)))
                        callback.mediaSwipe(downMediaKey, activeGesture == 1);
                    resetVisual();
                } else if (downMediaKey != null) {
                    // Never turn a stale or short media gesture into a dismiss.
                    if (activeGesture == 0 && mediaGestureActive() && Math.abs(dx) < slop && Math.abs(dy) < slop) performClick();
                    else resetVisual();
                } else if (activeGesture == 3 && dy < -dp(48)) {
                    resetVisual();
                    if (callback != null) callback.retract();
                } else if ((activeGesture == 1 || activeGesture == 2)
                        && Math.abs(dx) > Math.max(dp(62), getWidth() * .25f)) {
                    resetVisual();
                    if (callback != null) callback.dismiss();
                } else if (activeGesture != 0) {
                    animate().translationX(0f).translationY(0f)
                            .alpha(1f).setDuration(170L).start();
                } else if (Math.abs(dx) < slop && Math.abs(dy) < slop) {
                    performClick();
                }
                activeGesture = 0;
                downMediaKey = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelGestureLongPress();
                downMediaKey = null;
                longPressFired = false;
                resetVisual();
                activeGesture = 0;
                return true;
            default:
                return true;
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelGestureLongPress(); downMediaKey = null; longPressFired = false;
        super.onDetachedFromWindow();
    }

    public void resetVisual() {
        animate().cancel();
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
