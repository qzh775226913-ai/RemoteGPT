package app.remotessh;

import android.content.Context;
import android.text.Layout;
import android.text.StaticLayout;
import android.widget.TextView;

/** Four visual lines, always showing the newest end of streamed progress. */
final class LiveProgressTextView extends TextView {
  private String progress = "";
  LiveProgressTextView(Context context) {
    super(context);
    setTextSize(15);
    setLines(4);
    setIncludeFontPadding(false);
    setLineSpacing(6 * getResources().getDisplayMetrics().density, 1);
    setTextIsSelectable(true);
    setContentDescription(UiText.get(R.string.ui_latest_activity));
  }
  void setProgress(String value) { progress = value; fitProgress(); }
  private void fitProgress() {
    int width = getWidth() - getPaddingLeft() - getPaddingRight();
    if (width <= 0 || progress.isEmpty()) { setText(progress); return; }
    StaticLayout layout = StaticLayout.Builder.obtain(progress, 0, progress.length(), getPaint(), width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        .setLineSpacing(getLineSpacingExtra(), getLineSpacingMultiplier()).build();
    setText(layout.getLineCount() <= 4 ? progress : progress.substring(layout.getLineStart(layout.getLineCount() - 4)));
  }
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w,h,oldw,oldh);
    if (w != oldw) fitProgress();
  }
}
