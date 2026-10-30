"""Execute production camera transition/exit methods with fake WM/scheduler.
This tests state and geometry invariants, not Android rendering or performance.
"""
from pathlib import Path
import os, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'app/src/main/java/io/github/flymeisland/diagnostic/SystemUiAicyIsland.java').read_text(encoding='utf-8')
methods=source[source.index('    private static void cancelFrame()'):source.index('    private static void onMainAreaClick()')]
methods=methods[:methods.index('    private static void hideCallMedium()')] + methods[methods.index('    private static void morph(boolean expand)'): ]
methods=methods.replace('SystemUiAicyIsland::', 'CameraFlowTest::')
assert 'getLocationOnScreen' not in methods and 'setScale' not in methods
assert 'updateViewLayout' not in methods
assert all(x not in methods for x in ['params.width =','params.height =','params.y ='])
WORK=ROOT/'build/camera-flow-test';WORK.mkdir(parents=True,exist_ok=True)
body=r'''
import java.util.*;
public class CameraFlowTest {
 static void hideCallMedium() {}
 interface Interpolator {float map(float t);}
 static class Animator {}
 static class AnimatorListenerAdapter {public void onAnimationEnd(Animator a){}}
 static class ValueAnimator extends Animator {
   float value;boolean ended;static boolean instant;Interpolator interpolator=t->t;
   interface Update {void update(ValueAnimator a);}
   List<Update> updates=new ArrayList<>();List<AnimatorListenerAdapter> listeners=new ArrayList<>();
   static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
   void setInterpolator(Interpolator i){interpolator=i;}void setDuration(long t){}
   void addUpdateListener(Update u){updates.add(u);}void addListener(AnimatorListenerAdapter l){listeners.add(l);}
   void removeAllListeners(){listeners.clear();}void removeAllUpdateListeners(){updates.clear();}
   void cancel(){ended=true;for(var l:new ArrayList<>(listeners))l.onAnimationEnd(this);}
   Object getAnimatedValue(){return value;}void start(){tick(0);if(instant)tick(1);}
   void tick(float t){value=interpolator.map(t);for(var u:new ArrayList<>(updates))u.update(this);
     if(t==1&&!ended){ended=true;for(var l:new ArrayList<>(listeners))l.onAnimationEnd(this);}}
 }
 static class ViewTreeObserver {
   interface OnPreDrawListener {boolean onPreDraw();}
   List<OnPreDrawListener> listeners=new ArrayList<>();
   void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
   void draw(){for(var l:new ArrayList<>(listeners))l.onPreDraw();}
 }
 static class View {static final int VISIBLE=0,INVISIBLE=4;float alpha;int visibility;
   void setVisibility(int v){visibility=v;}void setAlpha(float a){alpha=a;}float getAlpha(){return alpha;}
   View animate(){return this;}void cancel(){}void setTranslationX(float x){}void setTranslationY(float y){}void invalidate(){}
 }
 static class CameraIslandLayout extends View {
   float w=148,h=29,y=0;int actualW=720,actualH=1600;ViewTreeObserver observer=new ViewTreeObserver();
   void releaseDeferredSide(){} float visualWidth(){return w;}float visualHeight(){return h;}float visualTop(){return y;}
   void setFrame(float a,float b,float c,float r){w=a;h=b;y=c;}
   void setContentSizes(int a,int b,int c,int d){}int getWidth(){return actualW;}int getHeight(){return actualH;}
   ViewTreeObserver getViewTreeObserver(){return observer;}
 }
 static class WindowManager {
   static class LayoutParams {int width=720,height=1600,y=26;}
   int writes;void updateViewLayout(CameraIslandLayout v,LayoutParams p){writes++;eq(p.y,26);}
 }
 static class Handler {Runnable next;void removeCallbacks(Runnable r){if(next==r)next=null;}void postDelayed(Runnable r,long t){next=r;}}
 static final Handler MAIN=new Handler();static final Runnable EXPAND=()->{},COLLAPSE=()->{},ROTATE=()->{},HIDE=()->{};
 static class Context {Resources getResources(){return new Resources();}}
 static class Resources {Metrics getDisplayMetrics(){return new Metrics();}}
 static class Metrics {float density=1;}
 static class Notice {String key,command="wechat",sourcePackage="wechat",sender="",body="";int pillWidth=148,pillHeight=29;byte[] avatar;Object action;Notice(String k){key=k;}}
 static class MiniBridgeContract {static final String COMMAND_WECHAT="wechat",COMMAND_MUSIC="music";}
 static class MessageCapsulePublisher {static final String WECHAT="wechat",QQ="qq";}
 static final LinkedHashMap<String,Notice> ACTIVE=new LinkedHashMap<>();
 static final Set<String> SUPPRESSED_KEYS=new HashSet<>();
 static final List<String> notified=new ArrayList<>();static int panelHeightLimit=320;
 static String restored;static int restoredWidth,restoredHeight;
 static String selectCandidate(String key,boolean other){return ACTIVE.isEmpty()?null:ACTIVE.keySet().iterator().next();}
 static void notifyOriginalOpened(Context c,String key){notified.add(key);}
 static void show(Context c,String cmd,String sender,String body,byte[] avatar,Object action,String pkg,String key,
   int pw,int ph,int top,int bw,int bh,boolean restore){restored=key;restoredWidth=pw;restoredHeight=ph;root=new CameraIslandLayout();}
 static CameraIslandLayout root;static WindowManager.LayoutParams lp;static WindowManager wm;
 static View fullContent,pillContent;static ValueAnimator running;static ViewTreeObserver.OnPreDrawListener pendingFrame;
 static Runnable pendingExit;static int generation;static float radius=16;static Context context=new Context();
 static boolean isExpanded,aicyShowing,launchInProgress;
 static int bigW=420,bigH=220,bigY=26,smallW=148,smallH=29,smallY=26,scheduled;
 static final long PILL_INTRO_MS=110L;
 static final long MORPH_MS=360L;
 static int dp(int n){return n;}static void log(String s){}static void schedule(boolean e){scheduled++;}
 static boolean isLocked(Object c){return false;}
 static void close(){if(pendingExit!=null){finishPendingExit();return;}cancelFrame();root=null;ACTIVE.clear();}
 static float mix(float a,float b,float t){return a+(b-a)*t;}
 static int checks;static void eq(float a,float b){checks++;if(Math.abs(a-b)>.001)throw new AssertionError(a+" != "+b);}
 static void check(boolean b){checks++;if(!b)throw new AssertionError();}
 static void reset(){root=new CameraIslandLayout();lp=new WindowManager.LayoutParams();wm=new WindowManager();
   fullContent=new View();pillContent=new View();pillContent.alpha=1;radius=16;generation=0;
   running=null;pendingFrame=null;pendingExit=null;isExpanded=false;aicyShowing=false;launchInProgress=false;MAIN.next=null;scheduled=0;ValueAnimator.instant=false;
   ACTIVE.clear();SUPPRESSED_KEYS.clear();notified.clear();restored=null;}
 static void layout(){root.actualW=lp.width;root.actualH=lp.height;root.observer.draw();}
 static void finish(){layout();ValueAnimator a=running;check(a!=null);a.tick(1);}
 // METHODS
 public static void main(String[] args){
   reset();animateCompactArrival();layout();ValueAnimator compactArrival=running;compactArrival.tick(1);eq(root.w,148);check(MAIN.next!=EXPAND);eq(wm.writes,0);
   reset();animateArrival();eq(root.w,29);eq(root.h,29);check(running==null);layout();
   ValueAnimator entry=running;float previous=29;
   for(int i=0;i<=120;i++){entry.tick(i/120f);check(root.w>=previous-.001&&root.w<=148.001);eq(root.h,29);eq(root.y,0);previous=root.w;}
   eq(root.w,148);check(MAIN.next==EXPAND);
   morph(true);check(running==null);root.observer.draw();check(running!=null); // host was already allocated at creation
   layout();ValueAnimator expand=running;previous=148;
   for(int i=0;i<=120;i++){expand.tick(i/120f);check(root.w>=previous-.001&&root.w<=420.001);eq(root.y,0);previous=root.w;}
   eq(root.w,420);eq(root.h,220);eq(fullContent.alpha,1);eq(pillContent.alpha,0);
   morph(false);layout();ValueAnimator collapse=running;collapse.tick(.4f);float w=root.w,h=root.h;
   ValueAnimator.Update stale=collapse.updates.get(0);morph(true);eq(root.w,w);eq(root.h,h);
   int writes=wm.writes;stale.update(collapse);eq(wm.writes,writes);eq(root.w,w);finish();eq(root.w,420);
   int[] exits={0};animateExit(()->exits[0]++);finish(); // card must reach pill before camera contraction
   eq(root.w,148);eq(root.h,29);eq(exits[0],0);check(pendingExit!=null);
   morph(true);check(pendingExit!=null);eq(root.w,148); // taps cannot interrupt an exit
   layout();ValueAnimator shrink=running;previous=148;
   for(int i=0;i<=120;i++){shrink.tick(i/120f);check(root.w<=previous+.001&&root.w>=28.999);eq(root.h,29);eq(root.y,0);previous=root.w;}
   eq(root.w,29);eq(exits[0],1);check(pendingExit==null);
   reset();animateExit(()->exits[0]++);layout();check(running!=null); // compact exits directly, no stationary card stage
   running.tick(1);eq(exits[0],2);
   reset();morph(true);var old=pendingFrame;cancelFrame();root.actualW=420;root.actualH=220;old.onPreDraw();check(running==null);
   reset();animateExit(()->{exits[0]++;close();});close();eq(exits[0],3);check(root==null);
   reset();ValueAnimator.instant=true;animateArrival();layout();eq(root.w,148);
   morph(true);layout();eq(root.w,420);animateExit(()->exits[0]++);layout();layout();eq(exits[0],4);
   reset();morph(true);finish();morph(false);layout();ValueAnimator pendingSize=running;pendingSize.tick(.5f);smallW=190;smallH=40;pendingSize.tick(1);check(pendingFrame!=null);finish();eq(root.w,190);eq(root.h,40);eq(wm.writes,0);smallW=148;smallH=29;
   reset();Notice clicked=new Notice("one"),other=new Notice("two");ACTIVE.put("one",clicked);ACTIVE.put("two",other);
   other.pillWidth=210;other.pillHeight=41;finishOpened(new Context(),"one",true,clicked);eq(restoredWidth,210);eq(restoredHeight,41);check(!ACTIVE.containsKey("one"));check(ACTIVE.get("two")==other);
   check(notified.equals(List.of("one")));check("two".equals(restored));
   reset();clicked=new Notice("one");Notice newer=new Notice("one");ACTIVE.put("one",newer);
   finishOpened(new Context(),"one",true,clicked);check(ACTIVE.get("one")==newer);check(notified.isEmpty());check(!SUPPRESSED_KEYS.contains("one"));
   reset();Notice queued=new Notice("queued");queued.pillWidth=190;queued.pillHeight=36;ACTIVE.put("queued",queued);
   closeOrRestoreQueued();eq(restoredWidth,190);eq(restoredHeight,36);check(ACTIVE.get("queued")==queued);check("queued".equals(restored));
   eq(wm.writes,0);eq(lp.width,720);eq(lp.height,1600);eq(lp.y,26);
   System.out.println("PASS: "+checks+" production camera-flow assertions (JVM harness; not Android runtime)");
 }
}
'''.replace('// METHODS',methods)
file=WORK/'CameraFlowTest.java';file.write_text(body,encoding='utf-8')
jdk=Path(os.environ.get('JAVA_HOME',r'C:/Program Files/Android/Android Studio/jbr'))/'bin'
def tool(name):
    candidate=jdk/(name+('.exe' if os.name=='nt' else ''))
    return str(candidate) if candidate.is_file() else shutil.which(name)
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(WORK.relative_to(ROOT)),str(file.relative_to(ROOT))],cwd=ROOT,check=True)
subprocess.run([tool('java'),'-cp',str(WORK.relative_to(ROOT)),'CameraFlowTest'],cwd=ROOT,check=True)
