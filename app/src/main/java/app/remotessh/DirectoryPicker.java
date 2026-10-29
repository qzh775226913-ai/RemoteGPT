package app.remotessh;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** SFTP directory picker. Only an explicit create action can mutate the remote filesystem. */
final class DirectoryPicker {
  private final RemoteApp app;
  private final RemoteScreens ui;
  private final Dialog dialog;
  private final LinearLayout list;
  private final TextView pathLabel, status, choose, up, create;
  private final Consumer<String> selected;
  private RemoteDirectory directory;
  private int request;

  DirectoryPicker(Activity activity, RemoteApp app, boolean dark, Consumer<String> selected) {
    this.app = app;
    this.ui = new RemoteScreens(activity, dark);
    this.selected = selected;
    LinearLayout root = ui.column();
    root.setContentDescription(UiText.get(R.string.ui_remote_directory_picker));
    dialog = ui.window(root);
    LinearLayout header = ui.row();
    header.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
    header.addView(
        ui.icon("back", UiText.get(R.string.ui_back), dialog::dismiss),
        new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
    ui.heading(header, UiText.get(R.string.ui_choose_project_path));
    root.addView(header);
    pathLabel = ui.text("", 14);
    pathLabel.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
    pathLabel.setTextIsSelectable(true);
    root.addView(pathLabel);
    LinearLayout toolbar = ui.row();
    toolbar.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(12));
    up = button(UiText.get(R.string.ui_parent_directory), false, () -> load(RemoteDirectory.parent(directory.path)));
    LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(0, -2, 1);
    upParams.setMarginEnd(ui.dp(12));
    toolbar.addView(up, upParams);
    toolbar.addView(
        button(UiText.get(R.string.ui_enter_path), false, this::enterPath), new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(toolbar);
    status = ui.text(UiText.get(R.string.ui_loading_directory), 14);
    status.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
    root.addView(status);
    ScrollView scroll = new ScrollView(activity);
    list = ui.column();
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = ui.row();
    footer.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(20));
    create = button(UiText.get(R.string.ui_create_project), false, this::newProject);
    LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, -2, 1);
    createParams.setMarginEnd(ui.dp(12));
    footer.addView(create, createParams);
    choose =
        button(
            UiText.get(R.string.ui_use_this_folder),
            true,
            () -> {
              if (directory != null) {
                selected.accept(directory.path);
                dialog.dismiss();
              }
            });
    footer.addView(choose, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(footer);
    dialog.setOnDismissListener(d -> request++);
  }

  private TextView button(String label, boolean primary, Runnable click) {
    TextView view = ui.button(label, primary, click);
    view.setMaxLines(1);
    view.setEllipsize(android.text.TextUtils.TruncateAt.END);
    view.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12));
    return view;
  }

  void show(String initial) {
    ui.show(dialog);
    load(initial);
  }

  void dismiss() {
    dialog.dismiss();
  }

  private void loading() {
    list.removeAllViews();
    clearStatusAction();
    status.setText(UiText.get(R.string.ui_loading_directory));
    status.setVisibility(View.VISIBLE);
    choose.setEnabled(false);
    choose.setAlpha(.4f);
    up.setEnabled(false);
    create.setEnabled(false);
  }

  private void load(String path) {
    int token = ++request;
    loading();
    app.browseDirectory(
        path,
        d -> {
          if (token != request || !dialog.isShowing()) return;
          display(d);
        },
        error -> {
          if (token != request || !dialog.isShowing()) return;
          status.setText(UiText.get(R.string.ui_cannot_read_this_path_tap_for_details));
          ui.clickable(
              status,
              () ->
                  new AlertDialog.Builder(ui.activity)
                      .setTitle(UiText.get(R.string.ui_cannot_browse_directory))
                      .setMessage(error)
                      .setPositiveButton(UiText.get(R.string.ui_close), null)
                      .show());
          list.addView(ui.button(UiText.get(R.string.ui_go_to_home_directory), false, () -> load("")));
          list.addView(ui.button(UiText.get(R.string.ui_retry), false, () -> load(path)));
        });
  }

  private void display(RemoteDirectory result) {
    directory = result;
    pathLabel.setText(result.path);
    list.removeAllViews();
    clearStatusAction();
    status.setText(
        result.truncated
            ? UiText.get(R.string.ui_only_the_first_1_000_directories_are_shown_enter_a_full_path_to_g)
            : result.directories.isEmpty() ? UiText.get(R.string.ui_this_folder_has_no_subdirectories) : "");
    status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    choose.setEnabled(true);
    choose.setAlpha(1);
    up.setEnabled(!result.path.equals("/"));
    create.setEnabled(true);
    for (String name : result.directories) {
      LinearLayout row = ui.row();
      row.setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(6));
      IconView folder = new IconView(ui.activity, "folder", ui.fg);
      row.addView(folder, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(48)));
      TextView title = ui.text(name, 18);
      title.setPadding(ui.dp(8), 0, 0, 0);
      row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      ui.clickable(row, () -> load(RemoteDirectory.child(result.path, name)));
      list.addView(row);
    }
  }

  private void clearStatusAction() {
    status.setOnClickListener(null);
    status.setClickable(false);
    status.setFocusable(false);
    status.setForeground(null);
  }

  private void enterPath() {
    EditText input = new EditText(ui.activity);
    input.setSingleLine(true);
    input.setText(directory == null ? "/" : directory.path);
    input.setContentDescription(UiText.get(R.string.ui_absolute_remote_path));
    new AlertDialog.Builder(ui.activity)
        .setTitle(UiText.get(R.string.ui_enter_remote_path))
        .setView(input)
        .setNegativeButton(UiText.get(R.string.ui_cancel), null)
        .setPositiveButton(UiText.get(R.string.ui_open), (d, w) -> load(input.getText().toString().trim()))
        .show();
  }

  private void newProject() {
    if (directory == null) return;
    EditText name = new EditText(ui.activity);
    name.setSingleLine(true);
    name.setHint(UiText.get(R.string.ui_project_name));
    name.setContentDescription(UiText.get(R.string.ui_new_project_name));
    String parent = directory.path;
    AlertDialog prompt =
        new AlertDialog.Builder(ui.activity)
            .setTitle(UiText.get(R.string.ui_create_project))
            .setMessage(UiText.get(R.string.ui_create_a_folder_in) + parent)
            .setView(name)
            .setNegativeButton(UiText.get(R.string.ui_cancel), null)
            .setPositiveButton(UiText.get(R.string.ui_create), null)
            .create();
    prompt.setOnShowListener(
        d ->
            prompt
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String value = name.getText().toString().trim();
                      try {
                        RemoteDirectory.child(parent, value);
                      } catch (Exception e) {
                        name.setError(e instanceof IllegalArgumentException ? UiText.get(R.string.ui_invalid_project_name) : e.getMessage());
                        return;
                      }
                      prompt.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                      int token = ++request;
                      app.createProject(
                          parent,
                          value,
                          result -> {
                            if (token != request || !dialog.isShowing()) return;
                            prompt.dismiss();
                            display(result);
                          },
                          error -> {
                            if (token != request || !dialog.isShowing()) return;
                            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            name.setError(error);
                          });
                    }));
    prompt.show();
  }
}
