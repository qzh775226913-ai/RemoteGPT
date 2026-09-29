package app.remotessh;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DialogStyleTest {
  private final Context app = RuntimeEnvironment.getApplication();

  private int alertDialogTheme(int theme) {
    ContextThemeWrapper context = new ContextThemeWrapper(app, theme);
    TypedValue value = new TypedValue();
    context.getTheme().resolveAttribute(android.R.attr.alertDialogTheme, value, true);
    return value.resourceId;
  }

  @Test public void appThemesUseMatchingRoundedDialogs() {
    assertEquals(R.style.RemoteDialog, alertDialogTheme(R.style.AppTheme));
    assertEquals(R.style.RemoteDialogDark, alertDialogTheme(R.style.AppThemeDark));

    float radius = 24 * app.getResources().getDisplayMetrics().density;
    GradientDrawable light =
        (GradientDrawable) app.getDrawable(R.drawable.remote_dialog_light);
    GradientDrawable dark =
        (GradientDrawable) app.getDrawable(R.drawable.remote_dialog_dark);
    assertEquals(radius, light.getCornerRadius(), .1f);
    assertEquals(radius, dark.getCornerRadius(), .1f);
  }
}
