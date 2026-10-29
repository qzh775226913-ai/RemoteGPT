package app.remotessh;

import android.app.Activity;
import android.app.AlertDialog;
import java.util.Arrays;

/** Native language picker, using autonyms even when the UI language changes. */
final class LanguagePicker {
  static void show(Activity activity, RemoteApp app) {
    String[] labels = AppLanguage.NAMES.clone();
    labels[0] = UiText.get(R.string.ui_follow_system);
    AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle(UiText.get(R.string.ui_language))
        .setSingleChoiceItems(labels, Arrays.asList(AppLanguage.TAGS).indexOf(AppLanguage.preference(app)),
            (window, index) -> { window.dismiss(); app.language.select(AppLanguage.TAGS[index]); })
        .setNegativeButton(UiText.get(R.string.ui_cancel), null).create();
    // Tie this window to its owner without retaining an application listener.
    activity.getApplication().registerActivityLifecycleCallbacks(new AppLanguageWindow(activity, dialog));
    dialog.show();
  }

  private static final class AppLanguageWindow implements android.app.Application.ActivityLifecycleCallbacks {
    private final Activity owner;
    private final AlertDialog dialog;
    AppLanguageWindow(Activity owner, AlertDialog dialog) {
      this.owner = owner; this.dialog = dialog;
      dialog.setOnDismissListener(ignored -> owner.getApplication().unregisterActivityLifecycleCallbacks(this));
    }
    public void onActivityDestroyed(Activity activity) { if (activity == owner) dialog.dismiss(); }
    public void onActivityCreated(Activity a, android.os.Bundle b) {}
    public void onActivityStarted(Activity a) {}
    public void onActivityResumed(Activity a) {}
    public void onActivityPaused(Activity a) {}
    public void onActivityStopped(Activity a) {}
    public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
  }
}
