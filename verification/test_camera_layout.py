"""Run the local-camera layout against a small JVM view/layout harness.

Checks layout/viewport invariants, including a delayed WM resize. This is NOT an
Android renderer, SystemUI test, frame-rate measurement or real-device proof.
"""
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/viewport-test'
SRC = WORK / 'src'
JAVA = Path(os.environ.get('JAVA_HOME', r'C:/Program Files/Android/Android Studio/jbr')) / 'bin'
def java_tool(name):
    executable = name + ('.exe' if os.name == 'nt' else '')
    candidate = JAVA / executable
    return str(candidate) if candidate.is_file() else shutil.which(name)
FILES = {
    'android/animation/Animator.java': '''package android.animation; public class Animator {}''',
    'android/animation/AnimatorListenerAdapter.java': '''package android.animation; public class AnimatorListenerAdapter {public void onAnimationEnd(Animator a){}}''',
    'android/animation/ValueAnimator.java': '''package android.animation; public class ValueAnimator extends Animator {
public static final int INFINITE=-1,REVERSE=2;public void setRepeatCount(int n){}public void setRepeatMode(int n){}public interface Update {void update(ValueAnimator a);} private Update update;private AnimatorListenerAdapter listener;private float value;
public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.value=b;return v;}
public interface Interpolator {float map(float t);} public void setInterpolator(Interpolator i){}public void setDuration(long v){}public void addUpdateListener(Update a){update=a;}public void addListener(AnimatorListenerAdapter l){listener=l;}
public void removeAllListeners(){listener=null;}public void removeAllUpdateListeners(){update=null;}public void cancel(){}
public Object getAnimatedValue(){return value;}public void start(){if(update!=null)update.update(this);if(listener!=null)listener.onAnimationEnd(this);}}''',
    'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable; public class Drawable {
public Drawable mutate(){return this;}public void setAlpha(int a){}public void setBounds(int a,int b,int c,int d){}public void draw(android.graphics.Canvas c){}}''',
    'android/content/Context.java': '''package android.content;
public class Context { public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
    'android/content/res/Resources.java': '''package android.content.res;
public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
    'android/util/DisplayMetrics.java': '''package android.util; public class DisplayMetrics {public float density=1;}''',
    'android/graphics/Color.java': '''package android.graphics; public class Color {public static final int BLACK=0xff000000;}''',
    'android/graphics/Paint.java': '''package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1;public enum Style{STROKE,FILL} public Paint(int f){} public void setColor(int c){}public void setStyle(Style s){}public void setAlpha(int a){}public void setStrokeWidth(float w){}}''',
    'android/graphics/RectF.java': '''package android.graphics;
