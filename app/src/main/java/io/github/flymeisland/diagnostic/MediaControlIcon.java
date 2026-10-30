package io.github.flymeisland.diagnostic;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small local vector symbols for transport controls; no font glyph dependency. */
final class MediaControlIcon extends Drawable {
    private final String action;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    MediaControlIcon(String action) { this.action = action; paint.setColor(Color.WHITE); }
    @Override public void draw(Canvas c) {
        android.graphics.Rect b=getBounds(); int save=c.save();
        float side=Math.min(b.width(), b.height());
        c.translate(b.centerX()-side/2f,b.centerY()-side/2f); c.scale(side/24f,side/24f);
        if ("pause".equals(action)) { c.drawRoundRect(6,4,10,20,1,1,paint); c.drawRoundRect(14,4,18,20,1,1,paint); }
        else if ("play".equals(action)) triangle(c,6,4,20,12,6,20);
        else if ("previous".equals(action)) { c.drawRoundRect(4,4,7,20,1,1,paint); triangle(c,19,4,8,12,19,20); }
        else if ("next".equals(action)) { c.drawRoundRect(17,4,20,20,1,1,paint); triangle(c,5,4,16,12,5,20); }
        else if ("favorite".equals(action)) {
            Path p=new Path(); p.moveTo(12,21); p.cubicTo(10,19,3,14,3,9); p.cubicTo(3,3,9,2,12,6); p.cubicTo(15,2,21,3,21,9); p.cubicTo(21,14,14,19,12,21); p.close(); c.drawPath(p,paint);
        } else if ("lyrics".equals(action)) {
            c.drawRoundRect(4,4,20,6,1,1,paint); c.drawRoundRect(4,9,20,11,1,1,paint); c.drawRoundRect(4,14,16,16,1,1,paint); c.drawRoundRect(4,19,13,21,1,1,paint);
        } else if ("shuffle".equals(action)) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.2f); paint.setStrokeCap(Paint.Cap.ROUND);
            Path p=new Path();p.moveTo(3,6);p.cubicTo(9,6,13,18,20,18);p.moveTo(3,18);p.cubicTo(9,18,13,6,20,6);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);
            triangle(c,18,3,22,6,18,9);triangle(c,18,15,22,18,18,21);
        }
        c.restoreToCount(save);
    }
    private void triangle(Canvas c,float x1,float y1,float x2,float y2,float x3,float y3) {
        Path p=new Path(); p.moveTo(x1,y1);p.lineTo(x2,y2);p.lineTo(x3,y3);p.close();c.drawPath(p,paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
