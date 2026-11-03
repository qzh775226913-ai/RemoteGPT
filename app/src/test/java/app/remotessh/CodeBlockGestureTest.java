package app.remotessh;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.os.Looper;
import android.text.Selection;
import android.text.Spannable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.time.Duration;
import static org.robolectric.Shadows.shadowOf;

/** Start on real selectable code and dispatch through the conversation parent. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},qualifiers="zh-rCN-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CodeBlockGestureTest {
  private ActivityController<Activity> owner;
  private ConversationScrollView outer;
  private LinearLayout messages;
  private HorizontalScrollView horizontal;
  private TextView code;
  private EditText input;

  private void setup(boolean inline,boolean wide) {
    owner=Robolectric.buildActivity(Activity.class).setup().visible();Activity activity=owner.get();
    UiText.initialize(activity);outer=new ConversationScrollView(activity);outer.setSmoothScrollingEnabled(false);
    messages=new LinearLayout(activity);messages.setOrientation(LinearLayout.VERTICAL);
    messages.addView(new View(activity),new LinearLayout.LayoutParams(-1,500));
    StringBuilder raw=new StringBuilder();
    for(int i=0;i<100;i++)raw.append(wide?"- Adds conversation context usage estimates, manual compaction and detailed information for remote tasks. ":"short ").append(i).append('\n');
    if(inline) {
      ChatItem item=new ChatItem("command","commandExecution","");item.detail=raw.toString();
      ToolInlineDetailView detail=new ToolInlineDetailView(activity,new DialogUi(activity,0xff000000,0xffffffff,0xffaaaaaa,0xff303030,0xff444444),item);
      messages.addView(detail,new LinearLayout.LayoutParams(-1,-2));horizontal=(HorizontalScrollView)detail.getChildAt(0);
    } else {
      MessageBodyView body=new MessageBodyView(activity,0xffffffff,0xffaaaaaa,0xff303030,v->{},v->{});
      body.setSource("```markdown\n"+raw+"```\n");messages.addView(body,new LinearLayout.LayoutParams(-1,-2));
      horizontal=(HorizontalScrollView)((LinearLayout)body.getChildAt(0)).getChildAt(1);
    }
    code=(TextView)horizontal.getChildAt(0);
    messages.addView(new View(activity),new LinearLayout.LayoutParams(-1,1000));outer.addView(messages);
    FrameLayout root=new FrameLayout(activity);root.addView(outer,new FrameLayout.LayoutParams(-1,500));
    input=new EditText(activity);FrameLayout.LayoutParams composer=new FrameLayout.LayoutParams(-1,48);composer.topMargin=500;
    root.addView(input,composer);activity.setContentView(root);
    root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(548,View.MeasureSpec.EXACTLY));
    root.layout(0,0,360,548);layout();outer.read();outer.scrollTo(0,650);input.requestFocus();
    assertTrue("the composer initially owns focus",input.hasFocus());
    assertTrue("a tall code card spans the viewport",horizontal.getHeight()>500);
    assertTrue(code.isTextSelectable());
  }

  private void layout() {
    outer.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
    outer.layout(0,0,360,500);
  }
  @After public void close(){if(owner!=null)owner.pause().stop().destroy();}
  private void event(long down,int step,int action,float x,float y) {
    MotionEvent e=MotionEvent.obtain(down,down+step*30,action,x,y,0);
    try{outer.dispatchTouchEvent(e);}finally{e.recycle();}
  }
  private void swipe(float dx,float dy,int end) {
    long down=SystemClock.uptimeMillis();event(down,0,MotionEvent.ACTION_DOWN,280,200);
    for(int i=1;i<=5;i++)event(down,i,MotionEvent.ACTION_MOVE,280+dx*i/5,200+dy*i/5);
    event(down,6,end,280+dx,200+dy);
  }
  private void horizontalReading(boolean inline) {
    setup(inline,true);int y=outer.getScrollY();swipe(-200,-70,MotionEvent.ACTION_UP);
    assertTrue("code still scrolls horizontally",horizontal.getScrollX()>0);
    assertEquals("horizontal reading must not drag or reveal a different part of the conversation",y,outer.getScrollY());
    assertEquals(0,horizontal.getScrollY());assertFalse(outer.isFollowing());
  }
  @Test public void fencedCodeHorizontalDriftDoesNotMoveConversation(){horizontalReading(false);}
  @Test public void inlineToolCodeHorizontalDriftDoesNotMoveConversation(){horizontalReading(true);}
  @Test public void verticalSwipesStartingOnCodeTrackTheFingerInBothDirections() {
    setup(false,true);int y=outer.getScrollY();swipe(-10,-160,MotionEvent.ACTION_UP);
    assertTrue("upward drag moves toward later content: "+y+" -> "+outer.getScrollY(),outer.getScrollY()>y+100);
    assertTrue("no focus-driven leap",outer.getScrollY()<=y+180);
    int next=outer.getScrollY();swipe(10,160,MotionEvent.ACTION_UP);
    assertTrue("downward drag moves toward older content",outer.getScrollY()<next-100);
    assertTrue("reverse drag remains bounded",outer.getScrollY()>=next-180);
    assertEquals(0,horizontal.getScrollX());assertEquals(0,code.getScrollY());
  }
  @Test public void narrowCodeDoesNotTrapVerticalSwipe() {
    setup(false,false);int y=outer.getScrollY();swipe(0,-160,MotionEvent.ACTION_UP);
    assertTrue(outer.getScrollY()>y+100);assertTrue(outer.getScrollY()<=y+180);
  }
  @Test public void firstTapOnTallCodeKeepsTheVisibleLinesInPlace() {
    setup(false,true);int y=outer.getScrollY();long down=SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,200);event(down,1,MotionEvent.ACTION_UP,280,200);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40));
    assertEquals("focusing selectable code must not jump to another line",y,outer.getScrollY());
  }
  private void tapNearTheTop(boolean inline,boolean wide) {
    setup(inline,wide);outer.scrollTo(0,350);int y=outer.getScrollY();long down=SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,300);event(down,1,MotionEvent.ACTION_UP,280,300);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40));
    assertEquals("focusing the card must not align its top with the viewport",y,outer.getScrollY());
    assertTrue("native selectable text still receives focus",code.hasFocus());
    outer.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY));
    outer.layout(0,0,360,420);
    assertEquals("later viewport resizing must keep the same reading position",y,outer.getScrollY());
    outer.requestChildRectangleOnScreen(messages,new Rect(0,0,100,40),true);
    assertTrue("explicit accessibility or programmatic reveal still works",outer.getScrollY()<y);
  }
  @Test public void tappingNearTheTopOfATallCardDoesNotRevealTheWholeCodeView(){tapNearTheTop(false,true);}
  @Test public void focusingInlineCodeAndResizingKeepsTheVisibleLines(){tapNearTheTop(true,true);}
  @Test public void focusingNarrowCodeAndResizingKeepsTheVisibleLines(){tapNearTheTop(false,false);}
  @Test public void longPressedSelectionKeepsItsNativeDragAndCopy() {
    setup(false,true);int y=outer.getScrollY();long down=SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,200);
    Selection.setSelection((Spannable)code.getText(),0,6);
    MotionEvent held=MotionEvent.obtain(down,down+ViewConfiguration.getLongPressTimeout()+30,MotionEvent.ACTION_MOVE,180,160,0);
    try{outer.dispatchTouchEvent(held);}finally{held.recycle();}
    assertEquals("selection dragging must not pan the conversation",y,outer.getScrollY());
    assertEquals("selection dragging must not become a horizontal code swipe",0,horizontal.getScrollX());
    assertTrue(code.hasSelection());
    assertTrue("native selection retains its copy action",code.onTextContextMenuItem(android.R.id.copy));
    event(down,30,MotionEvent.ACTION_CANCEL,180,160);
  }
  @Test public void verticalDragInterruptsHorizontalInertiaWithoutDriftingSideways() {
    setup(false,true);horizontal.scrollTo(300,0);horizontal.fling(5000);int x=horizontal.getScrollX(),y=outer.getScrollY();
    swipe(-10,-160,MotionEvent.ACTION_UP);
    assertTrue("vertical dragging must leave the code's old horizontal fling",outer.getScrollY()>y+100);
    assertEquals("vertical dragging stops the old horizontal fling",x,horizontal.getScrollX());
  }
  @Test public void cancelledHorizontalSwipeReleasesTheNextVerticalGesture() {
    setup(false,true);int y=outer.getScrollY();swipe(-160,-40,MotionEvent.ACTION_CANCEL);
    assertEquals(y,outer.getScrollY());swipe(0,-160,MotionEvent.ACTION_UP);assertTrue(outer.getScrollY()>y+100);
  }
  @Test public void normalRectangleRevealWorksAfterTouchOrRemoval() {
    setup(false,true);int y=outer.getScrollY();swipe(-160,-30,MotionEvent.ACTION_UP);
    assertEquals(y,outer.getScrollY());
    outer.requestChildRectangleOnScreen(messages,new Rect(0,0,100,40),true);assertTrue(outer.getScrollY()<y);
    outer.scrollTo(0,y);long down=SystemClock.uptimeMillis();
    event(down,0,MotionEvent.ACTION_DOWN,280,200);event(down,1,MotionEvent.ACTION_MOVE,220,190);
    ((ViewGroup)horizontal.getParent()).removeView(horizontal);
    outer.requestChildRectangleOnScreen(messages,new Rect(0,0,100,40),true);assertTrue(outer.getScrollY()<y);
  }
}
