package app.remotessh;

/** One main-thread refresh timer per connection, independent of picker windows. */
final class ModelRefreshScheduler {
  static final long INTERVAL_MS = 30_000;
  private final RemoteApp app;
  private Runnable task;

  ModelRefreshScheduler(RemoteApp app) { this.app = app; }

  void start() {
    stop();
    if (!app.connected) return;
    int generation = app.connectionGeneration();
    task = new Runnable() {
      private boolean active() {
        return task == this && app.connected && generation == app.connectionGeneration();
      }
      @Override public void run() {
        if (!active()) return;
        app.refreshModels();
        app.refreshModelSettings();
        if (active()) app.main.postDelayed(this, INTERVAL_MS);
      }
    };
    app.main.postDelayed(task, INTERVAL_MS);
  }

  void stop() {
    if (task != null) app.main.removeCallbacks(task);
    task = null;
  }
}
