package app.remotessh;

import android.os.Looper;
import java.util.*;
import org.json.JSONObject;

/**
 * Owns the per-connection conversation cache and temporary background dispatch.
 *
 * RemoteApp's selected fields remain a compatibility facade for existing views.
 * Only this class switches that facade. Changes are published after restoring the
 * selected conversation, including when an action throws or dispatch is nested.
 */
final class ConversationStore {
  private final RemoteApp app;
  private final Map<String, ConversationState> states = new LinkedHashMap<>();
  private int scopeDepth;
  private boolean scopeChanged;

  ConversationStore(RemoteApp app) { this.app = app; }

  private void requireMainThread() {
    if (Looper.myLooper() != app.main.getLooper())
      throw new IllegalStateException("Conversation state must be accessed on the main thread");
  }

  void checkpoint() {
    requireMainThread();
    states.computeIfAbsent(app.threadId, key -> new ConversationState()).capture(app);
  }

  private ConversationState create(String id, boolean inheritSelection) {
    ConversationState state = new ConversationState();
    state.threadId = id;
    if (inheritSelection) {
      state.model = app.model;
      state.effort = app.reasoningEffort;
      state.permission = app.permissionMode;
    }
    app.restoreServiceTier(state, id);
    if (!inheritSelection || !id.isEmpty()) {
      app.restorePermission(state, id);
      app.restoreInteractions(state, id);
    }
    return state;
  }

  void select(String id) {
    checkpoint();
    states.computeIfAbsent(id, key -> create(key, true)).apply(app);
  }

  void dispatch(String id, Runnable action) {
    requireMainThread();
    if (id.equals(app.threadId)) { action.run(); return; }
    checkpoint();
    ConversationState selected = states.get(app.threadId);
    ConversationState target = states.computeIfAbsent(id, key -> create(key, false));
    Profile selectedProfile = app.profile;
    String project = selectedProfile == null ? "" : selectedProfile.cwd;
    String lastThread = selectedProfile == null ? "" : selectedProfile.lastThread;
    scopeDepth++;
    target.apply(app);
    try {
      action.run();
    } finally {
      checkpoint();
      selected.apply(app);
      scopeDepth--;
      if (app.profile == selectedProfile && selectedProfile != null) {
        selectedProfile.cwd = project;
        selectedProfile.lastThread = lastThread;
      }
      if (scopeDepth == 0 && scopeChanged) {
        scopeChanged = false;
        app.changed();
      }
    }
  }

  boolean deferChanges() {
    requireMainThread();
    if (scopeDepth == 0) return false;
    scopeChanged = true;
    return true;
  }

  boolean isScoped() { return scopeDepth > 0; }

  List<String> ids() {
    checkpoint();
    List<String> ids = new ArrayList<>(states.keySet());
    ids.remove("");
    return ids;
  }

  List<JSONObject> approvals() {
    checkpoint();
    List<JSONObject> result = new ArrayList<>();
    for (ConversationState state : states.values()) result.addAll(state.approvals);
    return result;
  }

  ConversationState get(String id) { return states.get(id); }
  boolean contains(String id) { return states.containsKey(id); }
  Collection<ConversationState> values() { return Collections.unmodifiableCollection(states.values()); }
  void clear() { requireMainThread(); states.clear(); }
}
