package app.remotessh;

import static org.junit.Assert.*;
import android.app.Activity;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;

/** Dispatch through the vertical parent so interception and selectable cells participate. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TableGestureTest {
  private ActivityController<Activity> activity;
  private ConversationScrollView outer;
  private HorizontalScrollView table;
  private void setup(boolean wide) {
    activity=Robolectric.buildActivity(Activity.class).setup().visible();
    outer=new ConversationScrollView(activity.get());outer.setSmoothScrollingEnabled(false);
    LinearLayout content=new LinearLayout(activity.get());content.setOrientation(LinearLayout.VERTICAL);
    content.addView(new View(activity.get()),new LinearLayout.LayoutParams(-1,500));
    MessageBodyView body=new MessageBodyView(activity.get(),0xff171717,0xff626262,0xffeceef1,v->{},v->{});
    String source=wide?TableRenderingTest.WIDE:"| A | B |\n|---|---|\n| 1 | 2 |";
    for(int i=0;i<30;i++)source+=wide?"\n| 参数 | 1200 | 较长的参数说明与适用范围 | 正常 |":"\n| 1 | 2 |";
    body.setSource(source);content.addView(body,new LinearLayout.LayoutParams(-1,-2));
    content.addView(new View(activity.get()),new LinearLayout.LayoutParams(-1,1000));
    outer.addView(content);activity.get().setContentView(outer);
    outer.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
    outer.layout(0,0,360,500);outer.read();outer.scrollTo(0,650);
    table=(HorizontalScrollView)((TableContentView)body.getChildAt(0)).getChildAt(1);
    assertTrue(table.getHeight()>500);
  }
  @After public void close(){if(activity!=null)activity.pause().stop().destroy();}
  private void event(long down,int step,int action,float x,float y) {
    MotionEvent event=MotionEvent.obtain(down,down+step*30,action,x,y,0);
    try{outer.dispatchTouchEvent(event);}finally{event.recycle();}
  }
  private void swipe(float dx,float dy,int endAction) {
    long down=android.os.SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,200);
    for(int i=1;i<=5;i++)event(down,i,MotionEvent.ACTION_MOVE,280+dx*i/5,200+dy*i/5);
    event(down,6,endAction,280+dx,200+dy);
  }
  @Test public void horizontalDragWithVerticalDriftKeepsConversationY() {
    setup(true);int y=outer.getScrollY();
    swipe(-200,-70,MotionEvent.ACTION_UP);
    assertTrue("Table must scroll horizontally: outer="+outer.getScrollY()+" initial="+y+" table="+table.getWidth()+" grid="+table.getChildAt(0).getWidth()+" top="+table.getTop()+" tableY="+table.getScrollY(),table.getScrollX()>0);
    assertEquals("Horizontal reading must not move the conversation",y,outer.getScrollY());
    assertEquals(0,table.getScrollY());
    assertFalse(outer.isFollowing());
  }
  @Test public void horizontalDragInOppositeDirectionKeepsConversationY() {
    setup(true);table.scrollTo(300,0);int x=table.getScrollX(),y=outer.getScrollY();
    swipe(60,20,MotionEvent.ACTION_UP);
    assertTrue(table.getScrollX()<x);assertEquals(y,outer.getScrollY());
  }
  @Test public void straightHorizontalDragDoesNotRevealFocusedCell() {
    setup(true);int y=outer.getScrollY();
    swipe(-200,0,MotionEvent.ACTION_UP);
    assertTrue(table.getScrollX()>0);assertEquals(y,outer.getScrollY());
    // Ending the gesture must restore normal programmatic / accessibility reveal.
    outer.requestChildRectangleOnScreen(outer.getChildAt(0),new android.graphics.Rect(0,0,100,40),true);
    assertTrue(outer.getScrollY()<y);
  }
  @Test public void verticalDragStartingOnTableStillScrollsConversation() {
    setup(true);int y=outer.getScrollY();
    swipe(-5,-160,MotionEvent.ACTION_UP);
    assertTrue("Vertical table gesture must scroll messages",outer.getScrollY()>y);
    assertEquals(0,table.getScrollX());
  }
  @Test public void cancelReleasesParentForNextVerticalGesture() {
    setup(true);int y=outer.getScrollY();
    swipe(-160,-50,MotionEvent.ACTION_CANCEL);
    assertEquals(y,outer.getScrollY());
    swipe(0,-160,MotionEvent.ACTION_UP);
    assertTrue(outer.getScrollY()>y);
  }
  @Test public void removingTableDuringDragRestoresFocusReveal() {
    setup(true);int y=outer.getScrollY();long down=android.os.SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,200);
    event(down,1,MotionEvent.ACTION_MOVE,220,190);
    assertEquals(y,outer.getScrollY());
    ((ViewGroup)table.getParent()).removeView(table);
    outer.requestChildRectangleOnScreen(outer.getChildAt(0),new android.graphics.Rect(0,0,100,40),true);
    assertTrue(outer.getScrollY()<y);
  }
  @Test public void narrowTableDoesNotTrapVerticalGesture() {
    setup(false);int y=outer.getScrollY();
    swipe(-10,-160,MotionEvent.ACTION_UP);
    assertTrue(outer.getScrollY()>y);assertEquals(0,table.getScrollX());
  }
  @Test @Config(sdk=26) public void android8HorizontalDragKeepsConversationY(){horizontalDragWithVerticalDriftKeepsConversationY();}
}
