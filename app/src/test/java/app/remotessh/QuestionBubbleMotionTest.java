package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class QuestionBubbleMotionTest {
  @Test public void morphUsesTimedEasingAndCompletionOrCancellationReleasesOverlay() throws Exception {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();FrameLayout root=activity.findViewById(android.R.id.content);
      TextView bubble=new TextView(activity);bubble.setText("?");root.addView(bubble,new FrameLayout.LayoutParams(48,48));
      root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,480,800);
      AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Question").setMessage("Choose").show();
      View decor=dialog.getWindow().getDecorView();
      decor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));decor.layout(20,30,380,330);
      QuestionBubbleMotion motion=new QuestionBubbleMotion(activity);int children=root.getChildCount();boolean[] done={false};
      motion.run(dialog,bubble,true,()->done[0]=true);
      assertFalse(done[0]);assertEquals(children+1,root.getChildCount());assertEquals(0f,bubble.getAlpha(),.001f);
      java.lang.reflect.Field field=QuestionBubbleMotion.class.getDeclaredField("animator");field.setAccessible(true);
      ValueAnimator animator=(ValueAnimator)field.get(motion);assertEquals(260,animator.getDuration());
      float early=animator.getInterpolator().getInterpolation(.25f),late=animator.getInterpolator().getInterpolation(.75f);
      assertTrue(early>0f && early<late && late<1f);assertNotEquals(.25f,early,.01f);
      animator.end();
      assertTrue(done[0]);assertEquals(children,root.getChildCount());assertEquals(1f,bubble.getAlpha(),.001f);
      done[0]=false;motion.run(dialog,bubble,false,()->done[0]=true);assertEquals(children+1,root.getChildCount());
      motion.cancel();assertFalse(done[0]);assertEquals(children,root.getChildCount());dialog.dismiss();
    } finally {owner.pause().stop().destroy();}
  }
}
