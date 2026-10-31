package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.NumberFormat;

/** Small visual summary of the last context usage reported by this thread. */
final class ContextUsagePanel extends LinearLayout {
  ContextUsagePanel(Context context, ContextUsage usage, DialogUi ui) {
    super(context);
    setOrientation(VERTICAL);
    int foreground = ui.fg, secondary = ui.muted;
    setPadding(dp(20), dp(12), dp(20), dp(12));
    if (!usage.known) {
      TextView unknown = label(UiText.get(R.string.ui_context_unavailable), 15, secondary);
      addView(unknown);
      return;
    }
    NumberFormat count = NumberFormat.getIntegerInstance(UiText.locale());
    NumberFormat percent = NumberFormat.getPercentInstance(UiText.locale());
    percent.setMaximumFractionDigits(1);
    LinearLayout overview = new LinearLayout(context);
    overview.setGravity(Gravity.CENTER_VERTICAL);
    overview.addView(new Ring(context, usage.fraction(), ui.line, ui.bg == android.graphics.Color.BLACK ? 0xff79cba7 : 0xff25825e), new LayoutParams(dp(72), dp(72)));
    LinearLayout text = new LinearLayout(context);
    text.setOrientation(VERTICAL);
    text.setPadding(dp(16), 0, 0, 0);
    text.addView(label(percent.format(usage.fraction()), 25, foreground));
    text.addView(label(UiText.get(R.string.ui_context_last_tokens, count.format(usage.used)), 13, secondary));
    text.addView(label(UiText.get(R.string.ui_context_window_tokens, count.format(usage.window)), 13, secondary));
    overview.addView(text, new LayoutParams(0, -2, 1));
    addView(overview);
    addView(stat(UiText.get(R.string.ui_context_input_tokens, count.format(usage.input)),
        UiText.get(R.string.ui_context_cached_tokens, count.format(usage.cachedInput)), secondary));
    addView(stat(UiText.get(R.string.ui_context_output_tokens, count.format(usage.output)),
        UiText.get(R.string.ui_context_reasoning_tokens, count.format(usage.reasoningOutput)), secondary));
  }

  private LinearLayout stat(String first, String second, int color) {
    LinearLayout row = new LinearLayout(getContext());
    row.setPadding(0, dp(8), 0, 0);
    row.addView(label(first, 13, color), new LayoutParams(0, -2, 1));
    row.addView(label(second, 13, color), new LayoutParams(0, -2, 1));
    return row;
  }

  private TextView label(String text, int size, int color) {
    TextView view = new TextView(getContext());
    view.setText(text); view.setTextSize(size); view.setTextColor(color);
    return view;
  }

  private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

  private static final class Ring extends android.view.View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float fraction;
    private final int track, accent;
    Ring(Context context, float fraction,int track,int accent) { super(context); this.fraction = fraction;this.track=track;this.accent=accent; }
    @Override protected void onDraw(Canvas canvas) {
      float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(cx, cy)-dp(5);
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(8)); paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(track); canvas.drawCircle(cx, cy, radius, paint);
      paint.setColor(accent);
      canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90, 360*fraction, false, paint);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  }
}
