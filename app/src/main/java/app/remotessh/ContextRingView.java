package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Thin context ring around the existing 48dp conversation menu icon. */
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
    super.onDraw(canvas);
    float density = getResources().getDisplayMetrics().density;
    float radius = 18 * density;
    float x = getWidth() / 2f, y = getHeight() / 2f;
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2 * density);
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
