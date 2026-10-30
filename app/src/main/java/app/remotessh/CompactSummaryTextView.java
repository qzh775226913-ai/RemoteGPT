package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.TextView;

/** Optically centers a one-line folded summary across Latin, CJK and Cyrillic glyph bounds. */
final class CompactSummaryTextView extends TextView {
  private final Rect inkBounds=new Rect();

  CompactSummaryTextView(Context context) { super(context); }

  float opticalShift() {
    String value=getText().toString().trim();
    if(value.length()>2)value=value.substring(1,value.length()-1).trim();
    if(value.isEmpty())return 0;
    getPaint().getTextBounds(value,0,value.length(),inkBounds);
    Paint.FontMetricsInt metrics=getPaint().getFontMetricsInt();
    float shift=(metrics.ascent+metrics.descent-inkBounds.top-inkBounds.bottom)/2f;
    float limit=1.5f*getResources().getDisplayMetrics().density;
    return Math.max(-limit,Math.min(limit,shift));
  }

  @Override protected void onDraw(Canvas canvas) {
    int save=canvas.save();
    canvas.translate(0,opticalShift());
    super.onDraw(canvas);
    canvas.restoreToCount(save);
  }
}
