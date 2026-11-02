package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.animation.ValueAnimator;
import android.os.Looper;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

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
}
