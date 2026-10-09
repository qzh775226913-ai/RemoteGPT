package app.remotessh;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

/** Keeps model-list gestures inside the list when the settings sheet also scrolls. */
final class ModelListScrollView extends ScrollView {
  ModelListScrollView(Context context) { super(context); }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    ViewParent parent = getParent();
    if (parent != null && event.getActionMasked() == MotionEvent.ACTION_DOWN
        && (canScrollVertically(-1) || canScrollVertically(1))) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
    boolean handled = super.dispatchTouchEvent(event);
    if (parent != null && (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
      parent.requestDisallowInterceptTouchEvent(false);
    }
    return handled;
  }
}
