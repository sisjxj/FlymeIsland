from pathlib import Path
import os,shutil,subprocess
R=Path(__file__).resolve().parents[1];w=R/'build/call-notification-test';w.mkdir(parents=True,exist_ok=True)
p=w/'android/app/Notification.java';p.parent.mkdir(parents=True,exist_ok=True)
p.write_text("""package android.app; public class Notification { public static final String CATEGORY_CALL="call"; public String category; public Action[] actions; public static class Action { public CharSequence title; public PendingIntent actionIntent; public Action(String s,String p){title=s;actionIntent=p==null?null:new PendingIntent(p);} } public static class PendingIntent {String pkg; PendingIntent(String p){pkg=p;} public String getCreatorPackage(){return pkg;} } }""",encoding='utf8')
f=w/'io/github/flymeisland/diagnostic/Check.java';f.parent.mkdir(parents=True,exist_ok=True)
f.write_text("""package io.github.flymeisland.diagnostic; import android.app.Notification;
public class Check { static void c(boolean x){if(!x)throw new AssertionError();} public static void main(String[] z){
 Notification n=new Notification(); n.category="call";c(CallNotification.matches("com.android.incallui",n));c(!CallNotification.matches("other.app",n));
 n.category=null;c(!CallNotification.matches("com.android.incallui",n));
 n.actions=new Notification.Action[]{new Notification.Action("挂断","com.android.incallui")}; c(CallNotification.matches("com.android.incallui",n));
 n.actions=new Notification.Action[]{new Notification.Action("挂断","other.app")};c(!CallNotification.matches("com.android.incallui",n));
 n.actions=new Notification.Action[]{new Notification.Action("打开扬声器","com.android.incallui")};c(!CallNotification.matches("com.android.incallui",n));
 c(!CallNotification.matches("com.android.incallui",null)); System.out.println("PASS incoming and category-free ongoing call, trusted creator, unrelated notifications");}}""",encoding='utf8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(n):
 p=jdk/(n+('.exe' if os.name=='nt' else ''));return str(p) if p.is_file() else shutil.which(n)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(w.relative_to(R)),str(p.relative_to(R)),str(f.relative_to(R)),'app/src/main/java/io/github/flymeisland/diagnostic/CallNotification.java','app/src/main/java/io/github/flymeisland/diagnostic/CallActionPolicy.java'],cwd=R,check=True)
subprocess.run([tool('java'),'-cp',str(w.relative_to(R)),'io.github.flymeisland.diagnostic.Check'],cwd=R,check=True)
