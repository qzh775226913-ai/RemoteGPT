package app.remotessh;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/** Consistent touch feedback for the app's custom TextView, IconView and row controls. */
final class PressFeedback {
  private PressFeedback() {}

  static void apply(View view, boolean dark) {
    view.setClickable(true);
    view.setFocusable(true);
    int ripple = dark ? 0x33ffffff : 0x22000000;
    if (view instanceof IconView && "copy".equals(((IconView) view).icon)) {
      // Dialog icon controls already have a background ripple. Use one bounded layer.
      if (view.getBackground() instanceof RippleDrawable) view.setBackground(null);
      ((IconView) view).setCopyFeedback(ripple);
      return;
    }
    view.setForeground(
        new RippleDrawable(
            ColorStateList.valueOf(ripple), null, new ColorDrawable(Color.WHITE)));
  }
}
