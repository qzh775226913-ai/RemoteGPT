package app.remotessh;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Reference-inspired host chips, project list, project threads and search. */
final class RemoteBrowser {
  interface Actions {
    void connect(Profile p);

    default void version() {}

    void add();

    void settings();

    default void edit() {}

    void directories();

    void diagnostics();

    void chat();
  }

  private final RemoteApp app;
  private final RemoteScreens ui;
  private final Actions actions;
  private final Dialog dialog;
  private final LinearLayout list, hosts;
  private final TextView heading, subtitle, primary;
  private final EditText search;
  private String filter = "", project = "";
  private String renderedSignature = "";

  private boolean centerHost = true;

  RemoteBrowser(Activity activity, RemoteApp app, boolean dark, Actions actions) {
    this.app = app;
    this.ui = new RemoteScreens(activity, dark);
    this.actions = actions;
    LinearLayout root = ui.column();
    root.setContentDescription("远程控制项目列表");
    dialog = ui.window(root);
    LinearLayout header = ui.row();
    header.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(16));
    header.addView(
        ui.icon(
            "back",
            "返回",
            () -> {
              if (project.isEmpty()) dialog.dismiss();
              else {
                project = "";
                app.historyProject = "";
                if (app.connected) app.refreshThreads(false);
                refresh();
              }
            }),
        new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
    heading = ui.heading(header, "远程控制");
    IconView menu =
        ui.icon(
            "more",
            "远程控制菜单",
            this::showMenu);
    header.addView(menu, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
    root.addView(header);
    HorizontalScrollView scrollHosts = new HorizontalScrollView(activity);
    scrollHosts.setHorizontalScrollBarEnabled(false);
    hosts = ui.row();
    hosts.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(18));
    scrollHosts.addView(hosts);
    root.addView(scrollHosts);
    subtitle = ui.text("", 13);
    subtitle.setTextColor(ui.muted);
    subtitle.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(12));
    subtitle.setContentDescription("远端版本与更新");
    subtitle.setOnClickListener(v -> { if (app.connected) actions.version(); });
    root.addView(subtitle);
    ScrollView scroll = new ScrollView(activity);
    list = ui.column();
    list.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(16));
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = ui.row();
    footer.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(20));
    search = new EditText(activity);
    search.setTextSize(16);
    search.setTextColor(ui.fg);
    search.setHintTextColor(ui.muted);
    search.setHint("搜索项目和对话");
    search.setContentDescription("搜索项目和对话");
    search.setSingleLine(true);
    search.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
    search.setBackground(ui.rounded(ui.panel));
    footer.addView(search, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
    primary =
        ui.button(
            "新项目",
            true,
            () -> {
              if (project.isEmpty()) actions.directories();
              else {
                if (!app.canSwitchConversation()) {
                  Toast.makeText(activity, "正在恢复会话或上传附件，请稍候", Toast.LENGTH_SHORT).show();
                  return;
                }
                if (app.useProject(project)) {
                  dialog.dismiss();
                  actions.chat();
                }
              }
            });
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, ui.dp(54));
    pp.leftMargin = ui.dp(10);
    footer.addView(primary, pp);
    root.addView(footer);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            refreshList();
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  String[] menuLabels() {
    return project.isEmpty()
        ? new String[] {"添加连接", "编辑当前连接", "刷新项目与对话", "设置"}
        : new String[] {"在此项目新建对话", "刷新项目对话", "复制项目路径"};
  }

  private void showMenu() {
    new AlertDialog.Builder(ui.activity).setTitle(project.isEmpty() ? "远程控制" : "项目")
        .setItems(menuLabels(), (d, w) -> {
          if (!project.isEmpty()) {
            if (w == 0) {
              if (!app.canSwitchConversation()) { Toast.makeText(ui.activity, "正在恢复会话或上传附件，请稍候", Toast.LENGTH_SHORT).show(); return; }
              if (app.useProject(project)) { dialog.dismiss(); actions.chat(); }
            } else if (w == 1) app.refreshThreads(false);
            else {
              ((android.content.ClipboardManager) ui.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                  .setPrimaryClip(android.content.ClipData.newPlainText("项目路径", project));
              Toast.makeText(ui.activity, "已复制项目路径", Toast.LENGTH_SHORT).show();
            }
          } else {
            if (w == 0) actions.add();
            else if (w == 1) actions.edit();
            else if (w == 2) app.refreshThreads(false);
            else actions.settings();
          }
        }).show();
  }

  void show() {
    ui.show(dialog);
    renderedSignature = "";
    if (!app.historyProject.isEmpty()) {
      app.historyProject = "";
      if (app.connected) app.refreshThreads(false);
    }
    filter = app.profile == null ? (app.vault.profiles.isEmpty() ? "" : app.vault.profiles.get(0).id) : app.profile.id;
    refresh();
  }

  boolean isShowing() {
    return dialog.isShowing();
  }

  void dismiss() {
    dialog.dismiss();
  }

  void openProject(String path) {
    project = path;
    filter = app.profile == null ? "" : app.profile.id;
    app.historyProject = path;
    if (app.connected) app.refreshThreads(false);
    refresh();
  }

  void refresh() {
    if (!isShowing()) return;
    subtitle.setText(
        app.connecting || app.recoveryInProgress()
            ? (app.profile==null?"":app.profile.title()+" · ")+ConnectionService.summary(app)
            : app.connected
                ? app.profile.title()+" · 已连接 · "+app.remoteVersion.label()+(project.isEmpty()?"":"\n"+project)
                : "选择一台电脑，查看项目和对话");
    String signature = signature();
    if (signature.equals(renderedSignature)) return;
    renderedSignature = signature;
    heading.setText(project.isEmpty() ? "远程控制" : RemoteDirectory.name(project));
    heading.setMaxLines(1);
    heading.setEllipsize(TextUtils.TruncateAt.END);
    primary.setText(project.isEmpty() ? "新项目" : "聊天");
    ((View)hosts.getParent()).setVisibility(project.isEmpty()?View.VISIBLE:View.GONE);
    hosts.removeAllViews();
    for (Profile p : app.vault.profiles) {
      boolean active = app.connected && app.profile != null && p.id.equals(app.profile.id);
      TextView chip =
          ui.button(
              (active ? "● " : "○ ") + p.title(),
              filter.equals(p.id),
              () -> {
                centerHost = true;
                filter = p.id;
                project = "";
                if (active) {
                  app.historyProject = "";
                  app.refreshThreads(false);
                } else {
                  app.historyProject = "";
                  actions.connect(p);
                }
                refresh();
              });
      chip.setText(AppIcons.label(ui.activity,chip.getText().toString(),active?"status":"radio",null,filter.equals(p.id)?android.graphics.Color.WHITE:ui.fg,ui.dp(16)));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.leftMargin = ui.dp(8);
      hosts.addView(chip, lp);
      if (centerHost && filter.equals(p.id)) {
        centerHost = false;
        chip.post(() -> {
          HorizontalScrollView scroll = (HorizontalScrollView) hosts.getParent();
          scroll.scrollTo(Math.max(0, chip.getLeft() + chip.getWidth() / 2 - scroll.getWidth() / 2), 0);
        });
      }
    }
    refreshList();
  }

  private String signature() {
    StringBuilder value = new StringBuilder(filter).append('|').append(project).append('|')
        .append(app.connected).append('|').append(app.connecting).append('|')
        .append(app.profile == null ? "" : app.profile.id).append('|')
        .append(app.remoteVersion.label()).append('|').append(app.nextCursor).append('|').append(app.canSwitchConversation());
    for (Profile p : app.vault.profiles)
      value.append("|p:").append(p.id).append(':').append(p.name).append(':').append(p.cwd)
          .append(':').append(p.projects.hashCode());
    for (int i = 0; i < app.threads.length(); i++) {
      JSONObject thread = app.threads.optJSONObject(i);
      if (thread != null) value.append("|t:").append(thread.optString("id"))
          .append(':').append(thread.optString("name")).append(':').append(thread.optString("preview"))
          .append(':').append(thread.optString("cwd")).append(':').append(thread.optLong("updatedAt"))
          .append(':').append(app.threadRunning(thread));
    }
    return value.toString();
  }

  private boolean matches(String text) {
    return text.toLowerCase(Locale.ROOT)
        .contains(search.getText().toString().trim().toLowerCase(Locale.ROOT));
  }

  private void section(String title) {
    TextView label = ui.text(title, 21);
    label.setPadding(0, ui.dp(12), 0, ui.dp(16));
    list.addView(label);
  }

  private void refreshList() {
    list.removeAllViews();
    if (project.isEmpty()) {
      section("项目");
      int count = 0;
      for (Profile p : app.vault.profiles) {
        if (!filter.equals(p.id)) continue;
        LinkedHashSet<String> paths = new LinkedHashSet<>(p.projects);
        if (!p.cwd.isEmpty()) paths.add(p.cwd);
        for (String path : paths) {
          if (!matches(path)) continue;
          count++;
          LinearLayout row = ui.row();
          row.setPadding(0, ui.dp(4), 0, ui.dp(4));
          IconView folder = new IconView(ui.activity, "folder", ui.fg);
          row.addView(folder, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(48)));
          TextView name = ui.text(RemoteDirectory.name(path), 20);
          name.setPadding(ui.dp(8), 0, 0, 0);
          name.setMaxLines(1);
          name.setEllipsize(TextUtils.TruncateAt.END);
          row.addView(name, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
          name.setGravity(Gravity.CENTER_VERTICAL);
          ui.clickable(
              row,
              () -> {
                if (app.connected && app.profile.id.equals(p.id)) {
                  openProject(path);
                } else {
                  actions.connect(p);
                  Toast.makeText(ui.activity, "连接此电脑后再打开项目", Toast.LENGTH_SHORT).show();
                }
              });
          list.addView(row);
        }
        if (paths.isEmpty())
          list.addView(ui.button("连接 " + p.title(), false, () -> actions.connect(p)));
      }
      if (count == 0) {
        TextView note = ui.text("选择远端文件夹，或新建一个项目。", 14);
        note.setTextColor(ui.muted);
        note.setPadding(0, 0, 0, ui.dp(12));
        list.addView(note);
        list.addView(ui.button("选择路径 / 新建项目", false, actions::directories));
      }
      section("最近");
    }
    int count = 0;
    if (app.connected && filter.equals(app.profile.id))
      for (int i = 0; i < app.threads.length(); i++) {
        JSONObject thread = app.threads.optJSONObject(i);
        if (thread == null) continue;
        if (!project.isEmpty() && !thread.optString("cwd").equals(project)) continue;
        String name = thread.isNull("name") ? "" : thread.optString("name");
        if (name.trim().isEmpty()) name = thread.optString("preview", "新对话");
        if (!matches(name)) continue;
        count++;
        LinearLayout row = ui.row();
        TextView text = ui.text(name.isEmpty() ? "新对话" : name, 18);
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setPadding(0, ui.dp(16), ui.dp(12), ui.dp(16));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        long age =
            Math.max(
                0,
                System.currentTimeMillis() / 1000
                    - thread.optLong("updatedAt", System.currentTimeMillis() / 1000));
        TextView time =
            ui.text(
                age < 3600 ? "刚刚" : age < 86400 ? (age / 3600) + "小时" : (age / 86400) + "天", 13);
        time.setTextColor(ui.muted);
        if (app.threadRunning(thread)) {
          ProgressBar running = new ProgressBar(ui.activity);
          running.setIndeterminate(true);
          running.setContentDescription("对话正在运行");
          LinearLayout.LayoutParams progress = new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20));
          progress.setMargins(0, 0, ui.dp(10), 0);
          row.addView(running, progress);
        }
        row.addView(time);
        String id = thread.optString("id");
        ui.clickable(
            row,
            () -> {
              if (!app.canSwitchConversation()) {
                app.openConversation(id);
                Toast.makeText(ui.activity, "正在恢复会话，完成后自动打开", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
              }
              app.viewConversation(id);
              dialog.dismiss();
            });
        list.addView(row);
      }
    if (count == 0) {
      TextView note = ui.text(project.isEmpty() ? "暂无对话" : "没有更多线程", 16);
      note.setTextColor(ui.muted);
      note.setGravity(Gravity.CENTER);
      note.setPadding(0, ui.dp(24), 0, ui.dp(24));
      list.addView(note);
    }
    if (app.connected)
      list.addView(
          ui.button(
              app.nextCursor.isEmpty() ? "刷新" : "加载更多",
              false,
              () -> app.refreshThreads(!app.nextCursor.isEmpty())));
    if (!app.connected && app.vault.profiles.isEmpty())
      list.addView(ui.button("添加 SSH 连接", true, actions::add));
  }
}
