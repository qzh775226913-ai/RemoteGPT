package app.remotessh;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Owns the connection draft, credential form and key-import result for one window. */
final class ProfileEditor {
  static final int REQUEST_KEY = 42;
  interface Actions {
    void connect(Profile profile);
    void removed();
  }

  private final Activity activity;
  private final RemoteApp app;
  private final DialogUi ui;
  private final boolean wide;
  private final Actions actions;
  private Dialog dialog;
  private EditText keyEditor;

  ProfileEditor(Activity activity, RemoteApp app, DialogUi ui, boolean wide, Actions actions) {
    this.activity = activity; this.app = app; this.ui = ui; this.wide = wide; this.actions = actions;
  }

  boolean isShowing() { return dialog != null && dialog.isShowing(); }
  void dismiss() { if (dialog != null) dialog.dismiss(); }

  void importKey(Uri uri) {
    if (!isShowing() || keyEditor == null || uri == null) return;
    try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
      if (in == null) throw new IOException("无法读取文件");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) {
        if (bytes.size() + count > 65536) throw new IOException("私钥文件不能超过 64 KiB");
        bytes.write(buffer, 0, count);
      }
      keyEditor.setText(bytes.toString(StandardCharsets.UTF_8.name()));
    } catch (Exception error) { ui.toast(error.getMessage()); }
  }

  void show(Profile original, boolean fresh) {
    if (isShowing()) return;
    Profile draft = Profile.from(original.json());
    draft.password = original.password;
    draft.privateKey = original.privateKey;
    draft.passphrase = original.passphrase;
    LinearLayout form = ui.column();
    form.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16));
    ScrollView sc = new ScrollView(activity);
    sc.addView(form);
    EditText name = ui.field(form, "连接名称", draft.name, "我的电脑", false),
        host = ui.field(form, "主机", draft.host, "192.168.1.10", false),
        port = ui.field(form, "端口", String.valueOf(draft.port), "22", false);
    port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    EditText user = ui.field(form, "用户名", draft.user, "用户名", false);
    RadioGroup auth = new RadioGroup(activity);
    auth.setOrientation(LinearLayout.VERTICAL);
    auth.setPadding(0, ui.dp(12), 0, ui.dp(4));
    RadioButton passwordAuth = new RadioButton(activity);
    AppIcons.selection(passwordAuth,ui.fg,ui.muted,true);
    passwordAuth.setId(R.id.auth_password);
    passwordAuth.setText("密码登录");
    passwordAuth.setTextColor(ui.fg);
    RadioButton keyAuth = new RadioButton(activity);
    AppIcons.selection(keyAuth,ui.fg,ui.muted,true);
    keyAuth.setId(R.id.auth_key);
    keyAuth.setText("私钥登录");
    keyAuth.setTextColor(ui.fg);
    auth.addView(passwordAuth);
    auth.addView(keyAuth);
    RadioButton tailscaleAuth = new RadioButton(activity);
    AppIcons.selection(tailscaleAuth,ui.fg,ui.muted,true);
    tailscaleAuth.setId(R.id.auth_tailscale);
    tailscaleAuth.setText("Tailscale SSH（免密码）");
    tailscaleAuth.setTextColor(ui.fg);
    auth.addView(tailscaleAuth);
    form.addView(auth);
    TextView networkHelp = ui.text("Tailscale：先在手机 Tailscale App 中连接同一网络，主机填 100.x 地址或完整 MagicDNS 名称。普通 SSH 仍选密码/私钥；仅启用 Tailscale SSH 的主机选免密码。", 13, ui.muted);
    form.addView(networkHelp);
    LinearLayout passwordSection = ui.column();
    form.addView(passwordSection);
    EditText password = ui.field(passwordSection, "密码", draft.password, "SSH 登录密码", true);
    LinearLayout keySection = ui.column();
    form.addView(keySection);
    EditText key = ui.field(keySection, "私钥", draft.privateKey, "粘贴 PEM / OpenSSH 私钥", true);
    key.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setMinLines(3);
    key.setMaxLines(5);
    keyEditor = key;
    keySection.addView(
        ui.action(
            "从文件导入私钥",
            () -> {
              Intent i =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .addCategory(Intent.CATEGORY_OPENABLE);
              activity.startActivityForResult(i, REQUEST_KEY);
            }));
    EditText passphrase = ui.field(keySection, "私钥口令", draft.passphrase, "可选", true);
    auth.setOnCheckedChangeListener(
        (group, checked) -> {
          passwordSection.setVisibility(checked == R.id.auth_password ? View.VISIBLE : View.GONE);
          keySection.setVisibility(checked == R.id.auth_key ? View.VISIBLE : View.GONE);
        });
    auth.check(draft.tailscaleSsh ? R.id.auth_tailscale : draft.privateKey.isEmpty() ? R.id.auth_password : R.id.auth_key);
    TextView advanced = ui.action("项目与启动设置 ⌄", () -> {});
    advanced.setText(AppIcons.label(activity, advanced.getText(), null, "chevronDown", ui.fg, ui.dp(18)));
    LinearLayout.LayoutParams advancedLayout = new LinearLayout.LayoutParams(-1, -2);
    advancedLayout.topMargin = ui.dp(12);
    form.addView(advanced, advancedLayout);
    LinearLayout extra = ui.column();
    extra.setVisibility(draft.cwd.isEmpty() ? View.GONE : View.VISIBLE);
    form.addView(extra);
    ui.clickable(
        advanced,
        () ->
            extra.setVisibility(extra.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    EditText cwd = ui.field(extra, "远端项目目录", draft.cwd, "/home/me/project（可选）", false),
        command = ui.field(extra, "远端启动命令", draft.command, "codex app-server", false);
    CheckBox persistent = new CheckBox(activity);
    AppIcons.selection(persistent,ui.fg,ui.muted,false);
    persistent.setText("远端持久会话（需 Python 3，断线后继续运行）");
    persistent.setTextColor(ui.fg);
    persistent.setChecked(draft.persistent);
    extra.addView(persistent);
    TextView note = ui.text("持久模式会在远端 ~/.remote-gpt 下安装会话托管程序。启动命令需提供 Codex App Server 兼容的标准输入 / 输出协议。远端需预先完成安装和登录。", 12, ui.muted);
    note.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
    extra.addView(note);
    CheckBox remember = new CheckBox(activity);
    AppIcons.selection(remember,ui.fg,ui.muted,false);
    remember.setText("在此设备加密保存凭据");
    remember.setTextColor(ui.fg);
    remember.setTextSize(13);
    remember.setChecked(draft.remember);
    form.addView(remember);
    TextView validation = ui.text("", 13, Color.rgb(181, 63, 49));
    LinearLayout footer = ui.column();
    footer.addView(validation);
    Dialog dialog = ui.profileWindow(fresh ? "添加 SSH 连接" : "编辑 SSH 连接", sc, footer, wide);
    this.dialog = dialog;
    dialog.setOnDismissListener(d -> keyEditor = null);
    java.util.function.Consumer<Boolean> persist =
        shouldConnect -> {
          try {
            draft.name = name.getText().toString().trim();
            draft.host = host.getText().toString().trim();
            draft.port = Integer.parseInt(port.getText().toString());
            draft.user = user.getText().toString().trim();
            boolean useKey = auth.getCheckedRadioButtonId() == R.id.auth_key;
            draft.tailscaleSsh = auth.getCheckedRadioButtonId() == R.id.auth_tailscale;
            draft.password = useKey || draft.tailscaleSsh ? "" : password.getText().toString();
            draft.privateKey = useKey ? key.getText().toString() : "";
            draft.passphrase = useKey ? passphrase.getText().toString() : "";
            draft.cwd = cwd.getText().toString().trim();
            draft.command = command.getText().toString().trim();
            draft.persistent = persistent.isChecked();
            draft.remember = remember.isChecked();
            draft.validate();
            if (shouldConnect && !draft.tailscaleSsh
                && (useKey ? draft.privateKey.trim().isEmpty() : draft.password.isEmpty()))
              throw new IllegalArgumentException(useKey ? "请先导入或粘贴私钥" : "请输入 SSH 登录密码");
            if (!app.storeProfile(draft)) {
              validation.setText(app.error);
              return;
            }
            dialog.dismiss();
            if (shouldConnect) actions.connect(draft);
            else {
              app.changed();
              ui.toast("连接已保存");
            }
          } catch (Exception ex) {
            validation.setText(ex instanceof NumberFormatException ? "请输入有效端口" : ex.getMessage());
          }
        };
    LinearLayout buttons = ui.row();
    footer.addView(buttons);
    TextView save = ui.action("保存", () -> persist.accept(false));
    buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
    TextView connect = ui.action("保存并连接", () -> persist.accept(true));
    connect.setTextColor(ui.bg);
    connect.setBackground(ui.rounded(ui.fg, 24));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
    cp.leftMargin = ui.dp(12);
    buttons.addView(connect, cp);
    if (!fresh) {
      form.addView(
          ui.action(
              "查看已信任的主机指纹",
              () -> {
                String target = original.host + ":" + original.port;
                String pin = app.vault.getPin(target);
                new AlertDialog.Builder(activity)
                    .setTitle("主机指纹")
                    .setMessage(target + "\n\n" + (pin == null ? "尚未信任此主机" : pin))
                    .setPositiveButton("关闭", null)
                    .setNeutralButton(
                        pin == null ? null : "重置主机信任",
                        (d, w) ->
                            new AlertDialog.Builder(activity)
                                .setTitle("重置此主机的信任？")
                                .setMessage("仅在确认服务器更换了 SSH 主机密钥时重置。下次连接必须重新核对指纹。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton(
                                    "重置",
                                    (confirm, which) -> {
                                      if (app.profile != null
                                          && (app.profile.host + ":" + app.profile.port)
                                              .equals(target)) app.disconnect();
                                      synchronized (app.vault) {
                                        app.vault.pins.remove(target);
                                        app.save();
                                      }
                                      ui.toast("下次连接将重新核对指纹");
                                    })
                                .show())
                    .show();
              }));
      form.addView(
          ui.action(
              "移除此连接",
              () ->
                  new AlertDialog.Builder(activity)
                      .setTitle("移除连接？")
                      .setMessage("仅删除此设备保存的连接和凭据，远端文件不受影响。")
                      .setNegativeButton("取消", null)
                      .setPositiveButton(
                          "移除",
                          (d, w) -> {
                            if (app.profile != null && app.profile.id.equals(original.id))
                              app.disconnect();
                            app.vault.profiles.remove(original);
                            app.save();
                            dialog.dismiss();
                            actions.removed();
                          })
                      .show()));
    }
  }

}
