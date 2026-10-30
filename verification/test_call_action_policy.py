from pathlib import Path
import os,shutil,subprocess
R=Path(__file__).resolve().parents[1]; w=R/'build/call-policy-test'; w.mkdir(parents=True,exist_ok=True)
f=w/'io/github/flymeisland/diagnostic/CallPolicyCheck.java';f.parent.mkdir(parents=True,exist_ok=True)
f.write_text("""package io.github.flymeisland.diagnostic;
public class CallPolicyCheck {
 static void check(String s, CallActionPolicy.Kind k){if(CallActionPolicy.classify(s)!=k)throw new AssertionError();}
 public static void main(String[] args){
  check("接听",CallActionPolicy.Kind.ANSWER);check(" ANSWER ",CallActionPolicy.Kind.ANSWER);
  check("拒接",CallActionPolicy.Kind.REJECT);check("挂断",CallActionPolicy.Kind.HANGUP);check("打开扬声器",CallActionPolicy.Kind.SPEAKER_ON);check("关闭扬声器",CallActionPolicy.Kind.SPEAKER_OFF);
  check("静音",CallActionPolicy.Kind.SILENCE);check(null,CallActionPolicy.Kind.UNKNOWN);
  check("自动接听",CallActionPolicy.Kind.UNKNOWN);check("accept call later",CallActionPolicy.Kind.UNKNOWN);
  check("结束录音",CallActionPolicy.Kind.UNKNOWN);check("",CallActionPolicy.Kind.UNKNOWN);
  System.out.println("PASS explicit action labels and unknown rejection");
 }
}""",encoding='utf8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(n):
 p=jdk/(n+('.exe' if os.name=='nt' else ''));return str(p) if p.is_file() else shutil.which(n)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(w.relative_to(R)),str(f.relative_to(R)),'app/src/main/java/io/github/flymeisland/diagnostic/CallActionPolicy.java'],cwd=R,check=True)
subprocess.run([tool('java'),'-cp',str(w.relative_to(R)),'io.github.flymeisland.diagnostic.CallPolicyCheck'],cwd=R,check=True)
