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
          mode == PermissionMode.INHERIT ? "沿用当前对话" : mode.label, ui.fg, selected);
      option.setContentDescription("权限选项：" + mode.label);
      ui.clickable(option, () -> {
        if (app.profile == profile && app.threadId.equals(thread) && generation == app.connectionGeneration()) app.choosePermission(mode);
        dismiss();
      });
    }
    if (forcedAtOpen) {
      LinearLayout force = option(choices, "bolt", red(), "强制允许", red(), true);
      force.setContentDescription("权限选项：强制允许");
      ui.clickable(force, () -> {
        if (app.profile == profile && app.threadId.equals(thread) && generation == app.connectionGeneration()) app.forcedApprovals.disable();
        dismiss();
      });
    }
    TextView note = ui.text("发送消息以更新权限", 12, ui.muted);
    note.setPadding(ui.dp(16), ui.dp(12), ui.dp(8), 0);
    choices.addView(note);
    dialog = ui.sheet("权限", choices, wide);
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
    option.addView(label, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
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
    warning = new AlertDialog.Builder(activity).setTitle("全部允许(强制)").setMessage(WARNING)
        .setNegativeButton("取消", null)
        .setPositiveButton("继续", (ignored, which) -> {
          if (app.profile != profile || generation != app.connectionGeneration() || !app.connected) return;
          if (!app.forcedApprovals.canOffer(request)) return;
          if (!app.forcedApprovals.enable(thread)) ui.toast("对话正在恢复或仅查看历史，未启用强制允许");
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
