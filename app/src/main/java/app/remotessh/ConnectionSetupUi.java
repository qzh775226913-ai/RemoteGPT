package app.remotessh;

import android.app.Activity;
import android.app.AlertDialog;

/** Explicit downgrade after a Python failure; never installs software or changes credentials. */
final class ConnectionSetupUi {
  static boolean canContinue(RemoteApp app, String diagnostic) {
    return !app.connected && !app.connecting && app.profile != null && app.profile.persistent
        && ConnectionPreflight.canDisablePersistence(diagnostic);
  }

  static void continueWithoutPersistence(Activity activity, RemoteApp app) {
    Profile source = app.profile;
    if (source == null || app.connected || app.connecting) return;
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_disable_persistence_and_reconnect))
        .setMessage(UiText.get(R.string.ui_codex_will_start_directly_tasks_may_not_continue_after_disconnect))
        .setNegativeButton(UiText.get(R.string.ui_cancel), null)
        .setPositiveButton(UiText.get(R.string.ui_disable_and_reconnect), (dialog, which) -> {
          if (app.profile != source || app.connected || app.connecting) return;
          Profile updated = Profile.from(source.json());
          updated.password = source.password; updated.privateKey = source.privateKey; updated.passphrase = source.passphrase;
          updated.persistent = false;
          if (app.storeProfile(updated)) app.connect(updated);
        }).show();
  }
}
