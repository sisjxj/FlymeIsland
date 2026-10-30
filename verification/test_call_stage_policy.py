from pathlib import Path
import os, shutil, subprocess
R=Path(__file__).resolve().parents[1]; w=R/'build/call-stage-test';w.mkdir(parents=True,exist_ok=True)
f=w/'io/github/flymeisland/diagnostic/CallStageCheck.java';f.parent.mkdir(parents=True,exist_ok=True)
f.write_text("""package io.github.flymeisland.diagnostic;
public class CallStageCheck {
 public static void main(String[] args) {
  if(CallStagePolicy.next(CallStagePolicy.Stage.INCOMING,CallStagePolicy.Event.SWIPE_UP)!=CallStagePolicy.Stage.COMPACT) throw new AssertionError("ringing swipe");
  if(CallStagePolicy.next(CallStagePolicy.Stage.INCOMING,CallStagePolicy.Event.CONNECTED)!=CallStagePolicy.Stage.CONTROLS) throw new AssertionError("connected");
  if(CallStagePolicy.next(CallStagePolicy.Stage.CONTROLS,CallStagePolicy.Event.SWIPE_UP)!=CallStagePolicy.Stage.COMPACT) throw new AssertionError("call swipe");
  System.out.println("PASS call stages: ringing swipe, connected, call swipe");
 }
}""",encoding='utf8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(n):
 p=jdk/(n+('.exe' if os.name=='nt' else '')); return str(p) if p.is_file() else shutil.which(n)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(w.relative_to(R)),str(f.relative_to(R)),'app/src/main/java/io/github/flymeisland/diagnostic/CallStagePolicy.java'],cwd=R,check=True)
subprocess.run([tool('java'),'-cp',str(w.relative_to(R)),'io.github.flymeisland.diagnostic.CallStageCheck'],cwd=R,check=True)
