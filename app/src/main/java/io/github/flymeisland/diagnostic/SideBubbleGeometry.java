package io.github.flymeisland.diagnostic;
/** Camera-local droplet geometry: monotone bud growth, bridge pinch and separation. */
public final class SideBubbleGeometry {
    public final float centerX, centerY, radius, neck, bridgeStartX, bridgeEndX;
    private SideBubbleGeometry(float x,float y,float r,float n,float start,float end) {
        centerX=x;centerY=y;radius=r;neck=n;bridgeStartX=start;bridgeEndX=end;
    }
    public static SideBubbleGeometry calculate(float right,float top,float height,float diameter,float gap,float progress) {
        float t=Math.max(0f,Math.min(1f,progress));
        float r=Math.max(0,diameter)*.5f;
        float cx=right-r*.65f+(gap+r*1.65f)*t;
        float radius=r*(.15f+.85f*t);
        float neck=t<.83f ? r*.38f*(1f-t/.83f) : 0f;
        return new SideBubbleGeometry(cx,top+height*.5f,radius,neck,right-r*.25f,cx-radius*.55f);
    }
}
