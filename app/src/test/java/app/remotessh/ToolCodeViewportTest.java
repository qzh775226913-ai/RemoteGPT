package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Looper;
import android.text.Selection;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ToolCodeViewportTest {
  private static ToolCodeViewport viewport(Activity activity){
    TextView text=new TextView(activity);text.setTextSize(16);text.setHorizontallyScrolling(true);text.setTextIsSelectable(true);
    StringBuilder source=new StringBuilder();
    for(int i=0;i<90;i++)source.append("line ").append(i).append(" abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").append('\n');
    text.setText(source.toString());ToolCodeViewport viewport=new ToolCodeViewport(activity,text);
    activity.setContentView(viewport);layout(viewport);return viewport;
  }
  private static void layout(View view){
    view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));
    view.layout(0,0,400,300);
  }
  private static void touch(View view,long down,long time,int action,float x,float y){
    MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);
    try{view.dispatchTouchEvent(event);}finally{event.recycle();}
  }
  private static void pointers(View view,long down,long time,int action,float x1,float y1,float x2,float y2){
    MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
    MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
    for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].pressure=1;coords[i].size=1;}
    coords[0].x=x1;coords[0].y=y1;coords[1].x=x2;coords[1].y=y2;
    MotionEvent event=MotionEvent.obtain(down,time,action,2,properties,coords,0,0,1,1,0,0,0,0);
    try{view.dispatchTouchEvent(event);}finally{event.recycle();}
  }

  @Test public void zoomKeepsTheSameCodeUnderTheMovingFingersAndClamps(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      ToolCodeViewport viewport=viewport(owner.get());viewport.scrollTo(200,300);
      viewport.zoom(2,100,80,130,110);
      assertEquals(2,viewport.scaleFactor(),.001f);
      assertEquals(470,viewport.getScrollX());assertEquals(650,viewport.getScrollY());
      viewport.zoom(99,130,110,130,110);assertEquals(4,viewport.scaleFactor(),.001f);
      viewport.zoom(.01f,130,110,130,110);assertEquals(.65f,viewport.scaleFactor(),.001f);
      assertTrue(viewport.getScrollX()>=0);assertTrue(viewport.getScrollY()>=0);
    }finally{owner.pause().stop().destroy();}
  }

  @Test public void pinchAndSingleFingerDragWorkWithoutLosingSelectableText(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      ToolCodeViewport viewport=viewport(owner.get());TextView code=(TextView)viewport.getChildAt(0);
      viewport.scrollTo(200,400);long now=android.os.SystemClock.uptimeMillis();
      touch(viewport,now,now,MotionEvent.ACTION_DOWN,120,150);
      pointers(viewport,now,now+10,MotionEvent.ACTION_POINTER_DOWN | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),120,150,280,150);
      pointers(viewport,now,now+30,MotionEvent.ACTION_MOVE,70,150,330,150);
      pointers(viewport,now,now+50,MotionEvent.ACTION_MOVE,40,150,360,150);
      assertTrue("two-finger gesture enlarges code",viewport.scaleFactor()>1.1f);
      pointers(viewport,now,now+60,MotionEvent.ACTION_POINTER_UP | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),40,150,360,150);
      touch(viewport,now,now+70,MotionEvent.ACTION_UP,40,150);
      int x=viewport.getScrollX(),y=viewport.getScrollY();now+=100;
      touch(viewport,now,now,MotionEvent.ACTION_DOWN,250,220);
      touch(viewport,now,now+30,MotionEvent.ACTION_MOVE,170,120);
      assertTrue("finger dragging pans horizontally: "+x+" -> "+viewport.getScrollX()+", width="+code.getWidth()+", scale="+viewport.scaleFactor(),viewport.getScrollX()>x+50);
      assertTrue("finger dragging pans vertically",viewport.getScrollY()>y+70);
      touch(viewport,now,now+60,MotionEvent.ACTION_UP,170,120);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));viewport.computeScroll();
      assertTrue(viewport.getScrollX()>=0);assertTrue(viewport.getScrollY()>=0);
      assertTrue(code.isTextSelectable());assertTrue(code.getText().toString().contains("line 89"));
      assertEquals("the viewport never rescales text by reflowing it",1,viewport.getChildCount());
    }finally{owner.pause().stop().destroy();}
  }

  @Test public void selectionHandlesAreNotStolenByDragInterception(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      ToolCodeViewport viewport=viewport(owner.get());TextView code=(TextView)viewport.getChildAt(0);
      Selection.setSelection((Spannable)code.getText(),0,4);
      long now=android.os.SystemClock.uptimeMillis();
      MotionEvent move=MotionEvent.obtain(now,now+ViewConfiguration.getLongPressTimeout()+20,MotionEvent.ACTION_MOVE,300,200,0);
      try{assertFalse(viewport.onInterceptTouchEvent(move));}finally{move.recycle();}
    }finally{owner.pause().stop().destroy();}
  }
}