public class RectF {public float left,top,right,bottom;
public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
public void setEmpty(){set(0,0,0,0);}public boolean isEmpty(){return right<=left||bottom<=top;}public float width(){return right-left;}
public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}}''',
    'android/graphics/Path.java': '''package android.graphics; public class Path {
public final RectF rect=new RectF();
public enum Direction {CW} public void rewind(){} public void addRoundRect(RectF r,float x,float y,Direction d){rect.set(r.left,r.top,r.right,r.bottom);}
public void addOval(RectF r,Direction d){rect.set(r.left,r.top,r.right,r.bottom);} public void moveTo(float x,float y){}public void cubicTo(float a,float b,float c,float d,float e,float f){}public void lineTo(float x,float y){}public void close(){} }''',
    'android/graphics/Region.java': '''package android.graphics; public class Region {
public final RectF rect=new RectF();public Region(){}public Region(Region other){set(other);}
public void set(Region other){rect.set(other.rect.left,other.rect.top,other.rect.right,other.rect.bottom);}
public void set(int l,int t,int r,int b){rect.set(l,t,r,b);}public void setEmpty(){set(0,0,0,0);}
public boolean setPath(Path p,Region clip){rect.set(Math.max(p.rect.left,clip.rect.left),Math.max(p.rect.top,clip.rect.top),Math.min(p.rect.right,clip.rect.right),Math.min(p.rect.bottom,clip.rect.bottom));return true;}
public enum Op {UNION} public boolean op(Region r,Op o){rect.set(Math.min(rect.left,r.rect.left),Math.min(rect.top,r.rect.top),Math.max(rect.right,r.rect.right),Math.max(rect.bottom,r.rect.bottom));return true;}
public boolean equals(Object obj){if(!(obj instanceof Region))return false;RectF r=((Region)obj).rect;return rect.left==r.left&&rect.top==r.top&&rect.right==r.right&&rect.bottom==r.bottom;}
public int hashCode(){return 1;}}''',
    'android/view/AttachedSurfaceControl.java': '''package android.view; public class AttachedSurfaceControl {
public android.graphics.Region region=new android.graphics.Region();public void setTouchableRegion(android.graphics.Region r){region=new android.graphics.Region(r);}}''',
    'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {
public final RectF drawn=new RectF(); public float radius;
public void drawRoundRect(RectF r,float rx,float ry,Paint p){drawn.set(r.left,r.top,r.right,r.bottom);radius=rx;}
public void drawOval(RectF r,Paint p){}public void drawPath(Path x,Paint p){}
public int save(){return 1;} public void clipPath(Path p){} public void restoreToCount(int s){} }''',
    'android/view/ViewTreeObserver.java': '''package android.view; import java.util.*;
public class ViewTreeObserver {public interface OnPreDrawListener{boolean onPreDraw();}
public final List<OnPreDrawListener> listeners=new ArrayList<>();
public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}
public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
public void preDraw(){for(var l:new ArrayList<>(listeners))l.onPreDraw();}}''',
    'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
private int action;private float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
public int getActionMasked(){return action;} public float getRawX(){return x;} public float getRawY(){return y;}
public float getX(){return x;} public float getY(){return y;} }''',
    'android/view/ViewConfiguration.java': '''package android.view; public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 8;}public static int getLongPressTimeout(){return 500;}}''',
    'android/view/ViewPropertyAnimator.java': '''package android.view; public class ViewPropertyAnimator {
public void cancel(){} public ViewPropertyAnimator translationX(float x){return this;}
public ViewPropertyAnimator translationY(float x){return this;} public ViewPropertyAnimator alpha(float a){return this;}
public ViewPropertyAnimator setDuration(long t){return this;} public void start(){} }''',
    'android/view/View.java': '''package android.view; public class View {
private int mw,mh,w,h; private float tx,ty,alpha=1;
public static final int VISIBLE=0,INVISIBLE=4;private int visibility=0;
public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;onVisibilityChanged(this,v);}
protected void onVisibilityChanged(View changed,int v){}protected void onAttachedToWindow(){}
private Runnable delayed;public void post(Runnable r){r.run();}public boolean postDelayed(Runnable r,long delay){delayed=r;return true;}public boolean removeCallbacks(Runnable r){if(delayed==r)delayed=null;return true;}public void fireDelayed(){Runnable r=delayed;delayed=null;if(r!=null)r.run();}public android.content.Context getContext(){return context;}protected void onDetachedFromWindow(){}private final AttachedSurfaceControl surface=new AttachedSurfaceControl();
public AttachedSurfaceControl getRootSurfaceControl(){return surface;}
public int windowY; public int layoutRequests; public final android.content.Context context;
public View(android.content.Context c){context=c;}
public void setClickable(boolean b){} public void setWillNotDraw(boolean b){}
public void invalidate(){} public void requestLayout(){layoutRequests++;}
public android.content.res.Resources getResources(){return context.getResources();}
public void getLocationOnScreen(int[] a){a[0]=0;a[1]=windowY+Math.round(ty);}
public static class MeasureSpec {public static final int EXACTLY=0x40000000;
public static int getSize(int n){return n&0x3fffffff;} public static int makeMeasureSpec(int n,int m){return n|m;}}
protected void setMeasuredDimension(int w,int h){mw=w;mh=h;}
protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}
public void measure(int w,int h){onMeasure(w,h);} public int getMeasuredWidth(){return mw;} public int getMeasuredHeight(){return mh;}
public void layout(int l,int t,int r,int b){w=r-l;h=b-t;onLayout(true,l,t,r,b);}
protected void onLayout(boolean c,int l,int t,int r,int b){}
public int getWidth(){return w;} public int getHeight(){return h;}
public void setTranslationX(float f){tx=f;} public void setTranslationY(float f){ty=f;}
public float getTranslationX(){return tx;} public float getTranslationY(){return ty;}
public void setAlpha(float a){alpha=a;} public float getAlpha(){return alpha;}
public float getScaleX(){return 1;} public float getScaleY(){return 1;}
public ViewPropertyAnimator animate(){return new ViewPropertyAnimator();} public boolean performClick(){return true;}
public boolean dispatchTouchEvent(MotionEvent e){return true;} public boolean onInterceptTouchEvent(MotionEvent e){return false;}
public boolean onTouchEvent(MotionEvent e){return true;}
private final ViewTreeObserver observer=new ViewTreeObserver(); public ViewTreeObserver getViewTreeObserver(){return observer;}
}''',
    'android/widget/FrameLayout.java': '''package android.widget; import android.view.*; import java.util.*;
