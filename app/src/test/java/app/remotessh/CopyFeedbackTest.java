package app.remotessh;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.MotionEvent;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CopyFeedbackTest {
  private final Context context=RuntimeEnvironment.getApplication();
  private int dp(int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }

  @Test public void lightAndDarkCopyFeedbackIsCircularAndAligned() throws Exception {
    checkGeometry();
  }
  @Test @Config(sdk=26, qualifiers="xhdpi") public void android8HighDensityUsesSameGeometry() throws Exception {
    checkGeometry();
  }

  private void checkGeometry() throws Exception {
    org.robolectric.android.controller.ActivityController<android.app.Activity> controller=
        Robolectric.buildActivity(android.app.Activity.class).setup();
    try {
    Bitmap preview=Bitmap.createBitmap(dp(192),dp(48),Bitmap.Config.ARGB_8888);
    Canvas gallery=new Canvas(preview);
    int index=0;
    for(boolean dark:new boolean[]{false,true}) for(boolean bottom:new boolean[]{false,true}) {
      IconView view=new IconView(context,"copy",dark?Color.WHITE:Color.BLACK);
      // Reproduce the background installed by DialogUi.icon before PressFeedback.apply.
      view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33808080),null,new ColorDrawable(Color.WHITE)));
      PressFeedback.apply(view,dark);
      view.alignBottomEnd=bottom;
      controller.get().setContentView(view,new android.view.ViewGroup.LayoutParams(dp(48),dp(48)));
      view.layout(0,0,dp(48),dp(48));
      assertNull("Only one ripple layer",view.getBackground());
      assertTrue(view.isClickable());assertTrue(view.isFocusable());
      assertEquals(dp(48),view.getWidth());assertEquals(dp(48),view.getHeight());
      InsetDrawable foreground=(InsetDrawable)view.getForeground();
      RippleDrawable ripple=(RippleDrawable)foreground.getDrawable();
      Bitmap glyph=Bitmap.createBitmap(dp(48),dp(48),Bitmap.Config.ARGB_8888);
      view.draw(new Canvas(glyph)); // Propagates the foreground's bounds through its insets.
      Rect bounds=ripple.getBounds();
      assertEquals(dp(32),bounds.width());assertEquals(bounds.width(),bounds.height());
      assertEquals(dp(16),ripple.getRadius());
      assertEquals(bottom?dp(16):dp(8),bounds.left);assertEquals(bounds.left,bounds.top);
      Drawable mask=ripple.findDrawableByLayerId(android.R.id.mask);
      Bitmap maskImage=Bitmap.createBitmap(dp(48),dp(48),Bitmap.Config.ARGB_8888);
      mask.draw(new Canvas(maskImage));
      assertEquals("No square corner",0,Color.alpha(maskImage.getPixel(bounds.left+1,bounds.top+1)));
      assertEquals(255,Color.alpha(maskImage.getPixel(bounds.centerX(),bounds.centerY())));
      for(int y=0;y<glyph.getHeight();y++)for(int x=0;x<glyph.getWidth();x++) {
        if(Color.alpha(glyph.getPixel(x,y))>0)
          assertTrue("Glyph stays inside its circular feedback",Math.hypot(x+.5-bounds.exactCenterX(),y+.5-bounds.exactCenterY())<=dp(16));
      }
      // Any point within the larger 48dp hit target must still trigger the copy action once.
      int[] clicks={0};view.setOnClickListener(v->clicks[0]++);
      view.requestFocus();
      MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,1,1,0);
      MotionEvent up=MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,1,1,0);
      assertTrue(view.dispatchTouchEvent(down));assertTrue(view.dispatchTouchEvent(up));
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(1,clicks[0]);down.recycle();up.recycle();
      gallery.save();gallery.translate(dp(48)*index++,0);
      Paint background=new Paint();background.setColor(dark?0xff171717:Color.WHITE);gallery.drawRect(0,0,dp(48),dp(48),background);
      // Static mask overlay documents the clipping geometry; it is not an animation capture.
      Paint tint=new Paint();tint.setAlpha(50);tint.setColorFilter(new PorterDuffColorFilter(dark?Color.WHITE:Color.BLACK,PorterDuff.Mode.SRC_IN));
      gallery.drawBitmap(maskImage,0,0,tint);gallery.drawBitmap(glyph,0,0,null);gallery.restore();
      glyph.recycle();maskImage.recycle();
      view.layout(0,0,dp(64),dp(48));
      Bitmap resizedImage=Bitmap.createBitmap(dp(64),dp(48),Bitmap.Config.ARGB_8888);
      view.draw(new Canvas(resizedImage));resizedImage.recycle();
      Rect resized=((InsetDrawable)view.getForeground()).getDrawable().getBounds();
      assertEquals(bottom?dp(32):dp(16),resized.left);
      assertEquals(dp(32),resized.width());
    }
    File folder=new File("build/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());
    try(FileOutputStream out=new FileOutputStream(new File(folder,"copy-feedback-geometry-"+dp(1)+"x.png"))) {
      assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,out));
    }
    preview.recycle();
    } finally { controller.pause().stop().destroy(); }
  }
}
