"""Exercise production routing and its precedence, independently of Android UI."""
from pathlib import Path
import os,shutil,subprocess
R=Path(__file__).resolve().parents[1];w=R/'build/media-routing-test';w.mkdir(parents=True,exist_ok=True)
(w/'MediaRoutingCheck.java').write_text(r"""
import io.github.flymeisland.diagnostic.MediaRouting;
import io.github.flymeisland.diagnostic.MediaRouting.Route;
public class MediaRoutingCheck {
static void eq(Route a,Route b){if(a!=b)throw new AssertionError(a+" != "+b);}
public static void main(String[] args){
 eq(MediaRouting.choose(false,false,true,false,false,false,false,false),Route.NORMAL);
 eq(MediaRouting.choose(true,false,true,true,true,true,true,false),Route.UPDATE_MEDIA);
 eq(MediaRouting.choose(true,false,true,true,true,false,false,true),Route.UPDATE_MEDIA);
 eq(MediaRouting.choose(true,false,true,false,true,false,false,false),Route.PROMOTE_MEDIA);
 eq(MediaRouting.choose(true,false,true,false,true,false,false,true),Route.PROMOTE_MEDIA);
 eq(MediaRouting.choose(true,false,false,false,true,false,true,false),Route.INTERRUPT_WITH_NOTICE);
 eq(MediaRouting.choose(true,false,false,false,true,false,true,true),Route.QUEUE);
 eq(MediaRouting.choose(true,false,false,false,true,false,false,false),Route.QUEUE);
 eq(MediaRouting.choose(true,false,false,false,true,true,true,false),Route.NORMAL);
 System.out.println("PASS: first media, silent updates, notification interruption, restore and queue policy");}}
""",encoding='utf8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(n):
 p=jdk/(n+('.exe' if os.name=='nt' else ''));return str(p) if p.is_file() else shutil.which(n)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(w.relative_to(R)),str((w/'MediaRoutingCheck.java').relative_to(R)),'app/src/main/java/io/github/flymeisland/diagnostic/MediaRouting.java'],cwd=R,check=True)
subprocess.run([tool('java'),'-cp',str(w.relative_to(R)),'MediaRoutingCheck'],cwd=R,check=True)
