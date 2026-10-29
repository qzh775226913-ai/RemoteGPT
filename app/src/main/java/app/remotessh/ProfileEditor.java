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
    EditText name = ui.field(form, UiText.get(R.string.ui_connection_name), draft.name, UiText.get(R.string.ui_my_computer), false),
        host = ui.field(form, UiText.get(R.string.ui_host), draft.host, "192.168.1.10", false),
        port = ui.field(form, UiText.get(R.string.ui_port), String.valueOf(draft.port), "22", false);
    port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    EditText user = ui.field(form, UiText.get(R.string.ui_username), draft.user, UiText.get(R.string.ui_username), false);
    RadioGroup auth = new RadioGroup(activity);
    auth.setOrientation(LinearLayout.VERTICAL);
    auth.setPadding(0, ui.dp(12), 0, ui.dp(4));
    RadioButton passwordAuth = new RadioButton(activity);
    AppIcons.selection(passwordAuth,ui.fg,ui.muted,true);
    passwordAuth.setId(R.id.auth_password);
    passwordAuth.setText(UiText.get(R.string.ui_password_authentication));
    passwordAuth.setTextColor(ui.fg);
    RadioButton keyAuth = new RadioButton(activity);
    AppIcons.selection(keyAuth,ui.fg,ui.muted,true);
    keyAuth.setId(R.id.auth_key);
    keyAuth.setText(UiText.get(R.string.ui_private_key_authentication));
    keyAuth.setTextColor(ui.fg);
    auth.addView(passwordAuth);
    auth.addView(keyAuth);
    RadioButton tailscaleAuth = new RadioButton(activity);
    AppIcons.selection(tailscaleAuth,ui.fg,ui.muted,true);
    tailscaleAuth.setId(R.id.auth_tailscale);
    tailscaleAuth.setText(UiText.get(R.string.ui_tailscale_ssh_passwordless));
    tailscaleAuth.setTextColor(ui.fg);
    auth.addView(tailscaleAuth);
    form.addView(auth);
    TextView networkHelp = ui.text(UiText.get(R.string.ui_tailscale_connect_your_phone_to_the_same_network_in_the_tailscale), 13, ui.muted);
    form.addView(networkHelp);
    LinearLayout passwordSection = ui.column();
    form.addView(passwordSection);
    EditText password = ui.field(passwordSection, UiText.get(R.string.ui_password), draft.password, UiText.get(R.string.ui_ssh_login_password), true);
    LinearLayout keySection = ui.column();
    form.addView(keySection);
    EditText key = ui.field(keySection, UiText.get(R.string.ui_private_key), draft.privateKey, UiText.get(R.string.ui_paste_pem_openssh_private_key), true);
    key.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setMinLines(3);
    key.setMaxLines(5);
    keyEditor = key;
    keySection.addView(
        ui.action(
            UiText.get(R.string.ui_import_private_key_from_file),
            () -> {
              Intent i =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .addCategory(Intent.CATEGORY_OPENABLE);
              activity.startActivityForResult(i, REQUEST_KEY);
            }));
    EditText passphrase = ui.field(keySection, UiText.get(R.string.ui_key_passphrase), draft.passphrase, UiText.get(R.string.ui_optional), true);
    auth.setOnCheckedChangeListener(
        (group, checked) -> {
          passwordSection.setVisibility(checked == R.id.auth_password ? View.VISIBLE : View.GONE);
          keySection.setVisibility(checked == R.id.auth_key ? View.VISIBLE : View.GONE);
        });
    auth.check(draft.tailscaleSsh ? R.id.auth_tailscale : draft.privateKey.isEmpty() ? R.id.auth_password : R.id.auth_key);
    TextView advanced = ui.action(UiText.get(R.string.ui_project_and_startup_settings), () -> {});
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
    EditText cwd = ui.field(extra, UiText.get(R.string.ui_remote_project_directory), draft.cwd, UiText.get(R.string.ui_home_me_project_optional), false),
        command = ui.field(extra, UiText.get(R.string.ui_remote_startup_command), draft.command, "codex app-server", false);
    CheckBox persistent = new CheckBox(activity);
    AppIcons.selection(persistent,ui.fg,ui.muted,false);
    persistent.setText(UiText.get(R.string.ui_persistent_remote_sessions_requires_python_3_continue_after_disco));
    persistent.setTextColor(ui.fg);
    persistent.setChecked(draft.persistent);
    extra.addView(persistent);
    TextView note = ui.text(UiText.get(R.string.ui_persistent_mode_installs_a_session_manager_in_remote_gpt_on_the_h), 12, ui.muted);
    note.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
    extra.addView(note);
    CheckBox remember = new CheckBox(activity);
    AppIcons.selection(remember,ui.fg,ui.muted,false);
    remember.setText(UiText.get(R.string.ui_save_encrypted_credentials_on_this_device));
    remember.setTextColor(ui.fg);
    remember.setTextSize(13);
    remember.setChecked(draft.remember);
    form.addView(remember);
    TextView validation = ui.text("", 13, Color.rgb(181, 63, 49));
    LinearLayout footer = ui.column();
    footer.addView(validation);
    Dialog dialog = ui.profileWindow(fresh ? UiText.get(R.string.ui_add_ssh_connection_211) : UiText.get(R.string.ui_edit_ssh_connection), sc, footer, wide);
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
            try { draft.validate(); }
            catch (IllegalArgumentException invalid) { validation.setText(UiPresentation.profileValidation(invalid)); return; }
            if (shouldConnect && !draft.tailscaleSsh
                && (useKey ? draft.privateKey.trim().isEmpty() : draft.password.isEmpty()))
              throw new IllegalArgumentException(useKey ? UiText.get(R.string.ui_import_or_paste_a_private_key_first) : UiText.get(R.string.ui_enter_the_ssh_login_password));
            if (!app.storeProfile(draft)) {
              validation.setText(app.error);
              return;
            }
            dialog.dismiss();
            if (shouldConnect) actions.connect(draft);
            else {
              app.changed();
              ui.toast(UiText.get(R.string.ui_connection_saved));
            }
          } catch (Exception ex) {
            validation.setText(ex instanceof NumberFormatException ? UiText.get(R.string.ui_enter_a_valid_port) : ex.getMessage());
          }
        };
    LinearLayout buttons = ui.row();
    footer.addView(buttons);
    TextView save = ui.action(UiText.get(R.string.ui_save), () -> persist.accept(false));
    buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
    TextView connect = ui.action(UiText.get(R.string.ui_save_and_connect), () -> persist.accept(true));
    connect.setTextColor(ui.bg);
    connect.setBackground(ui.rounded(ui.fg, 24));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
    cp.leftMargin = ui.dp(12);
    buttons.addView(connect, cp);
    if (!fresh) {
      form.addView(
          ui.action(
              UiText.get(R.string.ui_view_trusted_host_fingerprint),
              () -> {
                String target = original.host + ":" + original.port;
                String pin = app.vault.getPin(target);
                new AlertDialog.Builder(activity)
                    .setTitle(UiText.get(R.string.ui_host_fingerprint_220))
                    .setMessage(target + "\n\n" + (pin == null ? UiText.get(R.string.ui_this_host_is_not_trusted_yet) : pin))
                    .setPositiveButton(UiText.get(R.string.ui_close), null)
                    .setNeutralButton(
                        pin == null ? null : UiText.get(R.string.ui_reset_host_trust),
                        (d, w) ->
                            new AlertDialog.Builder(activity)
                                .setTitle(UiText.get(R.string.ui_reset_trust_for_this_host))
                                .setMessage(UiText.get(R.string.ui_reset_only_after_confirming_the_server_s_ssh_host_key_has_changed))
                                .setNegativeButton(UiText.get(R.string.ui_cancel), null)
                                .setPositiveButton(
                                    UiText.get(R.string.ui_reset),
                                    (confirm, which) -> {
                                      if (app.profile != null
                                          && (app.profile.host + ":" + app.profile.port)
                                              .equals(target)) app.disconnect();
                                      synchronized (app.vault) {
                                        app.vault.pins.remove(target);
                                        app.save();
                                      }
                                      ui.toast(UiText.get(R.string.ui_the_fingerprint_will_be_checked_on_the_next_connection));
                                    })
                                .show())
                    .show();
              }));
      LinearLayout.LayoutParams removeLayout = new LinearLayout.LayoutParams(-1, -2);
      removeLayout.topMargin = ui.dp(12);
      form.addView(
          ui.action(
              UiText.get(R.string.ui_remove_this_connection),
              () ->
                  new AlertDialog.Builder(activity)
                      .setTitle(UiText.get(R.string.ui_remove_connection))
                      .setMessage(UiText.get(R.string.ui_only_the_connection_and_credentials_saved_on_this_device_are_remo))
                      .setNegativeButton(UiText.get(R.string.ui_cancel), null)
                      .setPositiveButton(
                          UiText.get(R.string.ui_remove),
                          (d, w) -> {
                            if (app.profile != null && app.profile.id.equals(original.id))
                              app.disconnect();
                            app.vault.profiles.remove(original);
                            app.save();
                            dialog.dismiss();
                            actions.removed();
                          })
                      .show()), removeLayout);
    }
  }

}
