package app.remotessh;

import android.app.*;

/** Conversation menu and last reported context usage. */
final class ContextMenuPanel {
  private final Activity activity;
  private final RemoteApp app;
  private final DialogUi ui;
  private final Runnable newChat, recentError;

  ContextMenuPanel(Activity activity, RemoteApp app, DialogUi ui, Runnable newChat, Runnable recentError) {
    this.activity = activity; this.app = app; this.ui=ui; this.newChat = newChat; this.recentError = recentError;
  }

  void show() {
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_conversation))
        .setItems(new String[] {UiText.get(R.string.ui_context_information),
            UiText.get(R.string.ui_compact_context), UiText.get(R.string.ui_new_conversation),
            UiText.get(R.string.ui_last_error)}, (d, which) -> {
          if (which == 0) contextInfo();
          else if (which == 1) confirmCompact();
          else if (which == 2) newChat.run();
          else recentError.run();
        }).show();
  }

  private void contextInfo() {
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_context_information))
        .setView(new ContextUsagePanel(activity, app.contextUsage, ui))
        .setPositiveButton(UiText.get(R.string.ui_close), null).show();
  }

  private void confirmCompact() {
    if (app.contextCompaction.isCompressing()) {
      new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_compact_context))
          .setMessage(UiText.get(R.string.ui_context_compacting))
          .setPositiveButton(UiText.get(R.string.ui_close), null).show();
      return;
    }
    if (!app.contextCompaction.canStart()) { compactUnavailable(); return; }
    String id = app.threadId;
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_compact_context))
        .setMessage(UiText.get(app.readOnly ? R.string.ui_compact_resume_confirmation :
            R.string.ui_compact_confirmation))
        .setNegativeButton(UiText.get(R.string.ui_cancel), null)
        .setPositiveButton(UiText.get(R.string.ui_compact_context), (d,w) -> {
          if (!app.contextCompaction.start(id)) compactUnavailable();
        }).show();
  }

  private void compactUnavailable() {
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_compact_context))
        .setMessage(UiText.get(R.string.ui_compact_unavailable))
        .setPositiveButton(UiText.get(R.string.ui_close), null).show();
  }
}
