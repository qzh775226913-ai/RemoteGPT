package app.remotessh;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import org.scilab.forge.jlatexmath.*;
import ru.noties.jlatexmath.awt.AndroidGraphics2D;
import ru.noties.jlatexmath.awt.Insets;

/** Public TeX layout API with isolated, filled text drawing and original missing-glyph outlines. */
final class MathDrawable extends Drawable {
  private final TeXIcon icon;
  private final Graphics graphics=new Graphics();
  MathDrawable(String tex,boolean display,float size,int color) {
    MathGlyphs.prepare();
    icon=new TeXFormula(tex).new TeXIconBuilder().setFGColor(new ru.noties.jlatexmath.awt.Color(color))
        .setSize(size).setStyle(display?TeXConstants.STYLE_DISPLAY:TeXConstants.STYLE_TEXT).build();
    icon.setInsets(new Insets(2,2,2,2));setBounds(0,0,getIntrinsicWidth(),getIntrinsicHeight());
  }
  TeXIcon icon(){return icon;}
  @Override public int getIntrinsicWidth(){return icon.getIconWidth();}
  @Override public int getIntrinsicHeight(){return icon.getIconHeight();}
  @Override public void draw(Canvas canvas) {
    int save=canvas.save();
    try {
      Rect bounds=getBounds();canvas.translate(bounds.left,bounds.top);
      float scale=Math.min(1f,Math.min(bounds.width()/(float)getIntrinsicWidth(),bounds.height()/(float)getIntrinsicHeight()));
      canvas.scale(scale,scale);graphics.setCanvas(canvas);icon.paintIcon(null,graphics,0,0);
    } finally {canvas.restoreToCount(save);}
  }
  @Override public void setAlpha(int alpha) { }
  @Override public void setColorFilter(ColorFilter filter) { }
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  static final class Graphics extends AndroidGraphics2D {
    private Canvas canvas;
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override public void setCanvas(Canvas value){super.setCanvas(value);canvas=value;}
    @Override public void drawChars(char[] chars,int offset,int length,int x,int y) {
      // Upstream shares a Paint with stroked rectangles; text must always have its own fill style.
      text.setStyle(Paint.Style.FILL);text.setColor(getColor().getColorInt());
      if(getFont()!=null){text.setTypeface(getFont().typeface());text.setTextSize(getFont().size());}
      if(!MathGlyphs.draw(canvas,text,getFont(),chars,offset,length,x,y))canvas.drawText(chars,offset,length,x,y,text);
    }
  }
}
