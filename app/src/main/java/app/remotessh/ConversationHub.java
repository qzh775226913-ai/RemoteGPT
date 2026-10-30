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
    AlertDialog dialog = loading(UiText.get(R.string.ui_available_skills));
    app.request("skills/list", Json.obj("cwds", new JSONArray().put(cwd)), result -> {
      if (!dialog.isShowing() || activity.isDestroyed()) return;
      if (!cwd.equals(app.currentProject())) { dialog.dismiss(); return; }
      StringBuilder text = new StringBuilder();
      JSONArray groups = result.optJSONArray("data");
      if (groups != null) for (int i=0;i<groups.length();i++) {
        JSONObject group = groups.optJSONObject(i);
        if (group == null) continue;
        JSONArray list = group.optJSONArray("skills");
        if (list != null) for (int n=0;n<list.length();n++) {
          JSONObject item = list.optJSONObject(n);
          if (item == null) continue;
          if (text.length()>0) text.append("\n\n");
          text.append(item.optString("name"));
          if (!item.optBoolean("enabled", true)) text.append(" · ").append(UiText.get(R.string.ui_disabled));
          String description = item.optString("description");
          if (!description.isEmpty()) text.append("\n").append(description);
        }
        JSONArray errors = group.optJSONArray("errors");
        if (errors != null) for (int n=0;n<errors.length();n++) {
          JSONObject problem = errors.optJSONObject(n);
          if (problem != null && !problem.optString("message").isEmpty()) {
            if (text.length()>0) text.append("\n\n");
            text.append(problem.optString("message"));
          }
        }
      }
      dialog.setMessage(text.length()==0 ? UiText.get(R.string.ui_no_skills_available) : text.toString());
    }, error -> { if (dialog.isShowing() && !activity.isDestroyed()) dialog.setMessage(UiText.get(R.string.ui_remote_list_failed)); });
  }

  private void mcp() {
    if (!app.connected) { unavailable(); return; }
    AlertDialog dialog = loading(UiText.get(R.string.ui_mcp_servers));
    JSONArray entries = new JSONArray();
    loadMcpPage(dialog, entries, "", 0, app.threadId);
  }

  private void loadMcpPage(AlertDialog dialog, JSONArray entries, String cursor, int page, String threadId) {
    JSONObject params = Json.obj("limit", 50, "detail", "toolsAndAuthOnly");
    try {
      if (!cursor.isEmpty()) params.put("cursor", cursor);
      if (!threadId.isEmpty()) params.put("threadId", threadId);
    } catch (JSONException ignored) {}
    app.request("mcpServerStatus/list", params, result -> {
      if (!dialog.isShowing() || activity.isDestroyed()) return;
      if (!threadId.equals(app.threadId)) { dialog.dismiss(); return; }
      JSONArray data = result.optJSONArray("data");
      if (data != null) for (int i=0;i<data.length();i++) entries.put(data.opt(i));
      String next = result.isNull("nextCursor") ? "" : result.optString("nextCursor");
      if (!next.isEmpty() && page < 19 && !next.equals(cursor)) { loadMcpPage(dialog, entries, next, page+1, threadId); return; }
      StringBuilder text = new StringBuilder();
      for (int i=0;i<entries.length();i++) {
        JSONObject server = entries.optJSONObject(i);
        if (server == null) continue;
        if (text.length()>0) text.append("\n\n");
        text.append(server.optString("name"));
        if (!server.isNull("runtimeStatus")) text.append(" · ").append(server.optString("runtimeStatus"));
        Object auth = server.opt("authStatus");
        if (auth != null && auth != JSONObject.NULL) text.append(" · ").append(auth instanceof JSONObject ? ((JSONObject)auth).optString("type", auth.toString()) : auth);
        JSONObject tools = server.optJSONObject("tools");
        if (tools != null) {
          List<String> names = new ArrayList<>();
          for (Iterator<String> keys = tools.keys(); keys.hasNext();) names.add(keys.next());
          Collections.sort(names);
          if (!names.isEmpty()) text.append("\n").append(UiText.get(R.string.ui_detail_tools, String.join(", ", names)));
        }
        String error = server.optString("toolsError");
        if (!error.isEmpty()) text.append("\n").append(error);
      }
      dialog.setMessage(text.length()==0 ? UiText.get(R.string.ui_no_mcp_servers) : text.toString());
    }, error -> { if (dialog.isShowing() && !activity.isDestroyed()) dialog.setMessage(UiText.get(R.string.ui_remote_list_failed)); });
  }

  private AlertDialog loading(String title) {
    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
        .setMessage(UiText.get(R.string.ui_loading)).setPositiveButton(UiText.get(R.string.ui_close), null).create();
    dialog.show();
    return dialog;
  }

  private void unavailable() {
    new AlertDialog.Builder(activity).setMessage(UiText.get(R.string.ui_connect_to_view_remote_capabilities))
        .setPositiveButton(UiText.get(R.string.ui_close), null).show();
  }
}
