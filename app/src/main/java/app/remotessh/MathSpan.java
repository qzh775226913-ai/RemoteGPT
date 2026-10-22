package app.remotessh;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Replaces only the presentation: selectable/copyable text retains its original TeX delimiters. */
final class MathSpan extends ReplacementSpan {
  final MathRenderer.Formula formula;
  MathSpan(MathRenderer.Formula formula){this.formula=formula;}
  @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt fm) {
    if(fm!=null) {
      Paint.FontMetricsInt original=paint.getFontMetricsInt();
      fm.ascent=Math.min(original.ascent,-formula.baseline);fm.top=Math.min(original.top,fm.ascent);
      fm.descent=Math.max(original.descent,formula.height-formula.baseline);fm.bottom=Math.max(original.bottom,fm.descent);
    }
    return formula.width;
  }
  @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint) {
    canvas.save();canvas.translate(x,y-formula.baseline);
    formula.drawable.setBounds(0,0,formula.width,formula.height);formula.drawable.draw(canvas);canvas.restore();
  }
}
