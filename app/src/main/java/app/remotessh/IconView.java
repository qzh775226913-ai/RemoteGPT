package app.remotessh;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
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
  private Integer feedbackColor;

  // Keep the 48dp hit target; only the copy glyph and its feedback use this visual slot.
  Rect copyFeedbackBounds() {
    int diameter = Math.min(Math.min(getWidth(), getHeight()),
        Math.round(32 * getResources().getDisplayMetrics().density));
    int left = alignBottomEnd ? getWidth() - diameter : (getWidth() - diameter) / 2;
    int top = alignBottomEnd ? getHeight() - diameter : (getHeight() - diameter) / 2;
    return new Rect(left, top, left + diameter, top + diameter);
  }

  void setCopyFeedback(int color) {
    feedbackColor = color;
    updateCopyFeedback();
  }

  private void updateCopyFeedback() {
    Rect bounds = copyFeedbackBounds();
    GradientDrawable mask = new GradientDrawable();
    mask.setShape(GradientDrawable.OVAL);
    mask.setColor(Color.WHITE);
    RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(feedbackColor), null, mask);
    ripple.setRadius(bounds.width() / 2);
    setForeground(new InsetDrawable(ripple, bounds.left, bounds.top,
        getWidth() - bounds.right, getHeight() - bounds.bottom));
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (feedbackColor != null) updateCopyFeedback();
  }

  @Override public void drawableHotspotChanged(float x, float y) {
    if (feedbackColor != null) {
      Rect bounds = copyFeedbackBounds();
      x = bounds.exactCenterX();
      y = bounds.exactCenterY();
    }
    super.drawableHotspotChanged(x, y);
  }

  public IconView(Context context){this(context,"computer",Color.BLACK);}
  public IconView(Context context,String icon,int color){super(context);this.icon=icon;this.color=color;}

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(glyph==null || !icon.equals(renderedIcon) || color!=renderedColor) {
      glyph=AppIcons.drawable(getContext(),icon,color);renderedIcon=icon;renderedColor=color;
    }
    float size=Math.min(getWidth(),getHeight())*.48f;
    canvas.save();
    if (feedbackColor != null) {
      Rect bounds = copyFeedbackBounds();
      canvas.translate(bounds.exactCenterX() - size / 2, bounds.exactCenterY() - size / 2);
    } else canvas.translate(alignBottomEnd?getWidth()-size-4*getResources().getDisplayMetrics().density:(getWidth()-size)/2,
        alignBottomEnd?getHeight()-size:(getHeight()-size)/2);
    glyph.setBounds(0,0,Math.round(size),Math.round(size));glyph.draw(canvas);canvas.restore();
  }
}
