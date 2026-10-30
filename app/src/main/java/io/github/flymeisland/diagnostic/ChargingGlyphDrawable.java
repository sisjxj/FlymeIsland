package io.github.flymeisland.diagnostic;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Original compact green ring/bolt artwork, visually referencing FlymeDuo's charging preview. */
public final class ChargingGlyphDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF(3.4f,3.4f,20.6f,20.6f);
    private final Path bolt = new Path();
    private int alpha=255; private ColorFilter filter;
    public ChargingGlyphDrawable() {
        bolt.moveTo(13.6f,3.9f); bolt.lineTo(7.8f,12.7f); bolt.lineTo(11.3f,12.7f);
        bolt.lineTo(10.4f,20.1f); bolt.lineTo(16.6f,10.8f); bolt.lineTo(13.0f,10.8f); bolt.close();
    }
    @Override public void draw(Canvas canvas) {
        int save=canvas.save();
        canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        paint.setColor(0xff202421);paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);paint.setColorFilter(filter);
        canvas.drawCircle(12f,12f,12f,paint);
        paint.setColor(0xff53c56d);paint.setAlpha(alpha);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.4f);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(ring,145f,90f,false,paint);canvas.drawArc(ring,305f,90f,false,paint);
        paint.setColor(0xfff9fbf8);paint.setAlpha(alpha);paint.setStyle(Paint.Style.FILL);canvas.drawPath(bolt,paint);
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int a) {alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f) {filter=f;invalidateSelf();}
    @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
}