public class FrameLayout extends View {private final List<View> children=new ArrayList<>();
public FrameLayout(android.content.Context c){super(c);} public void setClipChildren(boolean b){} public void setClipToPadding(boolean b){}
public void addView(View v){children.add(v);} public int getChildCount(){return children.size();}
public View getChildAt(int i){return children.get(i);}
protected void onDraw(android.graphics.Canvas c){} protected void dispatchDraw(android.graphics.Canvas c){} }''',
    'io/github/flymeisland/diagnostic/SystemUiIslandPrefs.java': '''package io.github.flymeisland.diagnostic;
public class SystemUiIslandPrefs {public static final int SWIPE_OFF=0,SWIPE_LEFT=1,SWIPE_RIGHT=2,SWIPE_BOTH=3;
public static int clamp(int n,int l,int h){return Math.max(l,Math.min(h,n));}}''',
    'io/github/flymeisland/diagnostic/CameraLayoutTest.java': '''package io.github.flymeisland.diagnostic;
import android.content.Context; import android.graphics.Canvas; import android.view.View;
public class CameraLayoutTest {
static int checks; static void eq(float actual,float expected){checks++;if(Math.abs(actual-expected)>.001f)
throw new AssertionError(actual+" != "+expected);}
static void layout(CameraIslandLayout v,int w,int h,int y){v.windowY=y;
v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
v.layout(0,0,w,h);v.getViewTreeObserver().preDraw();}
static void frame(CameraIslandLayout v,float w,float h,float y,float r){v.setFrame(w,h,y,r);
v.getViewTreeObserver().preDraw();Canvas c=new Canvas();v.onDraw(c);
eq(c.drawn.right-c.drawn.left,w);eq(c.drawn.bottom-c.drawn.top,h);eq(c.drawn.top,y);eq((c.drawn.left+c.drawn.right)/2,v.getWidth()/2f);
eq(c.radius,Math.min(r,Math.min(w,h)/2));
eq(v.getChildAt(0).getMeasuredWidth(),420);eq(v.getChildAt(0).getMeasuredHeight(),220);
eq(v.getChildAt(1).getMeasuredWidth(),148);eq(v.getChildAt(1).getMeasuredHeight(),29);
eq(v.getScaleX(),1);eq(v.getScaleY(),1);eq(v.getChildAt(0).getScaleX(),1);eq(v.getChildAt(0).getScaleY(),1);
eq(v.getChildAt(0).getTranslationY(),y);
eq(v.getChildAt(1).getTranslationY()+29/2f,y+h/2f);}
public static void main(String[] args){Context c=new Context();CameraIslandLayout v=new CameraIslandLayout(c);
v.addView(new View(c));v.addView(new View(c));v.setContentSizes(420,220,148,29);
layout(v,148,29,0);frame(v,148,29,0,16);
// Before and after asynchronous envelope allocation: identical screen-space frame.
layout(v,420,228,0);frame(v,148,29,0,16);int requests=v.layoutRequests;
for(int i=0;i<=120;i++){float t=i/120f;frame(v,148+272*t,29+191*t,8*t,16+11*t);}
eq(v.layoutRequests,requests);
// Reverse at a mid-frame without any normalization to either endpoint.
frame(v,284,124.5f,4,21.5f);layout(v,420,228,0);frame(v,284,124.5f,4,21.5f);
for(int i=0;i<=120;i++){float t=i/120f;frame(v,284-136*t,124.5f-95.5f*t,4-4*t,21.5f-5.5f*t);}
// Delayed compact settle must preserve both badge size and screen top.
layout(v,148,29,0);frame(v,148,29,0,16);
layout(v,420,228,808);frame(v,420,220,8,27);
layout(v,148,29,0);frame(v,148,29,0,16);
// Repeated same sizing never requests a new measure during animation frames.
requests=v.layoutRequests;v.setContentSizes(420,220,148,29);eq(v.layoutRequests,requests);
eq(v.isInputRegionReady()?1:0,1);int[] ready={0};v.whenInputReady(()->ready[0]++);eq(ready[0],1);
eq(v.getRootSurfaceControl().region.rect.right-v.getRootSurfaceControl().region.rect.left,148);
v.setVisibility(View.INVISIBLE);eq(v.getRootSurfaceControl().region.rect.right,0);
v.setVisibility(View.VISIBLE);eq(v.getRootSurfaceControl().region.rect.right-v.getRootSurfaceControl().region.rect.left,148);
layout(v,720,1600,0);frame(v,148,29,0,16);int[] clicks={0};
v.setSide("qq",new android.graphics.drawable.Drawable(),()->clicks[0]++);
eq(v.getRootSurfaceControl().region.rect.right,468);
v.dispatchTouchEvent(new android.view.MotionEvent(android.view.MotionEvent.ACTION_DOWN,450,14));
v.dispatchTouchEvent(new android.view.MotionEvent(android.view.MotionEvent.ACTION_UP,450,14));eq(clicks[0],1);
int[] next={0},previous={0},longs={0},dismisses={0};String[] identity={"main|player"};
v.setGestureCallback(new IslandGestureLayout.Callback(){
 public boolean isExpanded(){return false;}public void retract(){}public void dismiss(){dismisses[0]++;}
 public String mediaGestureKey(){return identity[0];}
 public void mediaSwipe(String key,boolean n){if(n)next[0]++;else previous[0]++;}
 public void mediaLongPress(String key){longs[0]++;}
});
v.onTouchEvent(new android.view.MotionEvent(0,360,14));v.onTouchEvent(new android.view.MotionEvent(2,310,14));v.onTouchEvent(new android.view.MotionEvent(1,310,14));eq(next[0],1);eq(dismisses[0],0);
v.onTouchEvent(new android.view.MotionEvent(0,360,14));v.onTouchEvent(new android.view.MotionEvent(2,410,14));v.onTouchEvent(new android.view.MotionEvent(1,410,14));eq(previous[0],1);
v.onTouchEvent(new android.view.MotionEvent(0,360,14));v.fireDelayed();v.onTouchEvent(new android.view.MotionEvent(1,360,14));eq(longs[0],1);
v.onTouchEvent(new android.view.MotionEvent(0,360,14));v.onTouchEvent(new android.view.MotionEvent(2,345,14));v.fireDelayed();v.onTouchEvent(new android.view.MotionEvent(1,345,14));eq(longs[0],1);eq(next[0],1);
v.onTouchEvent(new android.view.MotionEvent(0,360,14));identity[0]="main|other";v.fireDelayed();v.onTouchEvent(new android.view.MotionEvent(1,360,14));eq(longs[0],1);
identity[0]=null;v.onTouchEvent(new android.view.MotionEvent(0,360,14));v.onTouchEvent(new android.view.MotionEvent(2,560,14));v.onTouchEvent(new android.view.MotionEvent(1,560,14));eq(dismisses[0],1);
int[] sideNext={0},sideLong={0};String[] sid={"qq|player"};
v.setSideGestureHandler(new CameraIslandLayout.SideGestureHandler(){
 public boolean enabled(String key){return true;}public String identity(String key){return sid[0];}
 public void swipe(String key,boolean n){if(n)sideNext[0]++;}public void longPress(String key){sideLong[0]++;}
});
v.dispatchTouchEvent(new android.view.MotionEvent(0,450,14));v.dispatchTouchEvent(new android.view.MotionEvent(2,425,14));v.dispatchTouchEvent(new android.view.MotionEvent(1,425,14));eq(sideNext[0],1);
v.dispatchTouchEvent(new android.view.MotionEvent(0,450,14));v.fireDelayed();v.dispatchTouchEvent(new android.view.MotionEvent(1,450,14));eq(sideLong[0],1);eq(clicks[0],1);
v.dispatchTouchEvent(new android.view.MotionEvent(0,450,14));sid[0]="qq|other";v.fireDelayed();v.dispatchTouchEvent(new android.view.MotionEvent(1,450,14));eq(sideLong[0],1);eq(clicks[0],1);
v.setSide(null,null,null);eq(v.getRootSurfaceControl().region.rect.right,434);
v.setSideGestureHandler(null);
v.setSide("notice",new android.graphics.drawable.Drawable(),()->{v.deferSideBirth();v.setSide("music",new android.graphics.drawable.Drawable(),()->{});});
v.dispatchTouchEvent(new android.view.MotionEvent(0,450,14));v.dispatchTouchEvent(new android.view.MotionEvent(1,450,14));
eq(v.getRootSurfaceControl().region.rect.right,434);frame(v,420,220,8,27);
v.setSide("notice",new android.graphics.drawable.Drawable(),()->{});frame(v,148,29,0,16);eq(v.getRootSurfaceControl().region.rect.right,434);
v.releaseDeferredSide();eq(v.getRootSurfaceControl().region.rect.right,468);v.setSide(null,null,null);
v.deferSideBirth();v.setSide("music",new android.graphics.drawable.Drawable(),()->{});eq(v.getRootSurfaceControl().region.rect.right,434);
frame(v,420,220,8,27);v.setSide("notification",new android.graphics.drawable.Drawable(),()->{});v.releaseDeferredSide();
frame(v,148,29,0,16);eq(v.getRootSurfaceControl().region.rect.right,434);v.releaseDeferredSide();eq(v.getRootSurfaceControl().region.rect.right,468);
System.out.println("PASS: "+checks+" camera-local viewport invariants (JVM layout harness; not Android runtime)");}
}'''
}

for name, content in FILES.items():
    file = SRC / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(content, encoding='utf-8')
package = Path('io/github/flymeisland/diagnostic')
for name in ['IslandGestureLayout.java', 'CameraIslandLayout.java', 'SideBubbleGeometry.java']:
    (SRC / package / name).write_bytes((ROOT / 'app/src/main/java' / package / name).read_bytes())
classes = WORK / 'classes'
classes.mkdir(parents=True, exist_ok=True)
sources = WORK / 'sources.txt'
sources.write_text('\n'.join('"'+p.relative_to(ROOT).as_posix()+'"' for p in SRC.rglob('*.java')), encoding='utf-8')
subprocess.run([java_tool('javac'), '-encoding', 'UTF-8', '-d', str(classes.relative_to(ROOT)),
                '@'+str(sources.relative_to(ROOT))], cwd=ROOT, check=True)
subprocess.run([java_tool('java'), '-cp', str(classes.relative_to(ROOT)),
                'io.github.flymeisland.diagnostic.CameraLayoutTest'], cwd=ROOT, check=True)
