package io.github.flymeisland.diagnostic;
import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ScrollView;
final class CallSizeDialog {
    static void show(Activity a) {
        int[] values=CallSizePrefs.get(a); String[] labels={"一级宽度","一级高度","二级宽度","二级高度","三级宽度","三级高度"};
        int dp=Math.max(1,Math.round(a.getResources().getDisplayMetrics().density));
        LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(20*dp,8*dp,20*dp,8*dp);
        for(int i=0;i<values.length;i++) { final int index=i; TextView label=new TextView(a); content.addView(label); label.setText(labels[i]+"："+values[i]+" dp");
            SeekBar slider=new SeekBar(a);slider.setMax(CallSizePrefs.MAX[i]-CallSizePrefs.MIN[i]);slider.setProgress(values[i]-CallSizePrefs.MIN[i]);content.addView(slider);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean user){if(user){values[index]=CallSizePrefs.MIN[index]+progress;label.setText(labels[index]+"："+values[index]+" dp");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        }
        ScrollView scroll=new ScrollView(a);scroll.addView(content);
        new AlertDialog.Builder(a).setTitle("电话胶囊三级尺寸").setView(scroll).setPositiveButton("保存",(dialog,which)->CallSizePrefs.save(a,values)).setNegativeButton("取消",null).show();
    }
}
