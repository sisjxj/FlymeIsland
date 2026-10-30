"""Run production selected-session media commands with JVM fakes."""
from pathlib import Path
import os,shutil,subprocess
R=Path(__file__).resolve().parents[1];w=R/'build/media-controls-test';w.mkdir(parents=True,exist_ok=True)
s=(R/'app/src/main/java/io/github/flymeisland/diagnostic/MusicListenerService.java').read_text(encoding='utf8')
method=s[s.index('    private void controlSelected('):s.index('    private void registerOpenedDismissReceiver(')]
body=r"""
import java.util.*;
public class MediaControlCheck {
 static String TAG="t";static class Log {static int w(String a,String b){return 0;}}
 static class PlaybackState {static final int STATE_PLAYING=3;static final long ACTION_SKIP_TO_PREVIOUS=1,ACTION_SKIP_TO_NEXT=2,ACTION_PAUSE=4,ACTION_PLAY=8,ACTION_PLAY_PAUSE=16;int state;long actions;List<CustomAction> custom=new ArrayList<>();long getActions(){return actions;}int getState(){return state;}List<CustomAction> getCustomActions(){return custom;}static class CustomAction {String action;CustomAction(String a){action=a;}String getAction(){return action;}Object getExtras(){return null;}}}
 static class MediaController {PlaybackState state=new PlaybackState();int prev,next,pause,play,custom;PlaybackState getPlaybackState(){return state;}TransportControls getTransportControls(){return new TransportControls();}class TransportControls {void skipToPrevious(){prev++;}void skipToNext(){next++;}void pause(){pause++;}void play(){play++;}void sendCustomAction(PlaybackState.CustomAction a,Object e){custom++;}}}
 MediaController selected;boolean isPlaying(MediaController c){return c.state.state==3;}
 //METHOD
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] a){MediaControlCheck c=new MediaControlCheck();c.selected=new MediaController();c.selected.state.actions=1|2|4|8;c.selected.state.state=3;
 c.controlSelected("previous");c.controlSelected("next");c.controlSelected("toggle");check(c.selected.prev==1&&c.selected.next==1&&c.selected.pause==1);
 c.selected.state.state=0;c.controlSelected("toggle");check(c.selected.play==1);c.selected.state.actions=0;c.controlSelected("next");check(c.selected.next==1);
 c.selected.state.custom.add(new PlaybackState.CustomAction("like"));c.controlSelected("custom:bad");check(c.selected.custom==0);c.controlSelected("custom:like");check(c.selected.custom==1);
 c.controlSelected(null);c.selected=null;c.controlSelected("next");System.out.println("PASS: transport actions, playback state, supported flags and allowed custom actions");}}
""".replace('//METHOD',method)
(w/'MediaControlCheck.java').write_text(body,encoding='utf8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(n):
 p=jdk/(n+('.exe' if os.name=='nt' else ''));return str(p) if p.is_file() else shutil.which(n)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(w.relative_to(R)),str((w/'MediaControlCheck.java').relative_to(R))],cwd=R,check=True)
subprocess.run([tool('java'),'-cp',str(w.relative_to(R)),'MediaControlCheck'],cwd=R,check=True)
