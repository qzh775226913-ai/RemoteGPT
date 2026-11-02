package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class ConversationScrollFlingTest {
  @Test public void followStopsOldFlingAndNextTouchCanReadHistory(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();ConversationScrollView scroll=new ConversationScrollView(activity);
      View timeline=new View(activity);timeline.setMinimumHeight(5000);
      scroll.addView(timeline,new ScrollView.LayoutParams(-1,-2));activity.setContentView(scroll);
      layout(scroll);scroll.scrollTo(0,1000);assertFalse(scroll.isFollowing());
      scroll.fling(-10000);scroll.follow();scroll.pin();int latest=scroll.getScrollY();assertTrue(latest>1000);
      for(int i=0;i<30;i++){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));scroll.computeScroll();
        assertTrue("stopped inertia must not move away from latest",scroll.nearBottom());
        assertTrue(scroll.isFollowing());
      }
      long time=android.os.SystemClock.uptimeMillis();
      touch(scroll,time,time,MotionEvent.ACTION_DOWN,20,80);
      touch(scroll,time,time+20,MotionEvent.ACTION_MOVE,20,300);
      touch(scroll,time,time+40,MotionEvent.ACTION_UP,20,300);
      assertTrue(scroll.getScrollY()<latest);assertFalse(scroll.isFollowing());
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void growingBottomSpaceDoesNotMoveTheFinishLineOfAnExistingFling(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();ConversationScrollView scroll=new ConversationScrollView(activity);
      View timeline=new View(activity);timeline.setMinimumHeight(5000);
      scroll.addView(timeline,new ScrollView.LayoutParams(-1,-2));activity.setContentView(scroll);layout(scroll);
      scroll.follow();scroll.pin();int oldBottom=scroll.getScrollY();scroll.scrollTo(0,oldBottom-120);
      assertFalse(scroll.isFollowing());scroll.fling(10000);
      scroll.setPadding(0,0,0,240);layout(scroll);
      scroll.scrollTo(0,oldBottom);
      assertTrue("reaching the fling's original bottom must resume following",scroll.isFollowing());
      scroll.computeScroll();assertTrue("the new footer space must also be reached",scroll.nearBottom());
      for(int i=0;i<20;i++){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));scroll.computeScroll();
        assertTrue("old inertia must not pull the view back",scroll.nearBottom());
        assertTrue(scroll.isFollowing());
      }
    }finally{owner.pause().stop().destroy();}
  }

  @Test public void bouncingTowardLatestStaysLatchedUntilANewTouch(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();ConversationScrollView scroll=new ConversationScrollView(activity);
      View timeline=new View(activity);timeline.setMinimumHeight(5000);
      scroll.addView(timeline,new ScrollView.LayoutParams(-1,-2));activity.setContentView(scroll);layout(scroll);
      scroll.follow();scroll.pin();int bottom=scroll.getScrollY();scroll.fling(10000);
      scroll.scrollTo(0,bottom-100);assertTrue(scroll.isFollowing());
      long time=android.os.SystemClock.uptimeMillis();touch(scroll,time,time,MotionEvent.ACTION_DOWN,20,80);
      scroll.scrollTo(0,bottom-200);assertFalse("a new reading gesture must release the latch",scroll.isFollowing());
      touch(scroll,time,time+20,MotionEvent.ACTION_CANCEL,20,80);
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void footerOffsetDuringFlingDoesNotEndScrollingEarly(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();ConversationScrollView scroll=new ConversationScrollView(activity);
      View timeline=new View(activity);timeline.setMinimumHeight(5000);
      scroll.addView(timeline,new ScrollView.LayoutParams(-1,-2));activity.setContentView(scroll);layout(scroll);
      scroll.follow();scroll.pin();int oldBottom=scroll.getScrollY();
      scroll.scrollTo(0,oldBottom-300);assertFalse(scroll.isFollowing());scroll.fling(1200);
      // Expansion reserves more bottom space while a visual offset is corrected.
      scroll.setPadding(0,0,0,400);layout(scroll);
      scroll.preserveVisualOffset(300);scroll.preserveVisualOffset(-300);
      int before=scroll.getScrollY();
      for(int i=0;i<12;i++){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));scroll.computeScroll();
      }
      assertTrue("the in-progress fling should continue after footer layout",scroll.getScrollY()>before+50);
      assertFalse("a layout correction must not force following",scroll.isFollowing());
    }finally{owner.pause().stop().destroy();}
  }

  private static void layout(View view){
    view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));
    view.layout(0,0,600,400);
  }
  private static void touch(View view,long down,long time,int action,float x,float y){
    MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);
    try{view.dispatchTouchEvent(event);}finally{event.recycle();}
  }
}
