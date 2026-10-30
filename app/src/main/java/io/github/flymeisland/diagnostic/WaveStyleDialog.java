package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.*;

/** Local visual controls, independent from the audio capture permission. */
public final class WaveStyleDialog {
    private WaveStyleDialog() {}
    public static void show(Activity a) {
        int[] v=WaveStylePrefs.get(a);
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
        int dp=Math.max(1,Math.round(a.getResources().getDisplayMetrics().density));box.setPadding(20*dp,0,20*dp,0);
        slider(box,"波形条数量",v,0,"");slider(box,"波形条宽度",v,1," dp");slider(box,"波形条间距",v,2," dp");
        slider(box,"波形区域高度",v,3," dp");slider(box,"波形增益",v,4,"%");
        TextView arrangement=new TextView(a);arrangement.setText("排列方式");box.addView(arrangement);
        Spinner spin=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,new String[]{"顺序排列","中心对称"});adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spin.setAdapter(adapter);spin.setSelection(v[5]);box.addView(spin);
        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View x,int pos,long id){v[5]=pos;}public void onNothingSelected(AdapterView<?> p){}});
        Switch cover=new Switch(a);cover.setText("显示媒体封面（无封面时显示应用图标）");cover.setChecked(v[6]!=0);cover.setOnCheckedChangeListener((b,on)->v[6]=on?1:0);box.addView(cover);
        Switch rotate=new Switch(a);rotate.setText("播放时旋转封面");rotate.setChecked(v[7]!=0);rotate.setOnCheckedChangeListener((b,on)->v[7]=on?1:0);box.addView(rotate);
        Switch palette=new Switch(a);palette.setText("波形颜色取自歌曲封面");palette.setChecked(v[8]!=0);palette.setOnCheckedChangeListener((b,on)->v[8]=on?1:0);box.addView(palette);
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        new AlertDialog.Builder(a).setTitle("音乐波形与封面").setView(scroll)
                .setPositiveButton("保存",(d,n)->WaveStylePrefs.save(a,v))
                .setNeutralButton("恢复默认",(d,n)->WaveStylePrefs.save(a,WaveStylePrefs.DEFAULTS.clone()))
                .setNegativeButton("取消",null).show();
    }
    private static void slider(LinearLayout box,String name,int[] values,int index,String unit) {
        TextView text=new TextView(box.getContext());box.addView(text);SeekBar bar=new SeekBar(box.getContext());
        bar.setMax(WaveStylePrefs.MAX[index]-WaveStylePrefs.MIN[index]);bar.setProgress(values[index]-WaveStylePrefs.MIN[index]);box.addView(bar);
        text.setText(name+"："+values[index]+unit);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user){values[index]=WaveStylePrefs.MIN[index]+p;text.setText(name+"："+values[index]+unit);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    }
}
