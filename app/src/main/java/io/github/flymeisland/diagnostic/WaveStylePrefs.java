package io.github.flymeisland.diagnostic;
import android.content.Context;
import android.content.SharedPreferences;
/** Wave bars, spacing, gain and cover appearance, local configuration only. */
public final class WaveStylePrefs {
    public static final int[] DEFAULTS={5,3,3,16,200,0,1,0,0};
    public static final int[] MIN={3,1,1,8,50,0,0,0,0}, MAX={9,6,8,28,500,1,1,1,1};
    private static final String KEY="wave_style_v040_";
    private WaveStylePrefs() {}
    public static int[] get(Context c) {
        int[] out=new int[DEFAULTS.length];
        for(int i=0;i<out.length;i++) out[i]=SystemUiIslandPrefs.clamp(MusicPrefs.get(c).getInt(KEY+i,DEFAULTS[i]),MIN[i],MAX[i]);
        return out;
    }
    public static void save(Context c,int[] values) {
        if(values==null||values.length!=DEFAULTS.length) return;
        SharedPreferences.Editor edit=MusicPrefs.get(c).edit();
        for(int i=0;i<values.length;i++) edit.putInt(KEY+i,SystemUiIslandPrefs.clamp(values[i],MIN[i],MAX[i]));
        edit.apply(); SystemUiAicyClient.syncWaveStyle(c);
    }
}
