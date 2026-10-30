package app.remotessh;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ContextRingViewTest {
  @Test public void visibleRingShrinksWhileTouchTargetRemainsFullSize() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    ContextRingView ring=new ContextRingView(activity,Color.BLACK,Color.RED,Color.RED);
    ring.layout(0,0,48,48);
    Bitmap bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
    ring.draw(new Canvas(bitmap));
    int min=48,max=-1;
    for(int y=0;y<48;y++) for(int x=0;x<48;x++) {
      int pixel=bitmap.getPixel(x,y);
      if(Color.red(pixel)>180 && Color.green(pixel)<80 && Color.blue(pixel)<80) {
        min=Math.min(min,x);max=Math.max(max,x);
      }
    }
    assertEquals(48,ring.getWidth());
    assertTrue(max>=min);
    assertTrue("ring diameter: "+(max-min+1),max-min+1>=21 && max-min+1<=26);
  }
}
