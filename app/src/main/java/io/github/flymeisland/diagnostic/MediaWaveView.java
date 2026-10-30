package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Decorative playback-state animation; never captures or infers audio samples. */
public final class MediaWaveView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;
    private byte[] levels;
    private long lastSample;
    private final float[] smooth = new float[5];
    private final float[] hsv = {180f, .64f, 1f};
    private final float[] accentHsv = new float[3];
    private boolean hasAccent;
    public void setAccentColor(int color) {
        hasAccent = color != 0;
        if (hasAccent) Color.colorToHSV(color, accentHsv);
        invalidate();
    }
    private int bars=5, barWidth=3, gap=3, gain=200, arrangement=0;
    public void configure(int[] style) {
        if (style == null || style.length != WaveStylePrefs.DEFAULTS.length) return;
        bars=Math.max(3,Math.min(9,style[0]));barWidth=Math.max(1,Math.min(6,style[1]));
        gap=Math.max(1,Math.min(8,style[2]));gain=Math.max(50,Math.min(500,style[4]));arrangement=style[5];
        invalidate();
    }
    public void setLevels(byte[] value) {
        levels = value.clone();
        for (int i=0; i<5; i++) {
            float next=(levels[i] & 255)/255f;
            smooth[i] += (next-smooth[i]) * (next>smooth[i] ? .42f : .24f);
        }
        lastSample = SystemClock.uptimeMillis(); invalidate();
    }
    public MediaWaveView(Context context) { super(context); paint.setColor(Color.WHITE); paint.setStrokeCap(Paint.Cap.ROUND); }
    public void setPlaying(boolean value) { playing = value; setContentDescription(value ? "媒体正在播放" : "媒体已暂停"); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width=getWidth(), height=getHeight();
        float density=getResources().getDisplayMetrics().density;
        float stroke=barWidth*density, between=gap*density;
        float natural=bars*stroke+(bars-1)*between;
        float scale=Math.min(1f,width/Math.max(1f,natural));
        stroke*=scale;between*=scale;
        float start=(width-(bars*stroke+(bars-1)*between))*.5f;
        paint.setStrokeWidth(Math.max(1f,stroke));
        float phase=SystemClock.uptimeMillis()/180f;
        boolean fresh = levels != null && SystemClock.uptimeMillis() - lastSample < 1200L;
        for (int i=0;i<bars;i++) {
            int mapped = arrangement == 1 ? Math.round(Math.abs(i-(bars-1)*.5f)*4f/Math.max(1f,(bars-1)*.5f))
                    : Math.round(i*4f/Math.max(1,bars-1));
            float value = fresh ? Math.min(1f, smooth[mapped]*gain/100f)
                    : (float)(.5+.5*Math.sin(phase+mapped*1.37));
            float amplitude = !playing ? .22f : Math.min(.90f, .10f + .80f * value);
            if (hasAccent) {
                hsv[0] = (accentHsv[0] + 360f + (value-.5f)*14f + i*2f) % 360f;
                hsv[1] = accentHsv[1]; hsv[2] = Math.max(.6f, Math.min(1f, accentHsv[2] * (.82f + .18f*value)));
            } else { hsv[0] = 165f + 85f * value + i * 5f; hsv[1]=.64f; hsv[2]=1f; }
            paint.setColor(Color.HSVToColor(hsv));
            float half=height*amplitude*.5f, x=start+stroke*.5f+i*(stroke+between);
            canvas.drawLine(x,height*.5f-half,x,height*.5f+half,paint);
        }
        if (playing && isShown()) postInvalidateOnAnimation();
    }
}
