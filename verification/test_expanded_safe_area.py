from pathlib import Path
import os, subprocess
R=Path(__file__).resolve().parents[1]; w=R/"build/expanded-safe-area-test"; w.mkdir(parents=True,exist_ok=True)
p=w/"io/github/flymeisland/diagnostic/ExpandedSafeAreaCheck.java"; p.parent.mkdir(parents=True,exist_ok=True)
p.write_text("""package io.github.flymeisland.diagnostic;
public class ExpandedSafeAreaCheck {
 public static void main(String[] args) {
  int count=0;
  for(int limit=180;limit<=480;limit+=3) for(int request=0;request<=80;request++) for(int min:new int[]{152,194}) {
   int got=ExpandedSafeArea.appliedInset(request,limit,min);
   if(got<0 || got>request || got>Math.max(0,limit-min)) throw new AssertionError();
   if(limit>=min+request && got!=request) throw new AssertionError();
   count++;
  }
  if(ExpandedSafeArea.appliedInset(-1,320,152)!=0 || ExpandedSafeArea.appliedInset(28,376,194)!=28 || ExpandedSafeArea.appliedInset(80,180,194)!=0) throw new AssertionError();
  System.out.println("PASS expanded camera inset bounds: "+count+" cases");
 }
}""",encoding="utf-8")
jdk=Path(os.environ.get("JAVA_HOME",r"C:/Program Files/Android/Android Studio/jbr"))/"bin"
subprocess.run([str(jdk/("javac.exe" if os.name=="nt" else "javac")),"-encoding","UTF-8","-d",str(w.relative_to(R)),str(p.relative_to(R)),"app/src/main/java/io/github/flymeisland/diagnostic/ExpandedSafeArea.java"],cwd=R,check=True)
subprocess.run([str(jdk/("java.exe" if os.name=="nt" else "java")),"-cp",str(w.relative_to(R)),"io.github.flymeisland.diagnostic.ExpandedSafeAreaCheck"],cwd=R,check=True)
