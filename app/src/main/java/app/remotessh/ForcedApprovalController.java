package app.remotessh;

import java.util.*;
import org.json.JSONObject;

/** An explicit, temporary permission grant scoped to a conversation and SSH generation. */
final class ForcedApprovalController {
  static final class State {
    boolean enabled;
    int generation;
    long revision;
    String thread = "";
    final Set<String> submitted = new HashSet<>();
  }

  private final RemoteApp app;
  private boolean scheduled;

  ForcedApprovalController(RemoteApp app) { this.app = app; }

  boolean active() { return active(app.forcedApprovalState); }
  private boolean active(State state) {
    return app.connected && state.enabled && state.generation == app.connectionGeneration();
  }

  boolean enable(String thread) {
    final boolean[] enabled = {false};
    app.inConversation(thread, () -> {
      if (!app.connected || app.readOnly || app.threadId.isEmpty() || app.recoveryInProgress()) return;
      app.choosePermission(PermissionMode.FULL);
      State state = app.forcedApprovalState;
      state.enabled = true; state.generation = app.connectionGeneration(); state.revision++;
      state.thread = app.threadId;
      state.submitted.clear();
      enabled[0] = true;
      app.changed();
      drain();
    });
    return enabled[0];
  }

  void disable() {
    app.forcedApprovalState.enabled = false;
    app.forcedApprovalState.revision++;
    app.changed();
  }

  void selectionChanged() { app.forcedApprovalState.revision++; }

  void permissionApplied(PermissionMode sent, State state, long revision) {
    // A selection/grant made after submission belongs to a later turn.
    if (app.forcedApprovalState == state && state.revision == revision && sent == app.permissionMode) {
      state.enabled = false;
      state.submitted.clear();
    }
  }

  private boolean eligible(JSONObject request, State state) {
    return active(state) && LiveStatusNotifications.isApproval(request) && !request.isNull("id")
        && Json.object(request, "params").optString("threadId", state.thread).equals(state.thread);
  }

  JSONObject visible(List<JSONObject> requests, State state, boolean writable) {
    for (JSONObject request : requests)
      if (!writable || !eligible(request, state)) return request;
    return null;
  }

  private static String key(JSONObject request) {
    return request.optString("method") + ":" + request.opt("id") + ":"
        + Json.object(request, "params").optString("turnId");
  }

  void failed(JSONObject request) {
    if (active() && app.forcedApprovalState.submitted.contains(key(request))) {
      app.forcedApprovalState.enabled = false;
      app.forcedApprovalState.revision++;
    }
  }

  void settled(JSONObject request) {
    if (app.forcedApprovalState.submitted.contains(key(request)))
      app.approvals.removeIf(other -> key(other).equals(key(request)));
  }

  void schedule() {
    if (scheduled || !app.connected || app.recoveryInProgress()) return;
    boolean any = active();
    for (ConversationState state : app.conversationStore.values()) any |= active(state.forcedApproval);
    if (!any) return;
    scheduled = true;
    int generation = app.connectionGeneration();
    app.main.post(() -> {
      scheduled = false;
      if (generation != app.connectionGeneration() || !app.connected || app.recoveryInProgress()) return;
      for (String thread : app.conversationIds()) app.inConversation(thread, this::drain);
    });
  }

  void drain() {
    if (!active() || app.readOnly || app.recoveryInProgress() || !app.threadId.equals(app.forcedApprovalState.thread)) return;
    for (JSONObject request : new ArrayList<>(app.approvals)) {
      if (!eligible(request, app.forcedApprovalState) || app.isAnswering(request)) continue;
      if (!app.forcedApprovalState.submitted.add(key(request))) continue;
      app.answer(request, Json.obj("decision", "accept"));
      if (!app.isAnswering(request) && app.approvals.contains(request)) {
        failed(request);
        app.error = "强制允许未能提交，已恢复手动审批。请检查连接后重试。";
        app.changed();
        break;
      }
    }
  }
}
