package app.remotessh;

import android.app.*;
import java.text.NumberFormat;

/** Conversation menu and last reported context usage. */
final class ContextMenuPanel {
  private final Activity activity;
  private final RemoteApp app;
  private final Runnable newChat, recentError;

  ContextMenuPanel(Activity activity, RemoteApp app, Runnable newChat, Runnable recentError) {
    this.activity = activity; this.app = app; this.newChat = newChat; this.recentError = recentError;
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
    ContextUsage usage = app.contextUsage;
    String details;
    if (!usage.known) details = UiText.get(R.string.ui_context_unavailable);
    else {
      NumberFormat count = NumberFormat.getIntegerInstance(UiText.locale());
      NumberFormat percent = NumberFormat.getPercentInstance(UiText.locale());
      percent.setMaximumFractionDigits(1);
      details = UiText.get(R.string.ui_context_last_tokens, count.format(usage.used)) + "\n"
          + UiText.get(R.string.ui_context_window_tokens, count.format(usage.window)) + "\n"
          + UiText.get(R.string.ui_context_used_percent, percent.format(usage.fraction())) + "\n\n"
          + UiText.get(R.string.ui_context_input_tokens, count.format(usage.input)) + "\n"
          + UiText.get(R.string.ui_context_cached_tokens, count.format(usage.cachedInput)) + "\n"
          + UiText.get(R.string.ui_context_output_tokens, count.format(usage.output)) + "\n"
          + UiText.get(R.string.ui_context_reasoning_tokens, count.format(usage.reasoningOutput)) + "\n"
          + UiText.get(R.string.ui_context_total_tokens, count.format(usage.total)) + "\n\n"
          + UiText.get(R.string.ui_context_estimate_note);
    }
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_context_information))
        .setMessage(details).setPositiveButton(UiText.get(R.string.ui_close), null).show();
  }

  private void confirmCompact() {
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
