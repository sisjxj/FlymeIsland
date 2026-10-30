from pathlib import Path
import os, subprocess
R=Path(__file__).resolve().parents[1]
w=R/"build/unlock-test"; w.mkdir(parents=True,exist_ok=True)
p=w/"io/github/flymeisland/diagnostic/UnlockCheck.java"; p.parent.mkdir(parents=True,exist_ok=True)
p.write_text("""package io.github.flymeisland.diagnostic;
public class UnlockCheck {
 static void check(boolean expected,long cycle,long prev,long now,boolean present,boolean locked,boolean expanded,boolean blocked,boolean moving,boolean charging) {
  if(UnlockReplayPolicy.shouldReplay(cycle,prev,now,present,locked,expanded,blocked,moving,charging)!=expected) throw new AssertionError();
 }
 public static void main(String[] a) {
  check(true,100,99,200,true,false,false,false,false,false);
  check(false,100,100,200,true,false,false,false,false,false);
  check(false,99,100,200,true,false,false,false,false,false);
  check(false,-1,-1,200,true,false,false,false,false,false);
  check(false,201,-1,200,true,false,false,false,false,false);
  check(false,100,-1,10101,true,false,false,false,false,false);
  check(false,100,-1,200,false,false,false,false,false,false);
  check(false,100,-1,200,true,true,false,false,false,false);
  check(false,100,-1,200,true,false,true,false,false,false);
  check(false,100,-1,200,true,false,false,true,false,false);
  check(false,100,-1,200,true,false,false,false,true,false);
  check(false,100,-1,200,true,false,false,false,false,true);
  System.out.println("PASS unlock replay policy: eligible, duplicate, stale, lock, absent, animation, charge");
 }
}""",encoding="utf-8")
jdk=Path(os.environ.get("JAVA_HOME",r"C:/Program Files/Android/Android Studio/jbr"))/"bin"
subprocess.run([str(jdk/("javac.exe" if os.name=="nt" else "javac")),"-encoding","UTF-8","-d",str(w.relative_to(R)),str(p.relative_to(R)),"app/src/main/java/io/github/flymeisland/diagnostic/UnlockReplayPolicy.java"],cwd=R,check=True)
subprocess.run([str(jdk/("java.exe" if os.name=="nt" else "java")),"-cp",str(w.relative_to(R)),"io.github.flymeisland.diagnostic.UnlockCheck"],cwd=R,check=True)
