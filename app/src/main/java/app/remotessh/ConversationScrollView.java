package app.remotessh;

import android.content.Context;
import android.widget.ScrollView;

/** Follow only while the reader is at the latest message, including delayed layout. */
public final class ConversationScrollView extends ScrollView {
  private boolean following = true, layingOut, pinning;
  public ConversationScrollView(Context context) { super(context); }
  public boolean isFollowing() { return following; }
  public void follow() { following = true; requestLayout(); }
  public void read() { following = false; }
  public boolean nearBottom() {
    return getChildCount() == 0 || getChildAt(0).getHeight() + getPaddingTop()
        + getPaddingBottom() - getHeight() - getScrollY() <= 2 * getResources().getDisplayMetrics().density;
  }
  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int readingY = getScrollY();
    layingOut = true;
    super.onLayout(changed, l, t, r, b);
    if (!following) scrollTo(0, readingY);
    layingOut = false;
    pin();
  }
  public void pin() {
    if (!following || getChildCount() == 0) return;
    pinning = true;
    scrollTo(0, Math.max(0, getChildAt(0).getHeight() - getHeight() + getPaddingTop() + getPaddingBottom()));
    pinning = false;
  }
  @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
    if (!layingOut && !pinning) following = nearBottom();
    super.onScrollChanged(x, y, oldX, oldY);
  }
}
