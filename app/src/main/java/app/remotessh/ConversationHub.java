package app.remotessh;

import android.app.*;
import android.content.*;
import java.util.*;
import org.json.*;

/** Header menu for conversation facts and read-only remote capabilities. */
final class ConversationHub {
  private final Activity activity;
  private final RemoteApp app;
  ConversationHub(Activity activity, RemoteApp app) { this.activity = activity; this.app = app; }

  void show() {
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_conversation_information))
        .setItems(new String[] {UiText.get(R.string.ui_current_project_and_conversation),
            UiText.get(R.string.ui_available_skills), UiText.get(R.string.ui_mcp_servers)},
            (d, which) -> {
              if (which == 0) details();
              else if (which == 1) skills();
              else mcp();
            }).show();
  }

  private void details() {
    String project = app.currentProject();
    String text = UiText.get(R.string.ui_project_137)
        + (project.isEmpty() ? UiText.get(R.string.ui_remote_default_directory_no_path_returned) : project)
        + UiText.get(R.string.ui_conversation_139)
        + (app.threadTitle.isEmpty() ? UiText.get(R.string.ui_new_conversation) : app.threadTitle)
        + UiText.get(R.string.ui_conversation_id)
        + (app.threadId.isEmpty() ? UiText.get(R.string.ui_created_after_sending_first_message) : app.threadId)
        + UiText.get(R.string.ui_status)
        + (!app.connected ? UiText.get(R.string.ui_not_connected) : app.readOnly ? UiText.get(R.string.ui_read_only_try_resuming)
            : app.busy ? UiText.get(R.string.ui_processing) : UiText.get(R.string.ui_ready_to_continue))
        + "\n" + UiText.get(R.string.ui_detail_model, ModelOptions.displayName(app.models, app.model))
        + "\n" + UiText.get(R.string.ui_detail_effort, app.reasoningEffort.isEmpty() ? UiText.get(R.string.ui_default) : ModelOptions.label(app.reasoningEffort))
        + "\n" + UiText.get(R.string.ui_detail_permission, UiPresentation.permission(app.displayedPermission()))
        + "\n" + UiText.get(R.string.ui_detail_speed, app.serviceTierSummary().isEmpty() ? UiText.get(R.string.ui_default) : app.serviceTierSummary())
        + "\n" + UiText.get(R.string.ui_detail_remote_version, app.remoteVersion.label());
    new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_current_project_and_conversation))
        .setMessage(text).setPositiveButton(UiText.get(R.string.ui_close), null)
        .setNeutralButton(UiText.get(R.string.ui_copy), (d,w) -> {
          ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
          clipboard.setPrimaryClip(ClipData.newPlainText(UiText.get(R.string.ui_current_project_and_conversation), text));
        }).show();
  }

  private void skills() {
    if (!app.connected) { unavailable(); return; }
    String cwd = app.currentProject();
    if (cwd.isEmpty()) { unavailable(); return; }
    CapabilityDirectoryDialog dialog = new CapabilityDirectoryDialog(activity,
        UiText.get(R.string.ui_available_skills), UiText.get(R.string.ui_no_skills_available));
    app.request("skills/list", Json.obj("cwds", new JSONArray().put(cwd)), result -> {
      if (!dialog.isShowing()) return;
      if (!cwd.equals(app.currentProject())) { dialog.dismiss(); return; }
      List<CapabilityDirectoryDialog.Entry> entries = new ArrayList<>();
      JSONArray groups = result.optJSONArray("data");
      if (groups != null) for (int i=0;i<groups.length();i++) {
        JSONObject group = groups.optJSONObject(i);
        if (group == null) continue;
        JSONArray list = group.optJSONArray("skills");
        if (list != null) for (int n=0;n<list.length();n++) {
          JSONObject item = list.optJSONObject(n);
          if (item == null) continue;
          String name = item.optString("name");
          if (name.isEmpty()) continue;
          boolean enabled = item.optBoolean("enabled", true);
          StringBuilder detail = new StringBuilder();
          if (!enabled) detail.append(UiText.get(R.string.ui_disabled));
          String description = item.optString("description");
          if (!description.isEmpty()) { if (detail.length()>0) detail.append("\n\n"); detail.append(description); }
          String path = item.isNull("path") ? "" : item.optString("path");
          if (!path.isEmpty()) detail.append("\n\n").append(path);
          entries.add(new CapabilityDirectoryDialog.Entry(name,
              UiText.get(enabled ? R.string.ui_enabled : R.string.ui_disabled), detail.toString()));
        }
        JSONArray errors = group.optJSONArray("errors");
        if (errors != null) for (int n=0;n<errors.length();n++) {
          JSONObject problem = errors.optJSONObject(n);
          if (problem != null && !problem.optString("message").isEmpty()) {
            String message = problem.optString("message");
            String path = problem.optString("path");
            String name = path.isEmpty() ? UiText.get(R.string.ui_skill_errors) :
                path.substring(path.lastIndexOf('/') + 1);
            entries.add(new CapabilityDirectoryDialog.Entry(name,
                UiText.get(R.string.ui_skill_errors), message + (path.isEmpty() ? "" : "\n\n" + path)));
          }
        }
      }
      entries.sort(Comparator.comparingInt((CapabilityDirectoryDialog.Entry e) ->
          e.group.equals(UiText.get(R.string.ui_enabled)) ? 0 : e.group.equals(UiText.get(R.string.ui_disabled)) ? 1 : 2)
          .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
      dialog.showEntries(entries);
    }, error -> dialog.showError());
  }

  private void mcp() {
    if (!app.connected) { unavailable(); return; }
    CapabilityDirectoryDialog dialog = new CapabilityDirectoryDialog(activity,
        UiText.get(R.string.ui_mcp_servers), UiText.get(R.string.ui_no_mcp_servers));
    JSONArray entries = new JSONArray();
    loadMcpPage(dialog, entries, "", 0, app.threadId);
  }

  private void loadMcpPage(CapabilityDirectoryDialog dialog, JSONArray entries, String cursor, int page, String threadId) {
    JSONObject params = Json.obj("limit", 50, "detail", "toolsAndAuthOnly");
    try {
      if (!cursor.isEmpty()) params.put("cursor", cursor);
      if (!threadId.isEmpty()) params.put("threadId", threadId);
    } catch (JSONException ignored) {}
    app.request("mcpServerStatus/list", params, result -> {
      if (!dialog.isShowing()) return;
      if (!threadId.equals(app.threadId)) { dialog.dismiss(); return; }
      JSONArray data = result.optJSONArray("data");
      if (data != null) for (int i=0;i<data.length();i++) entries.put(data.opt(i));
      String next = result.isNull("nextCursor") ? "" : result.optString("nextCursor");
      if (!next.isEmpty() && page < 19 && !next.equals(cursor)) { loadMcpPage(dialog, entries, next, page+1, threadId); return; }
      List<CapabilityDirectoryDialog.Entry> listed = new ArrayList<>();
      for (int i=0;i<entries.length();i++) {
        JSONObject server = entries.optJSONObject(i);
        if (server == null) continue;
        String name = server.optString("name");
        if (name.isEmpty()) continue;
        String status = server.optString("runtimeStatus");
        String auth = server.optString("authStatus");
        int group = status.equals("connected") ? R.string.ui_connected_419 :
            status.equals("authenticationRequired") || status.equals("failed") || auth.equals("notLoggedIn") ?
                R.string.ui_needs_attention : R.string.ui_other_status;
        listed.add(new CapabilityDirectoryDialog.Entry(name,
            UiText.get(group), "", () -> loadMcpDetail(name, threadId)));
      }
      listed.sort(Comparator.comparingInt((CapabilityDirectoryDialog.Entry e) ->
          e.group.equals(UiText.get(R.string.ui_connected_419)) ? 0 :
              e.group.equals(UiText.get(R.string.ui_needs_attention)) ? 1 : 2)
          .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
      dialog.showEntries(listed);
    }, error -> dialog.showError());
  }

  private void loadMcpDetail(String name, String threadId) {
    if (!app.connected || !threadId.equals(app.threadId)) return;
    AlertDialog detail = new AlertDialog.Builder(activity).setTitle(name)
        .setMessage(UiText.get(R.string.ui_loading))
        .setPositiveButton(UiText.get(R.string.ui_close), null).create();
    detail.show();
    JSONObject params = Json.obj("serverName", name, "detail", "full");
    try { if (!threadId.isEmpty()) params.put("threadId", threadId); }
    catch (JSONException ignored) {}
    app.request("mcpServerStatus/list", params, result -> {
      if (!detail.isShowing() || activity.isDestroyed()) return;
      if (!threadId.equals(app.threadId)) { detail.dismiss(); return; }
      JSONArray data = result.optJSONArray("data");
      JSONObject server = data == null ? null : data.optJSONObject(0);
      detail.setMessage(server == null ? UiText.get(R.string.ui_no_mcp_servers) :
          McpServerDetail.describe(server));
    }, error -> {
      if (detail.isShowing() && !activity.isDestroyed())
        detail.setMessage(UiText.get(R.string.ui_remote_list_failed));
    });
  }

  private void unavailable() {
    new AlertDialog.Builder(activity).setMessage(UiText.get(R.string.ui_connect_to_view_remote_capabilities))
        .setPositiveButton(UiText.get(R.string.ui_close), null).show();
  }
}
