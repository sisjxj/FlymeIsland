package io.github.flymeisland.diagnostic;
import android.content.Context;
/** User-adjustable offsets relative to the safe two-sided call layout, in dp. */
final class CallLayoutPrefs {
 static final int[] MIN={-20,-12,-20,-12,-20,-12}, MAX={20,12,20,12,20,12};
 static final String[] LABELS={"文字左右", "文字上下", "免提图标左右", "免提图标上下", "挂断图标左右", "挂断图标上下"};
 static int[] get(Context c) { int[] v=new int[6]; for(int i=0;i<6;i++)v[i]=SystemUiIslandPrefs.clamp(MusicPrefs.get(c).getInt("call_layout_v061_"+i,0),MIN[i],MAX[i]);return v; }
 static void save(Context c,int[] v) { if(v==null||v.length!=6)return; android.content.SharedPreferences.Editor e=MusicPrefs.get(c).edit();for(int i=0;i<6;i++)e.putInt("call_layout_v061_"+i,SystemUiIslandPrefs.clamp(v[i],MIN[i],MAX[i]));e.apply();SystemUiAicyClient.syncWaveStyle(c); }
}
