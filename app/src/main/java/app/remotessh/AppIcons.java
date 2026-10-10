package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.CompoundButton;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/** Shared vector icons for controls and inline status symbols, independent of fonts. */
final class AppIcons {
  static final float STROKE_WIDTH = AppIconCatalog.STROKE_WIDTH;
  static Drawable drawable(Context context,String name,int color) {
    Drawable icon=context.getDrawable(AppIconCatalog.resource(name)).mutate();icon.setTint(color);return icon;
  }
  static void selection(CompoundButton button,int foreground,int muted,boolean radio) {
    Context context=button.getContext();String off=radio?"radio":"checkbox",on=radio?"radioSelected":"checkboxChecked";
    StateListDrawable states=new StateListDrawable();
    Drawable disabledOn=drawable(context,on,foreground),disabledOff=drawable(context,off,muted);disabledOn.setAlpha(100);disabledOff.setAlpha(100);
    states.addState(new int[]{-android.R.attr.state_enabled,android.R.attr.state_checked},disabledOn);
    states.addState(new int[]{-android.R.attr.state_enabled},disabledOff);
    states.addState(new int[]{android.R.attr.state_checked},drawable(context,on,foreground));
    states.addState(new int[]{},drawable(context,off,muted));
    button.setButtonTintList(null);button.setButtonDrawable(states);
  }
  static CharSequence label(Context context,CharSequence text,String leading,String trailing,int color,int size) {
    SpannableStringBuilder result=new SpannableStringBuilder(text);
    if(text.length()>0 && leading!=null)result.setSpan(new Glyph(drawable(context,leading,color),size),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if(text.length()>0 && trailing!=null)result.setSpan(new Glyph(drawable(context,trailing,color),size),text.length()-1,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return result;
  }
  private static final class Glyph extends ReplacementSpan {
    final Drawable drawable;final int size;
    Glyph(Drawable drawable,int size){this.drawable=drawable;this.size=size;drawable.setBounds(0,0,size,size);}
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt fm){return size;}
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
      Paint.FontMetrics fm=paint.getFontMetrics();canvas.save();canvas.translate(x,y+(fm.ascent+fm.descent-size)/2);drawable.draw(canvas);canvas.restore();
    }
  }
}
