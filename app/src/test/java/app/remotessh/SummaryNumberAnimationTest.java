package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SummaryNumberAnimationTest {
  private ActivityController<Activity> owner;
  private Activity activity;
  private DialogUi ui;
  private FrameLayout root;
  private ScrollView scroll;
  private LinearLayout messages;
  private ToolActivityGroupView card;
  private TextView header;

  @Before public void open() throws Exception {
    owner=Robolectric.buildActivity(Activity.class).setup();activity=owner.get();UiText.initialize(activity);
    ui=new DialogUi(activity,Color.BLACK,Color.WHITE,0xffaaaaaa,0xff303030,0xff444444);
    root=new FrameLayout(activity);scroll=new ScrollView(activity);messages=ui.column();
    root.addView(scroll,new FrameLayout.LayoutParams(-1,ui.dp(80)));
    scroll.addView(messages,new ScrollView.LayoutParams(-1,-2));activity.setContentView(root);
    ChatItem summary=new ChatItem("files-turn","fileSummary","已更改36个文件");summary.detail="+648 −648";
    card=new ToolActivityGroupView(activity,ui,summary,new HashSet<>(),item->{});
    header=(TextView)card.getChildAt(0);messages.addView(card,new LinearLayout.LayoutParams(-1,-2));
    messages.addView(new View(activity),new LinearLayout.LayoutParams(-1,ui.dp(800)));
    layout();
    Field numbers=ToolActivityGroupView.class.getDeclaredField("numbers");numbers.setAccessible(true);
    Field counters=RollingChangeNumbers.class.getDeclaredField("counters");counters.setAccessible(true);
    for(Object counter:((Map<?,?>)counters.get(numbers.get(card))).values()) {
      Field animator=counter.getClass().getDeclaredField("animator");animator.setAccessible(true);
      ValueAnimator value=(ValueAnimator)animator.get(counter);assertNotNull(value);value.setCurrentPlayTime(100);
    }
    assertTrue("the first pulse must still animate",card.countsAnimating());
  }

  @After public void close(){owner.pause().stop().destroy();}

  private void layout() {
    root.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(320),View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(ui.dp(640),View.MeasureSpec.EXACTLY));
    root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
  }

  private Bitmap frame() {
    Bitmap image=Bitmap.createBitmap(header.getWidth(),header.getHeight(),Bitmap.Config.ARGB_8888);
    header.draw(new Canvas(image));return image;
  }

  private void assertSettled() {
    assertFalse("the completed card must stop animating",card.countsAnimating());
    Spanned text=(Spanned)header.getText();int start=text.toString().indexOf("+648");
    assertTrue(start>=0);int end=start+"+648 −648".length();
    assertEquals("completed counts must use static text, without a cached rolling frame",0,
        text.getSpans(start,end,ReplacementSpan.class).length);
    assertEquals("addition and removal colors remain",2,text.getSpans(start,end,ForegroundColorSpan.class).length);
    assertEquals("the disclosure icon remains",1,text.getSpans(end,text.length(),ReplacementSpan.class).length);
  }

  @Test public void finishingOutsideViewportInvalidatesTheLastVisibleFrame() throws Exception {
    assertTrue(header.getGlobalVisibleRect(new Rect()));
    scroll.scrollTo(0,card.getHeight()+ui.dp(10));
    assertFalse("the summary is clipped while the conversation continues",header.getGlobalVisibleRect(new Rect()));
    Bitmap middle=frame();shadowOf(header).clearWasInvalidated();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
    assertTrue("the terminal frame must invalidate cached digits even while clipped",shadowOf(header).wasInvalidated());
    assertSettled();
    scroll.scrollTo(0,0);layout();
    Bitmap settled=frame();
    File directory=new File("build/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());
    try(FileOutputStream out=new FileOutputStream(new File(directory,"summary-number-settled-api"+android.os.Build.VERSION.SDK_INT+".png"))){
      assertTrue(settled.compress(Bitmap.CompressFormat.PNG,100,out));
    }
    assertFalse("the completed drawing must differ from the mid-roll frame",middle.sameAs(settled));
    middle.recycle();settled.recycle();
  }

  @Test public void detachingDuringTheFirstPulseRestoresStaticDigitsBeforeReattach() {
    View details=card.getChildAt(1);messages.removeView(card);
    assertSettled();
    messages.addView(card,0,new LinearLayout.LayoutParams(-1,-2));layout();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
    assertSettled();assertSame("settling must not rebuild file details",details,card.getChildAt(1));
  }

  @Test public void finalCardSettlesWithoutAnotherConversationRender() {
    View details=card.getChildAt(1);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
    assertSettled();assertSame("the terminal frame only updates number text",details,card.getChildAt(1));
  }
}
