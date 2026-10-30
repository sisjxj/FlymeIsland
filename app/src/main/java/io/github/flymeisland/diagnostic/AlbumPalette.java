package io.github.flymeisland.diagnostic;
import android.graphics.Bitmap;
import android.graphics.Color;
/** Derive one bounded, visible color from local artwork; no artwork leaves the device. */
public final class AlbumPalette {
    private AlbumPalette() {}
    public static int extract(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth()<=0 || bitmap.getHeight()<=0) return 0;
        double[] weights=new double[24], hue=new double[24], sat=new double[24], value=new double[24];
        double gray=0;int count=0;float[] hsv=new float[3];
        int sx=Math.max(1,bitmap.getWidth()/16),sy=Math.max(1,bitmap.getHeight()/16);
        for(int y=0;y<bitmap.getHeight();y+=sy) for(int x=0;x<bitmap.getWidth();x+=sx) {
            int color=bitmap.getPixel(x,y);
            if(Color.alpha(color)<128) continue;
            Color.colorToHSV(color,hsv);
            if(hsv[2]<.12f) continue;
            gray+=hsv[2];count++;
            if(hsv[1]<.10f) continue;
            int bin=Math.min(23,(int)(hsv[0]/15f));
            double w=hsv[1]*(.3+.7*hsv[2]);
            weights[bin]+=w;hue[bin]+=w*hsv[0];sat[bin]+=w*hsv[1];value[bin]+=w*hsv[2];
        }
        int best=0;for(int i=1;i<24;i++) if(weights[i]>weights[best]) best=i;
        if(weights[best] > 0) {
            hsv[0]=(float)(hue[best]/weights[best]);
            hsv[1]=(float)Math.max(.35,Math.min(.95,sat[best]/weights[best]));
            hsv[2]=(float)Math.max(.72,Math.min(1,value[best]/weights[best]));
            return Color.HSVToColor(hsv);
        }
        if(count==0) return 0;
        int level=(int)Math.round(255*Math.max(.55,Math.min(1,gray/count)));
        return Color.rgb(level,level,level);
    }
}
