package io.github.flymeisland.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.*;

/** Pick an installed application, then edit its compact capsule dimensions. */
public final class AppSizeDialog {
    private AppSizeDialog() {}
    private static final class App { final String pkg,name; App(String p,String n){pkg=p;name=n;} public String toString(){return name+"  ("+pkg+")";} }
    public static void show(Activity activity) {
        PackageManager pm=activity.getPackageManager();
        ArrayList<App> all=new ArrayList<>();
        for(ApplicationInfo info:pm.getInstalledApplications(0)) {
            if(MiniBridgeContract.APP.equals(info.packageName)) continue;
            try{all.add(new App(info.packageName,pm.getApplicationLabel(info).toString()));}catch(RuntimeException ignored){}
        }
        all.sort(Comparator.comparing(a->a.name,String.CASE_INSENSITIVE_ORDER));
        ArrayList<App> visible=new ArrayList<>(all);
        LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);
        int dp=Math.max(1,Math.round(activity.getResources().getDisplayMetrics().density));view.setPadding(16*dp,0,16*dp,0);
        EditText search=new EditText(activity);search.setSingleLine();search.setHint("搜索应用名称或包名");view.addView(search);
        ListView list=new ListView(activity);
        ArrayAdapter<App> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,visible);list.setAdapter(adapter);
        view.addView(list,new LinearLayout.LayoutParams(-1,360*dp));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("选择单独设置尺寸的应用").setView(view).setNegativeButton("关闭",null).create();
        list.setOnItemClickListener((parent,v,index,id)->{App selected=visible.get(index);dialog.dismiss();edit(activity,selected);});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int start,int before,int count){String q=s.toString().trim().toLowerCase(Locale.ROOT);visible.clear();
                for(App app:all)if(app.name.toLowerCase(Locale.ROOT).contains(q)||app.pkg.toLowerCase(Locale.ROOT).contains(q))visible.add(app);adapter.notifyDataSetChanged();}});
        dialog.show();
    }
    public static void showMedia(Activity activity) { edit(activity,new App("island.media","媒体播放")); }
    public static void showCharge(Activity activity) { edit(activity,new App("island.charge","充电")); }
    private static void edit(Activity activity,App app) {
        LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);
        int dp=Math.max(1,Math.round(activity.getResources().getDisplayMetrics().density));view.setPadding(20*dp,0,20*dp,0);
        int[] size={AppSizePrefs.width(activity,app.pkg),AppSizePrefs.height(activity,app.pkg)};
        slider(view,"胶囊宽度",size,0,SystemUiIslandPrefs.PILL_W_MIN,SystemUiIslandPrefs.PILL_W_MAX);
        slider(view,"胶囊高度",size,1,SystemUiIslandPrefs.PILL_H_MIN,SystemUiIslandPrefs.PILL_H_MAX);
        new AlertDialog.Builder(activity).setTitle(app.name+" · 胶囊尺寸").setView(view)
                .setPositiveButton("保存",(d,n)->AppSizePrefs.save(activity,app.pkg,size[0],size[1]))
                .setNeutralButton("island.charge".equals(app.pkg) ? "恢复推荐尺寸" : "恢复全局尺寸",(d,n)->AppSizePrefs.reset(activity,app.pkg))
                .setNegativeButton("取消",null).show();
    }
    private static void slider(LinearLayout box,String title,int[] values,int index,int min,int max) {
        TextView text=new TextView(box.getContext());box.addView(text);SeekBar seek=new SeekBar(box.getContext());seek.setMax(max-min);seek.setProgress(values[index]-min);box.addView(seek);
        text.setText(title+"："+values[index]+" dp");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user){values[index]=min+p;text.setText(title+"："+values[index]+" dp");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    }
}
