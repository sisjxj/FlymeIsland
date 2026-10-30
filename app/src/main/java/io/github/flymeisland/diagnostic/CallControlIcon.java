package io.github.flymeisland.diagnostic;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
/** Vector controls for the compact in-call surface. */
final class CallControlIcon extends Drawable {
    private final String kind; private final boolean active;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    CallControlIcon(String kind,boolean active){this.kind=kind;this.active=active;}
    @Override public void draw(Canvas c){
        android.graphics.Rect r=getBounds();int save=c.save();float side=Math.min(r.width(),r.height());
        c.translate(r.centerX()-side/2f,r.centerY()-side/2f);c.scale(side/24f,side/24f);
        p.setColor("hangup".equals(kind)?0xffff6a72:Color.WHITE);p.setStyle(Paint.Style.FILL);
        if("hangup".equals(kind)) {Path path=new Path();path.moveTo(3,13);path.cubicTo(8,8,16,8,21,13);path.lineTo(19,17);path.lineTo(15,15);path.lineTo(15,13);path.cubicTo(13,12,11,12,9,13);path.lineTo(9,15);path.lineTo(5,17);path.close();c.drawPath(path,p);}
        else {Path path=new Path();path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,5);path.lineTo(12,19);path.lineTo(7,15);path.lineTo(3,15);path.close();c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);c.drawArc(8,6,20,18,-55,110,false,p);if(active)c.drawArc(8,3,24,21,-50,100,false,p);p.setStyle(Paint.Style.FILL);}
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 24;} @Override public int getIntrinsicHeight(){return 24;}
}
