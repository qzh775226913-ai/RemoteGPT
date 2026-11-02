package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
    return label(context,text,leading,trailing,color,size,false,false);
  }
  static CharSequence compactLabel(Context context,CharSequence text,String leading,String trailing,int color,int size) {
    return label(context,text,leading,trailing,color,size,true,false);
  }
  static CharSequence compactLabel(Context context,CharSequence text,String leading,String trailing,int color,int size,boolean flipTrailing) {
    return label(context,text,leading,trailing,color,size,true,flipTrailing);
  }
  private static CharSequence label(Context context,CharSequence text,String leading,String trailing,int color,int size,
      boolean compact,boolean flipTrailing) {
    SpannableStringBuilder result=new SpannableStringBuilder(text);
    if(text.length()>0 && leading!=null)result.setSpan(new Glyph(drawable(context,leading,color),size,compact,false),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    if(text.length()>0 && trailing!=null)result.setSpan(new Glyph(drawable(context,trailing,color),size,compact,flipTrailing),text.length()-1,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return result;
  }
  static void textInkBounds(Paint paint,CharSequence text,Rect bounds){
    StringBuilder value=new StringBuilder(text);
    if(text instanceof Spanned){
      Spanned spans=(Spanned)text;
      for(Glyph icon:spans.getSpans(0,text.length(),Glyph.class))
        for(int i=Math.max(0,spans.getSpanStart(icon));i<Math.min(value.length(),spans.getSpanEnd(icon));i++)value.setCharAt(i,' ');
    }
    String visible=value.toString().trim();bounds.setEmpty();
    if(!visible.isEmpty())paint.getTextBounds(visible,0,visible.length(),bounds);
  }
  private static final class Glyph extends ReplacementSpan {
    final Drawable drawable;final int size;final boolean compact,flip;
    final Rect ink=new Rect();
    CharSequence measuredText;float measuredSize,measuredScale;android.graphics.Typeface measuredTypeface;
    Glyph(Drawable drawable,int size,boolean compact,boolean flip){
      this.drawable=drawable;this.size=size;this.compact=compact;this.flip=flip;drawable.setBounds(0,0,size,size);
    }
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt fm){return size;}
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
      float center=(top+bottom)/2f;
      if(compact){
        if(measuredText!=text || measuredSize!=paint.getTextSize() || measuredScale!=paint.getTextScaleX() || measuredTypeface!=paint.getTypeface()){
          textInkBounds(paint,text,ink);measuredText=text;measuredSize=paint.getTextSize();measuredScale=paint.getTextScaleX();measuredTypeface=paint.getTypeface();
        }
        if(!ink.isEmpty())center=y+(ink.top+ink.bottom)/2f;
      }
      canvas.save();canvas.translate(x,center-size/2f);
      if(flip)canvas.rotate(180,size/2f,size/2f);
      drawable.draw(canvas);canvas.restore();
    }
  }
}
