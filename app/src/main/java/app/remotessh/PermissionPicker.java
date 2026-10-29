package app.remotessh;

import android.app.*;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;

/** Owns the permission sheet and the explicit temporary-approval confirmation. */
final class PermissionPicker {
  static final String WARNING = "新的审批权限生效前，客户端将会自动允许全部审批请求，继续吗？";
  private final Activity activity;
  private final RemoteApp app;
  private final DialogUi ui;
  private final boolean wide;
  private Dialog dialog, warning;
  private Runnable listener;
  private Runnable warningListener;

  PermissionPicker(Activity activity, RemoteApp app, DialogUi ui, boolean wide) {
    this.activity = activity; this.app = app; this.ui = ui; this.wide = wide;
  }

  int red() { return ui.bg == Color.BLACK ? Color.rgb(255, 103, 103) : Color.rgb(202, 48, 48); }

  int color(PermissionMode mode) {
    switch (mode) {
      case DEFAULT: return Color.rgb(40, 151, 110);
      case READ_ONLY: return Color.rgb(62, 133, 225);
      case FULL: return Color.rgb(221, 133, 37);
      default: return ui.muted;
    }
  }

  static String icon(PermissionMode mode) {
    switch (mode) {
      case DEFAULT: return "shieldCheck";
      case READ_ONLY: return "lock";
      case FULL: return "bolt";
      default: return "shield";
    }
  }

  void dismiss() {
    if (listener != null) app.stateListeners.remove(listener);
    listener = null;
    if (warningListener != null) app.stateListeners.remove(warningListener);
    warningListener = null;
    if (dialog != null) dialog.dismiss();
    if (warning != null) warning.dismiss();
  }

  void show() {
    dismiss();
    Profile profile = app.profile;
    String thread = app.threadId;
    int generation = app.connectionGeneration();
    boolean forcedAtOpen = app.forcedApprovals.active();
    PermissionMode selectedAtOpen = app.permissionMode;
    LinearLayout choices = ui.column();
    choices.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(4));
    for (PermissionMode mode : PermissionMode.values()) {
      boolean selected = mode == app.permissionMode && !app.forcedApprovals.active();
      LinearLayout option = option(choices, icon(mode), color(mode),
          mode == PermissionMode.INHERIT ? UiText.get(R.string.ui_use_current_conversation) : UiPresentation.permission(mode), ui.fg, selected);
      option.setContentDescription(UiText.get(R.string.ui_permission_option_1_s, UiPresentation.permission(mode)));
      ui.clickable(option, () -> {
        if (app.profile == profile && app.threadId.equals(thread) && generation == app.connectionGeneration()) app.choosePermission(mode);
        dismiss();
      });
    }
    if (forcedAtOpen) {
      LinearLayout force = option(choices, "bolt", red(), UiText.get(R.string.ui_force_allow), red(), true);
      force.setContentDescription(UiText.get(R.string.ui_permission_option_1_s, UiText.get(R.string.ui_force_allow)));
      ui.clickable(force, () -> {
        if (app.profile == profile && app.threadId.equals(thread) && generation == app.connectionGeneration()) app.forcedApprovals.disable();
        dismiss();
      });
    }
    TextView note = ui.text(UiText.get(R.string.ui_send_a_message_to_update_permissions), 12, ui.muted);
    note.setPadding(ui.dp(16), ui.dp(12), ui.dp(8), 0);
    choices.addView(note);
    dialog = ui.sheet(UiText.get(R.string.ui_permissions_342), choices, wide);
    Runnable ownedListener = () -> {
      if (app.profile != profile || !app.threadId.equals(thread) || generation != app.connectionGeneration()
          || forcedAtOpen != app.forcedApprovals.active() || selectedAtOpen != app.permissionMode) dismiss();
    };
    listener = ownedListener;
    app.stateListeners.add(ownedListener);
    dialog.setOnDismissListener(ignored -> {
      app.stateListeners.remove(ownedListener);
      if (listener == ownedListener) listener = null;
    });
  }

  private LinearLayout option(LinearLayout choices, String icon, int iconColor, String name, int textColor, boolean selected) {
    LinearLayout option = ui.row();
    option.setPadding(ui.dp(8), 0, ui.dp(12), 0);
    option.setBackground(ui.rounded(selected ? ui.panel : ui.bg, 16));
    option.addView(new IconView(activity, icon, iconColor), new LinearLayout.LayoutParams(ui.dp(44), ui.dp(52)));
    TextView label = ui.text(name, 15, textColor);
    label.setGravity(Gravity.CENTER_VERTICAL);
    label.setMinHeight(ui.dp(52));
    label.setPadding(0, ui.dp(8), 0, ui.dp(8));
    option.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    if (selected) option.addView(new IconView(activity, "check", textColor), new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
    choices.addView(option);
    return option;
  }

  void confirm(JSONObject request) {
    if (warning != null && warning.isShowing()) return;
    if (request == null || app.profile == null || !app.forcedApprovals.canOffer(request)) return;
    Profile profile = app.profile;
    int generation = app.connectionGeneration();
    String thread = Json.object(request, "params").optString("threadId", app.threadId);
    warning = new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_allow_all_forced)).setMessage(UiText.get(R.string.ui_the_client_will_automatically_allow_all_approval_requests_until_t))
        .setNegativeButton(UiText.get(R.string.ui_cancel), null)
        .setPositiveButton(UiText.get(R.string.ui_continue), (ignored, which) -> {
          if (app.profile != profile || generation != app.connectionGeneration() || !app.connected) return;
          if (!app.forcedApprovals.canOffer(request)) return;
          if (!app.forcedApprovals.enable(thread)) ui.toast(UiText.get(R.string.ui_the_conversation_is_recovering_or_read_only_force_allow_was_not_e));
        }).create();
    warning.show();
    ((AlertDialog) warning).getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(red());
    Runnable ownedWarningListener = () -> {
      if (app.profile != profile || generation != app.connectionGeneration() || !app.connected
          || !app.forcedApprovals.canOffer(request)) warning.dismiss();
    };
    warningListener = ownedWarningListener;
    app.stateListeners.add(ownedWarningListener);
    warning.setOnDismissListener(ignored -> {
      app.stateListeners.remove(ownedWarningListener);
      if (warningListener == ownedWarningListener) warningListener = null;
    });
  }
}
