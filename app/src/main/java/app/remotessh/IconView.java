package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Existing control geometry, backed by the common flat vector family. */
public final class IconView extends View {
  public String icon;
  public int color;
  public boolean alignBottomEnd;
  private Drawable glyph;
  private String renderedIcon;
  private int renderedColor;

  public IconView(Context context){this(context,"computer",Color.BLACK);}
  public IconView(Context context,String icon,int color){super(context);this.icon=icon;this.color=color;}

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(glyph==null || !icon.equals(renderedIcon) || color!=renderedColor) {
      glyph=AppIcons.drawable(getContext(),icon,color);renderedIcon=icon;renderedColor=color;
    }
    float size=Math.min(getWidth(),getHeight())*.48f;
    canvas.save();
    canvas.translate(alignBottomEnd?getWidth()-size-4*getResources().getDisplayMetrics().density:(getWidth()-size)/2,
        alignBottomEnd?getHeight()-size:(getHeight()-size)/2);
    glyph.setBounds(0,0,Math.round(size),Math.round(size));glyph.draw(canvas);canvas.restore();
  }
}
