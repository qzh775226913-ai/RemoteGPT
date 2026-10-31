package app.remotessh;

/** Resumes a read-only history view before a user-confirmed manual compaction. */
final class ContextCompactionController {
  private final RemoteApp app;
  private String pendingThread = "";
  private int pendingGeneration;
  private boolean awaitingResume;
  private String requestedThread = "";
  private boolean observedRunning;
  private final Runnable observer = () -> { updateRequested(); advance(); };

  ContextCompactionController(RemoteApp app) { this.app = app; }

  boolean canStart() {
    return app.connected && !app.threadId.isEmpty() && app.canSwitchConversation()
        && !app.busy && !app.remoteRunning && !app.replaying && !app.attaching && !isCompressing();
  }

  boolean isCompressing() {
    if (app.threadId.equals(pendingThread) && !pendingThread.isEmpty()
        || app.threadId.equals(requestedThread) && !requestedThread.isEmpty()) return true;
    if (!app.currentThreadRunning()) return false;
    for (ChatItem item : app.items)
      if (item.type.equals("contextCompaction") && item.turnId.equals(app.turnId)) return true;
    return false;
  }

  private void updateRequested() {
    if (requestedThread.isEmpty()) return;
    if (!app.connected || pendingGeneration != app.connectionGeneration() || !requestedThread.equals(app.threadId)
        || observedRunning && !app.currentThreadRunning()) {
      requestedThread = ""; observedRunning = false;
      if (pendingThread.isEmpty()) app.stateListeners.remove(observer);
    } else if (app.currentThreadRunning()) observedRunning = true;
  }

  boolean start(String id) {
    if (!canStart() || !id.equals(app.threadId)) return false;
    if (!app.readOnly) { compact(id); return true; }
    cancel();
    pendingThread = id;
    pendingGeneration = app.connectionGeneration();
    awaitingResume = true;
    app.stateListeners.add(observer);
    app.resume(id);
    awaitingResume = false;
    advance();
    return true;
  }

  private void advance() {
    if (pendingThread.isEmpty()) return;
    if (awaitingResume) return;
    if (!app.connected || pendingGeneration != app.connectionGeneration()
        || !pendingThread.equals(app.threadId) || app.conversationStore.isScoped()) {
      cancel(); return;
    }
    if (app.readOnly) {
      if (!app.busy && app.loadingThread.isEmpty() && !app.replaying) cancel();
      return;
    }
    if (app.replaying || !app.loadingThread.isEmpty()) return;
    String id = pendingThread;
    cancel();
    if (!app.busy && !app.remoteRunning) compact(id);
  }

  private void compact(String id) {
    if (!app.connected || app.readOnly || app.busy || app.remoteRunning || app.replaying
        || !id.equals(app.threadId) || app.conversationStore.isScoped()) return;
    requestedThread = id;
    pendingGeneration = app.connectionGeneration();
    observedRunning = false;
    app.stateListeners.add(observer);
    app.changed();
    app.request("thread/compact/start", Json.obj("threadId", id), result -> {
      app.main.postDelayed(() -> {
        if (requestedThread.equals(id) && !observedRunning && !app.currentThreadRunning()) {
          requestedThread="";
          if (pendingThread.isEmpty()) app.stateListeners.remove(observer);
          app.changed();
        }
      }, 3000);
    }, error -> {
      requestedThread = ""; observedRunning = false;
      if (pendingThread.isEmpty()) app.stateListeners.remove(observer);
      app.changed();
    });
  }

  private void cancel() {
    pendingThread = "";
    awaitingResume = false;
    if (requestedThread.isEmpty()) app.stateListeners.remove(observer);
  }
}
