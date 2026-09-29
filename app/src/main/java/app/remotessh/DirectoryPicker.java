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
    root.setContentDescription("远端目录选择器");
    dialog = ui.window(root);
    LinearLayout header = ui.row();
    header.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
    header.addView(
        ui.icon("back", "返回", dialog::dismiss),
        new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
    ui.heading(header, "选择项目路径");
    root.addView(header);
    pathLabel = ui.text("", 14);
    pathLabel.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
    pathLabel.setTextIsSelectable(true);
    root.addView(pathLabel);
    LinearLayout toolbar = ui.row();
    toolbar.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(12));
    up = ui.button("上一级", false, () -> load(RemoteDirectory.parent(directory.path)));
    toolbar.addView(up, new LinearLayout.LayoutParams(0, -2, 1));
    toolbar.addView(
        ui.button("输入路径", false, this::enterPath), new LinearLayout.LayoutParams(0, -2, 1));
    create = ui.button("新建项目", true, this::newProject);
    toolbar.addView(create, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(toolbar);
    status = ui.text("正在读取目录…", 14);
    status.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
    root.addView(status);
    ScrollView scroll = new ScrollView(activity);
    list = ui.column();
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = ui.column();
    footer.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(20));
    choose =
        ui.button(
            "使用此文件夹",
            true,
            () -> {
              if (directory != null) {
                selected.accept(directory.path);
                dialog.dismiss();
              }
            });
    footer.addView(choose);
    root.addView(footer);
    dialog.setOnDismissListener(d -> request++);
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
    status.setText("正在读取目录…");
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
          status.setText("无法读取此路径，点按查看原因");
          ui.clickable(
              status,
              () ->
                  new AlertDialog.Builder(ui.activity)
                      .setTitle("浏览目录失败")
                      .setMessage(error)
                      .setPositiveButton("关闭", null)
                      .show());
          list.addView(ui.button("返回主目录", false, () -> load("")));
          list.addView(ui.button("重试", false, () -> load(path)));
        });
  }

  private void display(RemoteDirectory result) {
    directory = result;
    pathLabel.setText(result.path);
    list.removeAllViews();
    clearStatusAction();
    status.setText(
        result.truncated
            ? "仅显示前 1000 个目录，可输入完整路径直达"
            : result.directories.isEmpty() ? "此文件夹中没有子目录" : "");
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
    input.setContentDescription("远端绝对路径");
    new AlertDialog.Builder(ui.activity)
        .setTitle("输入远端路径")
        .setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("打开", (d, w) -> load(input.getText().toString().trim()))
        .show();
  }

  private void newProject() {
    if (directory == null) return;
    EditText name = new EditText(ui.activity);
    name.setSingleLine(true);
    name.setHint("项目名称");
    name.setContentDescription("新项目名称");
    String parent = directory.path;
    AlertDialog prompt =
        new AlertDialog.Builder(ui.activity)
            .setTitle("新建项目")
            .setMessage("在以下目录新建文件夹：\n" + parent)
            .setView(name)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建", null)
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
                        name.setError(e.getMessage());
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
