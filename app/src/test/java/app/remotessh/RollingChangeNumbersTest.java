package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public class RollingChangeNumbersTest {
  @Test public void rapidRealValuesRetargetWithoutJumpAndCatchUpQuickly() throws Exception {
    RollingChangeNumbers numbers=new RollingChangeNumbers(true,()->{});
    numbers.style("header","+1",false,false);numbers.attach();numbers.style("header","+2",true,false);
    Field countersField=RollingChangeNumbers.class.getDeclaredField("counters");countersField.setAccessible(true);
    Object counter=((Map<?,?>)countersField.get(numbers)).get("header/0+");
    Field animatorField=counter.getClass().getDeclaredField("animator");animatorField.setAccessible(true);
    Field progressField=counter.getClass().getDeclaredField("progress");progressField.setAccessible(true);
    ValueAnimator animator=(ValueAnimator)animatorField.get(counter);assertEquals(320,animator.getDuration());
    assertTrue("ease begins with acceleration",animator.getInterpolator().getInterpolation(.1f)<.1f);
    assertTrue("ease settles into the target",animator.getInterpolator().getInterpolation(.9f)>.9f);
    animator.setCurrentPlayTime(80);float before=progressField.getFloat(counter);
    numbers.style("header","+3",true,false);float after=progressField.getFloat(counter);
    assertEquals("speeding up must preserve the visible position",before,after,.025f);
    assertEquals(200,((ValueAnimator)animatorField.get(counter)).getDuration());
    numbers.style("header","+4",true,false);numbers.style("header","+5",true,false);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));assertFalse(numbers.animating());
    Field current=counter.getClass().getDeclaredField("current");current.setAccessible(true);
    Field from=counter.getClass().getDeclaredField("from");from.setAccessible(true);
    assertEquals("5",current.get(counter));assertNull("the settled span must draw the latest number",from.get(counter));
    numbers.style("header","+5",true,false);assertFalse("identical server values do not animate",numbers.animating());
    numbers.style("header","+6",true,false);assertTrue(numbers.animating());numbers.settle();assertFalse(numbers.animating());
    numbers.detach();
  }

  @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void settlingADigitWidthChangeDoesNotVisiblyJump() throws Exception {
    RollingChangeNumbers numbers=new RollingChangeNumbers(true,()->{});
    numbers.style("header","+9",false,false);numbers.attach();
    Spanned text=(Spanned)numbers.style("header","+10",true,false);
    ReplacementSpan span=text.getSpans(0,text.length(),ReplacementSpan.class)[0];
    Field countersField=RollingChangeNumbers.class.getDeclaredField("counters");countersField.setAccessible(true);
    Object counter=((Map<?,?>)countersField.get(numbers)).get("header/0+");
    Field animatorField=counter.getClass().getDeclaredField("animator");animatorField.setAccessible(true);
    ValueAnimator animator=(ValueAnimator)animatorField.get(counter);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(36);span.getSize(paint,text,0,text.length(),null);
    Bitmap before=Bitmap.createBitmap(160,70,Bitmap.Config.ARGB_8888),after=Bitmap.createBitmap(160,70,Bitmap.Config.ARGB_8888);
    animator.setCurrentPlayTime(animator.getDuration()-1);
    span.draw(new Canvas(before),text,0,text.length(),5,0,48,70,paint);
    animator.end();span.draw(new Canvas(after),text,0,text.length(),5,0,48,70,paint);
    int changed=0,visible=0;
    for(int y=0;y<70;y++)for(int x=0;x<160;x++){
      if(before.getPixel(x,y)!=after.getPixel(x,y))changed++;
      if(after.getPixel(x,y)!=0)visible++;
    }
    assertTrue("the final value must remain visible",visible>50);
    assertTrue("the last frame must align with the settled value",changed<80);
    before.recycle();after.recycle();numbers.detach();
  }
}
