package app.remotessh;

import java.util.*;
import org.json.*;

/** Server-backed conversation listing and metadata actions. */
final class ThreadDirectory {
  private final RemoteApp app;
  private long listRevision;
  ThreadDirectory(RemoteApp app) { this.app = app; }

  void invalidate() {
    listRevision++;
    app.nextCursor = "";
    app.threads = new JSONArray();
    app.changed();
  }

  void refresh(boolean more) {
    JSONObject params = Json.obj("limit", 50, "sortKey", "updated_at");
    try {
      if (!app.historyProject.isEmpty()) params.put("cwd", app.historyProject);
      if (!app.historySearch.isEmpty()) params.put("searchTerm", app.historySearch);
      if (more && !app.nextCursor.isEmpty()) params.put("cursor", app.nextCursor);
    } catch (JSONException ignored) {}
    String project = app.historyProject, query = app.historySearch;
    long listing = ++listRevision;
    long revision = app.statusRevision;
    app.request("thread/list", params, result -> {
      if (listing != listRevision || !project.equals(app.historyProject) || !query.equals(app.historySearch)) return;
      JSONArray data = result.optJSONArray("data");
      if (!more) app.threads = new JSONArray();
      if (data != null) for (int i = 0; i < data.length(); i++) {
        JSONObject listed = data.optJSONObject(i);
        if (listed == null) continue;
        app.threads.put(listed);
        String id = listed.optString("id");
        if (revision == app.statusRevision && !id.isEmpty() && !app.loadedThreads.contains(id) && !id.equals(app.loadingThread))
          app.inConversation(id, () -> {
            app.remoteRunning = Json.object(listed, "status").optString("type").equals("active");
            if (!app.remoteRunning) { app.busy = false; app.turnId = ""; app.clearServerRequests(); }
            app.threadTitle = app.restoredTitle(listed, id, app.threadTitle);
          });
      }
      if (app.profile != null) {
        boolean added = false;
        for (int i = 0; i < app.threads.length(); i++) {
          JSONObject thread = app.threads.optJSONObject(i);
          if (thread == null) continue;
          String cwd = thread.optString("cwd");
          if (cwd.startsWith("/") && app.profile.projects.size() < 200)
            added |= app.profile.projects.add(cwd);
        }
        if (added) app.save();
      }
      app.nextCursor = result.isNull("nextCursor") ? "" : result.optString("nextCursor");
      app.changed();
    });
  }

  void rename(String id, String name) {
    String clean = name.trim();
    if (id.isEmpty() || clean.isEmpty()) return;
    app.request("thread/name/set", Json.obj("threadId", id, "name", clean), result -> {
      updateName(id, clean);
      app.inConversation(id, () -> { app.threadTitle = clean; app.changed(); });
    });
  }

  void updateName(String id, String name) {
    for (int i = 0; i < app.threads.length(); i++) {
      JSONObject row = app.threads.optJSONObject(i);
      if (row != null && id.equals(row.optString("id"))) try { row.put("name", name); } catch (JSONException ignored) {}
    }
    app.changed();
  }

  boolean pinned(String id) {
    return app.profile != null && app.getSharedPreferences("pinned-threads", 0)
        .getStringSet(app.profile.id, Collections.emptySet()).contains(id);
  }

  void pin(String id, boolean value) {
    if (app.profile == null || id.isEmpty()) return;
    Set<String> ids = new HashSet<>(app.getSharedPreferences("pinned-threads", 0)
        .getStringSet(app.profile.id, Collections.emptySet()));
    if (value) ids.add(id); else ids.remove(id);
    app.getSharedPreferences("pinned-threads", 0).edit().putStringSet(app.profile.id, ids).apply();
    app.changed();
  }
}
