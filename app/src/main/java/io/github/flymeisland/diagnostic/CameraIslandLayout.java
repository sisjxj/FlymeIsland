package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.AttachedSurfaceControl;
import android.view.MotionEvent;
import android.view.View;

/**
 * Morph a rounded black viewport, never the pixels of its children.
 * Both content layouts keep their final measured size throughout a transition.
 * Geometry is LOCAL to a fixed camera anchor; no screen-position feedback or root scale.
 */
public final class CameraIslandLayout extends IslandGestureLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chargePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chargeGlow = new RectF();
    private boolean charging;
    private float chargePulse;
    private ValueAnimator chargeAnimator;
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private int panelW = 1, panelH = 1, pillW = 1, pillH = 1;
    private int callMediumW = 1, callMediumH = 1;
    public void setCallMediumSize(int w, int h) { callMediumW=w; callMediumH=h; requestLayout(); }
    private float visualW = 1f, visualH = 1f, visualTop, visualRadius;
    private float localTop = Float.NaN;
    private final Region touchRegion = new Region();
    private final Region previousTouchRegion = new Region();
    private final Region windowRegion = new Region();
    private boolean inputRegionReady;
    private Runnable onInputReady;
    private Drawable sideIcon;
    private final Path sidePath = new Path();
    private final Path bridgePath = new Path();
    private final Path iconPath = new Path();
    private final RectF iconBounds = new RectF();
    private final RectF sideBounds = new RectF();
    private final Region sideRegion = new Region();
    private boolean sidePressed;
    private Runnable onSideClick;
    private String sideKey;
    public interface SideGestureHandler {
        boolean enabled(String key);
        String identity(String key);
        void swipe(String key, boolean next);
        void longPress(String key);
    }
    private SideGestureHandler sideGestureHandler;
    private String downSideKey, downSideIdentity;
    private float sideDownX, sideDownY;
    private boolean sideLongFired, sideMoved, sideWasMediaGesture;
    private final Runnable sideLongPress = () -> {
        if (!sidePressed || downSideKey == null || !downSideKey.equals(sideKey)
                || sideGestureHandler == null || !sideGestureHandler.enabled(downSideKey)
                || !java.util.Objects.equals(downSideIdentity, sideGestureHandler.identity(downSideKey))) return;
        sideLongFired = true;
        sideGestureHandler.longPress(downSideKey);
    };
    public void setSideGestureHandler(SideGestureHandler handler) { sideGestureHandler = handler; }
    @Override protected float gestureWidth() { return visualW; }
    private ValueAnimator sideAnimator;
    private float sideProgress;
    private boolean sideBirthDeferred;
    private SideBubbleGeometry sideGeometry;

    public CameraIslandLayout(Context context) {
        super(context);
        paint.setColor(Color.BLACK);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

    }

    public void setCharging(boolean enabled) {
        if (charging == enabled) return;
        charging = enabled;
        if (chargeAnimator != null) { chargeAnimator.removeAllUpdateListeners(); chargeAnimator.cancel(); chargeAnimator = null; }
        if (enabled) {
            chargeAnimator = ValueAnimator.ofFloat(0f, 1f);
            chargeAnimator.setDuration(1100L); chargeAnimator.setRepeatCount(ValueAnimator.INFINITE);
            chargeAnimator.setRepeatMode(ValueAnimator.REVERSE);
            chargeAnimator.addUpdateListener(a -> { chargePulse = (Float) a.getAnimatedValue(); invalidate(); });
            chargeAnimator.start();
        } else chargePulse = 0f;
        invalidate();
    }

    public float visualWidth() { return visualW; }
    public float visualHeight() { return visualH; }
    public float visualTop() { return visualTop; }
    public boolean isInputRegionReady() { return inputRegionReady; }

    public void setSide(String key, Drawable icon, Runnable action) {
        if (key != null && key.equals(sideKey)) {
            onSideClick = action;
            if (icon != null) { sideIcon = icon.mutate(); invalidate(); }
            return;
        }
        sideKey = key; onSideClick = action;
        cancelSideAnimator();
        if (icon != null) {
            sideIcon = icon.mutate();
            sideProgress = 0f;
            if (sideBirthDeferred || !isCompactFrame()) {
                sideBirthDeferred = true;
                updateViewport(); invalidate();
            } else startSideAnimation(true);
        } else if (sideBirthDeferred) {
            sideBirthDeferred = false; sideProgress = 0f; sideIcon = null;
            updateViewport(); invalidate();
        } else startSideAnimation(false);
    }

    /** Hold the secondary bubble while an incoming card is expanding. */
    public void deferSideBirth() {
        sideBirthDeferred = true;
        cancelSideAnimator(); sideProgress = 0f;
        updateViewport(); invalidate();
    }

    /** Called after the compact transition and primary-source swap have settled. */
    public void releaseDeferredSide() {
        if (!sideBirthDeferred || !isCompactFrame()) return;
        sideBirthDeferred = false;
        if (sideIcon != null && sideKey != null) startSideAnimation(true);
    }

    private boolean isCompactFrame() { return visualW <= pillW + 1f && visualH <= pillH + 1f; }
    private void cancelSideAnimator() {
        if (sideAnimator == null) return;
        sideAnimator.removeAllListeners(); sideAnimator.removeAllUpdateListeners(); sideAnimator.cancel(); sideAnimator = null;
    }
    private void startSideAnimation(boolean show) {
        final ValueAnimator animator = ValueAnimator.ofFloat(sideProgress, show ? 1f : 0f);
        sideAnimator = animator; animator.setDuration(360L);
        animator.setInterpolator(t -> t*t*(3f-2f*t));
        animator.addUpdateListener(a -> {
            sideProgress = (Float) a.getAnimatedValue(); updateViewport(); invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (sideAnimator != a) return;
                sideAnimator = null; if (!show) sideIcon = null;
            }
        });
        animator.start();
    }

    public void whenInputReady(Runnable callback) {
        onInputReady = callback;
        if (inputRegionReady && callback != null) callback.run();
    }

    public void setContentSizes(int pw, int ph, int sw, int sh) {
        if (panelW == pw && panelH == ph && pillW == sw && pillH == sh) return;
        panelW = pw; panelH = ph; pillW = sw; pillH = sh;
        requestLayout();
    }

    public void setFrame(float width, float height, float top, float radius) {
        visualW = width; visualH = height; visualTop = top;
        visualRadius = Math.min(radius, Math.min(width, height) * .5f);
        updateViewport();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        for (int i = 0; i < getChildCount(); i++) {
            // Child 0 is the expanded card, child 1 is the compact badge.
            int width = i == 0 ? panelW : i == 1 ? pillW : callMediumW;
            int height = i == 0 ? panelH : i == 1 ? pillH : callMediumH;
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int x = (getWidth() - child.getMeasuredWidth()) / 2;
            child.layout(x, 0, x + child.getMeasuredWidth(), child.getMeasuredHeight());
        }
        updateViewport();
    }

    private void updateViewport() {
        float nextTop = visualTop;
        if (nextTop != localTop) {
            localTop = nextTop;
            invalidate();
        }
        float x = (getWidth() - visualW) * .5f;
        bounds.set(x, localTop, x + visualW, localTop + visualH);
        clip.rewind();
        clip.addRoundRect(bounds, visualRadius, visualRadius, Path.Direction.CW);
        sidePath.rewind(); bridgePath.rewind(); sideBounds.setEmpty(); sideGeometry = null;
        if (sideIcon != null && !sideBirthDeferred && isCompactFrame() && sideProgress > 0f) {
            float gap = getResources().getDisplayMetrics().density * 5f;
            float diameter = Math.min(pillH, Math.max(0f, getWidth() - bounds.right - gap));
            SideBubbleGeometry g = SideBubbleGeometry.calculate(bounds.right, localTop, visualH, diameter, gap, sideProgress);
            if (g.radius > 0f) {
                sideGeometry = g;
                sideBounds.set(g.centerX-g.radius,g.centerY-g.radius,g.centerX+g.radius,g.centerY+g.radius);
                sidePath.addOval(sideBounds, Path.Direction.CW);
                if (g.neck > 0f) {
                    float edge = Math.max(g.neck, Math.min(visualH*.30f, diameter*.22f));
                    float middle = (g.bridgeStartX+g.bridgeEndX)*.5f;
                    bridgePath.moveTo(g.bridgeStartX,g.centerY-edge);
                    bridgePath.cubicTo(middle,g.centerY-edge,middle,g.centerY-g.neck,g.bridgeEndX,g.centerY-g.neck);
                    bridgePath.lineTo(g.bridgeEndX,g.centerY+g.neck);
                    bridgePath.cubicTo(middle,g.centerY+g.neck,middle,g.centerY+edge,g.bridgeStartX,g.centerY+edge);
                    bridgePath.close();
                }
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setTranslationY(localTop + (i == 0 ? 0f : (visualH - pillH) * .5f));
        }
        updateInputRegion();
    }

    private void updateInputRegion() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        AttachedSurfaceControl surface = getRootSurfaceControl();
        if (surface == null) return;
        if (getVisibility() != View.VISIBLE || getAlpha() <= 0f) {
            touchRegion.setEmpty();
        } else {
            windowRegion.set(0, 0, getWidth(), getHeight());
            touchRegion.setPath(clip, windowRegion);
            if (!sideBounds.isEmpty() && sideProgress >= .65f) {
                sideRegion.setPath(sidePath, windowRegion);
                touchRegion.op(sideRegion, Region.Op.UNION);
            }
        }
        try {
            if (!inputRegionReady || !touchRegion.equals(previousTouchRegion)) {
                surface.setTouchableRegion(new Region(touchRegion));
                previousTouchRegion.set(touchRegion);
            }
            if (!inputRegionReady) {
                inputRegionReady = true;
                if (onInputReady != null) onInputReady.run();
            }
        } catch (RuntimeException unavailable) {
            // The host starts NOT_TOUCHABLE. If region setup fails, never
            // intercept touches across the entire transparent host rectangle.
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::updateInputRegion);
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (touchRegion != null) updateInputRegion();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (charging && isCompactFrame()) {
            float density = getResources().getDisplayMetrics().density;
            chargePaint.setStyle(Paint.Style.STROKE);
            for (int i=3;i>=1;i--) {
                float spread = density * i * 1.6f;
                chargeGlow.set(bounds.left-spread,bounds.top-spread,bounds.right+spread,bounds.bottom+spread);
                chargePaint.setColor(0xff6be59a);
                chargePaint.setAlpha(Math.round((9 + chargePulse * 8) * (4-i)));
                chargePaint.setStrokeWidth(density * 1.7f);
                canvas.drawRoundRect(chargeGlow,visualRadius+spread,visualRadius+spread,chargePaint);
            }
        }
        canvas.drawRoundRect(bounds, visualRadius, visualRadius, paint);
        if (charging && isCompactFrame()) {
            float d = getResources().getDisplayMetrics().density;
            chargePaint.setStyle(Paint.Style.STROKE); chargePaint.setColor(0xffffffff);
            chargePaint.setAlpha(Math.round(25 + chargePulse * 18)); chargePaint.setStrokeWidth(Math.max(1f,d));
            chargeGlow.set(bounds.left+d*2,bounds.top+d,bounds.right-d*2,bounds.bottom-d);
            canvas.drawRoundRect(chargeGlow,Math.max(0,visualRadius-d),Math.max(0,visualRadius-d),chargePaint);
        }
        if (!sideBounds.isEmpty()) {
            if (sideGeometry != null && sideGeometry.neck > 0f) canvas.drawPath(bridgePath, paint);
            canvas.drawOval(sideBounds, paint);
            int pad = Math.round(sideBounds.width() * .18f);
            sideIcon.setAlpha(Math.round(Math.max(0f, Math.min(1f, (sideProgress-.35f)/.65f)) * 255));
            sideIcon.setBounds(Math.round(sideBounds.left) + pad, Math.round(sideBounds.top) + pad,
                    Math.round(sideBounds.right) - pad, Math.round(sideBounds.bottom) - pad);
            int iconSave = canvas.save();
            iconBounds.set(sideBounds.left + pad, sideBounds.top + pad,
                    sideBounds.right - pad, sideBounds.bottom - pad);
            iconPath.rewind();
            iconPath.addOval(iconBounds, Path.Direction.CW);
            canvas.clipPath(iconPath);
            sideIcon.draw(canvas);
            canvas.restoreToCount(iconSave);
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override protected void onDetachedFromWindow() {
        setCharging(false); cancelSideAnimator(); removeCallbacks(sideLongPress); sidePressed = false; downSideKey = null; downSideIdentity = null;
        super.onDetachedFromWindow();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            sidePressed = sideProgress >= .65f && !sideBounds.isEmpty() && sideBounds.contains(event.getX(), event.getY());
            if (sidePressed) {
                downSideKey = sideKey; sideDownX = event.getRawX(); sideDownY = event.getRawY();
                sideLongFired = false; sideMoved = false;
                downSideIdentity = sideGestureHandler == null ? null : sideGestureHandler.identity(downSideKey);
                sideWasMediaGesture = sideGestureHandler != null && sideGestureHandler.enabled(downSideKey);
                removeCallbacks(sideLongPress);
                if (sideWasMediaGesture)
                    postDelayed(sideLongPress, android.view.ViewConfiguration.getLongPressTimeout());
            }
        }
        if (sidePressed) {
            float dx = event.getRawX() - sideDownX, dy = event.getRawY() - sideDownY;
            int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                sideMoved = true; removeCallbacks(sideLongPress);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                removeCallbacks(sideLongPress);
                boolean valid = downSideKey != null && downSideKey.equals(sideKey);
                boolean enabled = valid && sideGestureHandler != null && sideGestureHandler.enabled(downSideKey)
                        && java.util.Objects.equals(downSideIdentity, sideGestureHandler.identity(downSideKey));
                if (!sideLongFired && enabled && sideMoved && Math.abs(dx) > Math.max(slop * 2f, sideBounds.width() * .45f)
                        && Math.abs(dx) > Math.abs(dy) * 1.25f)
                    sideGestureHandler.swipe(downSideKey, dx < 0);
                else if (!sideLongFired && !sideMoved && valid && (!sideWasMediaGesture || enabled) && sideBounds.contains(event.getX(), event.getY()) && onSideClick != null)
                    onSideClick.run();
                sidePressed = false; downSideKey = null; downSideIdentity = null;
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(sideLongPress); sidePressed = false; downSideKey = null; downSideIdentity = null;
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !bounds.contains(event.getX(), event.getY())) return false;
        return super.dispatchTouchEvent(event);
    }
}
