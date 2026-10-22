package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MathDefectsTest {
  static final String[] CASES={"U_t\\approx\\Omega r","W\\approx\\sqrt{(\\Omega r)^2+(V_\\infty+v_i)^2}","\\phi\\approx\\arctan\\frac{V_\\infty+v_i}{\\Omega r}","\\boxed{\\alpha=\\theta-\\phi}","\\boxed{dT=\\frac12\\rho BW^2c(C_L\\cos\\phi+C_D\\sin\\phi)\\,dr}","\\boxed{dQ=\\frac12\\rho BW^2cr(C_L\\sin\\phi-C_D\\cos\\phi)\\,dr}"};
  @Before public void init(){ru.noties.jlatexmath.JLatexMathAndroid.init(RuntimeEnvironment.getApplication());MathGlyphs.init(RuntimeEnvironment.getApplication().getAssets());}
  @Test public void everyFormulaFromReportedScreenshotsMustTypeset() {
    for(String tex:CASES) {
      assertNotNull(tex,MathRenderer.render(tex,true,40,Color.WHITE));
    }
  }
  @Test public void rectangleDrawingCannotChangeTextFillOrSubsequentFrames() {
    MathDrawable.Graphics graphics=new MathDrawable.Graphics();
    android.graphics.Typeface face=android.graphics.Typeface.createFromAsset(RuntimeEnvironment.getApplication().getAssets(),
        "org/scilab/forge/jlatexmath/fonts/latin/jlm_cmr10.ttf");
    graphics.setFont(ru.noties.jlatexmath.awt.Font.createFont(face,48));
    graphics.setColor(new ru.noties.jlatexmath.awt.Color(Color.WHITE));
    Bitmap before=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);graphics.setCanvas(new Canvas(before));
    graphics.drawChars(new char[]{'A'},0,1,10,60);
    Bitmap after=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);graphics.setCanvas(new Canvas(after));
    graphics.draw(new ru.noties.jlatexmath.awt.geom.Rectangle2D.Float(200,200,20,20));
    graphics.drawChars(new char[]{'A'},0,1,10,60);
    assertTrue("Borders must not turn later letters into outlines",before.sameAs(after));
    before.recycle();after.recycle();
  }
  @Test public void cachedBoxedFormulaRendersIdenticallyOnEveryFrame() {
    MathRenderer.Formula f=MathRenderer.render("\\boxed{\\alpha=\\theta-\\phi}",true,48,Color.WHITE);
    assertNotNull(f);Bitmap first=Bitmap.createBitmap(f.width,f.height,Bitmap.Config.ARGB_8888);
    Bitmap next=Bitmap.createBitmap(f.width,f.height,Bitmap.Config.ARGB_8888);
    f.drawable.draw(new Canvas(first));f.drawable.draw(new Canvas(next));assertTrue(first.sameAs(next));
    first.recycle();next.recycle();
  }
  @Test public void uppercaseOmegaMustProduceVisiblePixels() {
    MathRenderer.Formula f=MathRenderer.render("\\Omega",true,48,Color.WHITE);assertNotNull(f);
    Bitmap b=Bitmap.createBitmap(f.width,f.height,Bitmap.Config.ARGB_8888);f.drawable.setBounds(0,0,f.width,f.height);f.drawable.draw(new Canvas(b));
    int pixels=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>128)pixels++;
    assertTrue("Omega glyph must not disappear; pixels="+pixels,pixels>40);b.recycle();
  }
}
