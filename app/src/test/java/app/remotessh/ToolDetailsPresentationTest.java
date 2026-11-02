package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ToolDetailsPresentationTest {
  private TextView containing(View view,String value){
    if(view instanceof TextView && ((TextView)view).getText().toString().contains(value))return (TextView)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
      TextView found=containing(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
    }
    return null;
  }

  @Test public void inlineCommandShowsCompleteSelectableContentWithoutExtraIndent(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=new DialogUi(activity,Color.BLACK,Color.WHITE,Color.LTGRAY,0xff303030,Color.GRAY);
      ChatItem command=new ChatItem("command","commandExecution","cat output.txt");
      StringBuilder output=new StringBuilder();for(int i=0;i<25;i++)output.append("line ").append(i).append('\n');
      command.detail=output.toString();ToolInlineDetailView inline=new ToolInlineDetailView(activity,ui,command);
      TextView code=containing(inline,"line 24");assertNotNull(code);
      assertTrue("the expanded code includes the command and complete output",code.getText().toString().contains("cat output.txt"));
      assertTrue(code.isTextSelectable());assertTrue("the inline content must not stop at 16 lines",code.getMaxLines()>25);
      assertTrue("the left edge should stay compact",inline.getPaddingLeft()<=ui.dp(8));
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void historicalNoticeUsesFullScreenReadableWrappedText(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=new DialogUi(activity,Color.BLACK,Color.WHITE,Color.LTGRAY,0xff303030,Color.GRAY);
      ChatItem notice=new ChatItem("old","historicalError","历史任务错误");notice.detail="The previous task reached its limit; try again later.";
      AtomicReference<String> copied=new AtomicReference<>();
      ToolDetailDialog.show(activity,ui,false,notice,()->notice,copied::set);
      Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertEquals("details should use the full available height",-1,dialog.getWindow().getAttributes().height);
      TextView body=containing(dialog.getWindow().getDecorView(),notice.detail);assertNotNull(body);
      assertTrue(body.isTextSelectable());assertNotEquals(Typeface.MONOSPACE,body.getTypeface());
      for(android.view.ViewParent parent=body.getParent();parent instanceof View;parent=parent.getParent())
        assertFalse("a notification must wrap as ordinary text",parent instanceof HorizontalScrollView);
      dialog.dismiss();
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void fullScreenCodeKeepsZoomAndCopyWhenTheServerUpdates(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=new DialogUi(activity,Color.BLACK,Color.WHITE,Color.LTGRAY,0xff303030,Color.GRAY);
      ChatItem command=new ChatItem("command","commandExecution","cat output.txt");
      command.detail="first output";AtomicReference<String> copied=new AtomicReference<>();
      ToolDetailDialog.show(activity,ui,false,command,()->command,copied::set);
      Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      View root=dialog.getWindow().getDecorView();ToolCodeViewport viewport=findViewport(root);assertNotNull(viewport);
      viewport.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));
      viewport.layout(0,0,400,300);viewport.zoom(1.7f,0,0,0,0);
      command.detail="updated remote output";
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(180));
      assertEquals(1.7f,viewport.scaleFactor(),.001f);
      TextView code=containing(viewport,"updated remote output");assertNotNull(code);assertTrue(code.isTextSelectable());
      View copy=findByDescription(root,UiText.get(R.string.ui_copy));assertNotNull(copy);copy.performClick();
      assertEquals("cat output.txt\n\nupdated remote output",copied.get());dialog.dismiss();
    }finally{owner.pause().stop().destroy();}
  }

  private ToolCodeViewport findViewport(View view){
    if(view instanceof ToolCodeViewport)return (ToolCodeViewport)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
      ToolCodeViewport found=findViewport(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
    }
    return null;
  }
  private View findByDescription(View view,String description){
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
      View found=findByDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;
    }
    return null;
  }

  @Test public void diffColorsRemainReadableInBothThemes(){
    for(int foreground:new int[]{Color.BLACK,Color.WHITE}){
      CharSequence text=ToolDetailDialog.highlight("+added\n-removed",true,foreground,Color.GRAY);
      android.text.Spanned spans=(android.text.Spanned)text;
      android.text.style.ForegroundColorSpan add=spans.getSpans(0,1,android.text.style.ForegroundColorSpan.class)[0];
      android.text.style.ForegroundColorSpan remove=spans.getSpans(7,8,android.text.style.ForegroundColorSpan.class)[0];
      assertNotEquals(add.getForegroundColor(),remove.getForegroundColor());
      if(foreground==Color.BLACK){assertTrue(Color.green(add.getForegroundColor())<160);assertTrue(Color.red(remove.getForegroundColor())<200);}
      else {assertTrue(Color.green(add.getForegroundColor())>160);assertTrue(Color.red(remove.getForegroundColor())>200);}
    }
  }

  @Test public void fullScreenReasoningKeepsProseAndCodeSelectableAndDistinct(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=new DialogUi(activity,Color.BLACK,Color.WHITE,Color.LTGRAY,0xff303030,Color.GRAY);
      ChatItem reasoning=new ChatItem("reasoning","reasoning","");
      reasoning.detail="Review this change:\n```java\nreturn value;\n```";
      ToolDetailDialog.show(activity,ui,false,reasoning,()->reasoning,value->{});
      Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      TextView prose=containing(dialog.getWindow().getDecorView(),"Review this change:");
      TextView code=containing(dialog.getWindow().getDecorView(),"return value;");
      assertNotNull(prose);assertNotNull(code);assertTrue(prose.isTextSelectable());assertTrue(code.isTextSelectable());
      assertNotEquals(Typeface.MONOSPACE,prose.getTypeface());assertEquals(Typeface.MONOSPACE,code.getTypeface());
      assertTrue(code.getText() instanceof android.text.Spanned);dialog.dismiss();
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void aSearchWithoutSeparateOutputKeepsItsFullQuery(){
    ChatItem search=new ChatItem("search","webSearch","A full search query");
    assertEquals("A full search query",ToolDetailDialog.raw(search));
    search.detail="Some results";
    assertEquals("A full search query\n\nSome results",ToolDetailDialog.raw(search));
  }
}
