package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Compact context ring centered in the existing 48dp touch target. */
final class ContextRingView extends IconView {
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float fraction;
  private boolean known;
  private int trackColor, progressColor;

  ContextRingView(Context context, int iconColor, int trackColor, int progressColor) {
    super(context, "more", iconColor);
    this.trackColor = trackColor;
    this.progressColor = progressColor;
  }

  void show(ContextUsage usage) {
    known = usage != null && usage.known;
    fraction = known ? usage.fraction() : 0f;
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    float x = getWidth() / 2f, y = getHeight() / 2f;
    canvas.save();
    canvas.scale(.6f, .6f, x, y);
    super.onDraw(canvas);
    canvas.restore();
    float density = getResources().getDisplayMetrics().density;
    float radius = 10.8f * density;
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2.6f * density);
    stroke.setStrokeCap(Paint.Cap.ROUND);
    stroke.setColor(trackColor);
    canvas.drawCircle(x, y, radius, stroke);
    if (known && fraction > 0) {
      stroke.setColor(progressColor);
      canvas.drawArc(x-radius, y-radius, x+radius, y+radius, -90, 360*fraction, false, stroke);
    }
    stroke.setStyle(Paint.Style.FILL);
  }
}
