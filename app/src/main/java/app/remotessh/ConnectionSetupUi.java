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
    new AlertDialog.Builder(activity).setTitle("关闭持久会话后重连")
        .setMessage("关闭后会直接启动 Codex。断线后任务继续运行不保证，远端版本检查／更新和部分问答历史恢复仍需要 Python。不会自动安装任何软件。继续吗？")
        .setNegativeButton("取消", null)
        .setPositiveButton("关闭并重连", (dialog, which) -> {
          if (app.profile != source || app.connected || app.connecting) return;
          Profile updated = Profile.from(source.json());
          updated.password = source.password; updated.privateKey = source.privateKey; updated.passphrase = source.passphrase;
          updated.persistent = false;
          if (app.storeProfile(updated)) app.connect(updated);
        }).show();
  }
}
