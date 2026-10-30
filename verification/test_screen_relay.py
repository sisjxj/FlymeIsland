from pathlib import Path
import os, subprocess
R=Path(__file__).resolve().parents[1]; w=R/"build/screen-relay-test"; w.mkdir(parents=True,exist_ok=True)
p=w/"io/github/flymeisland/diagnostic/ScreenRelayCheck.java";p.parent.mkdir(parents=True,exist_ok=True)
p.write_text("""package io.github.flymeisland.diagnostic;
public class ScreenRelayCheck {
 static void t(boolean yes,int uid,int expected,int invalid,String sender,long sent,long age,String action){
  if (ScreenRelayPolicy.accept(uid,expected,invalid,sender,"com.android.systemui",sent,age,action)!=yes) throw new AssertionError();
 }
 public static void main(String[] args){
  String on="android.intent.action.SCREEN_ON", off="android.intent.action.SCREEN_OFF", present="android.intent.action.USER_PRESENT";
  for(String x:new String[]{on,off,present}) t(true,10,10,-1,"com.android.systemui",100,1,x);
  t(true,10,10,-1,null,100,1,on); t(false,11,10,-1,null,100,1,on);
  t(false,-1,-1,-1,null,100,1,on); t(false,10,10,-1,"bad",100,1,on);
  t(false,10,10,-1,null,-1,1,on); t(false,10,10,-1,null,100,-1,on);
  t(false,10,10,-1,null,100,5001,on); t(false,10,10,-1,null,100,1,"bad");
  System.out.println("PASS screen relay: system UID, identity, age, action whitelist");
 }
}""",encoding="utf-8")
jdk=Path(os.environ.get("JAVA_HOME",r"C:/Program Files/Android/Android Studio/jbr"))/"bin"
subprocess.run([str(jdk/("javac.exe" if os.name=="nt" else "javac")),"-encoding","UTF-8","-d",str(w.relative_to(R)),str(p.relative_to(R)),"app/src/main/java/io/github/flymeisland/diagnostic/ScreenRelayPolicy.java"],cwd=R,check=True)
subprocess.run([str(jdk/("java.exe" if os.name=="nt" else "java")),"-cp",str(w.relative_to(R)),"io.github.flymeisland.diagnostic.ScreenRelayCheck"],cwd=R,check=True)
