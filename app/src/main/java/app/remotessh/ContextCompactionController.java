package app.remotessh;

/** Resumes a read-only history view before a user-confirmed manual compaction. */
final class ContextCompactionController {
  private final RemoteApp app;
  private String pendingThread = "";
  private int pendingGeneration;
  private boolean awaitingResume;
  private final Runnable observer = this::advance;

  ContextCompactionController(RemoteApp app) { this.app = app; }

  boolean canStart() {
    return app.connected && !app.threadId.isEmpty() && app.canSwitchConversation()
        && !app.busy && !app.remoteRunning && !app.replaying && !app.attaching;
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
    app.request("thread/compact/start", Json.obj("threadId", id), result -> {});
  }

  private void cancel() {
    pendingThread = "";
    awaitingResume = false;
    app.stateListeners.remove(observer);
  }
}
