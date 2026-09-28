package app.remotessh;

import android.app.*;
import android.content.*;
import android.os.IBinder;

public final class ConnectionService extends Service {
  private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

  private android.os.PowerManager.WakeLock wakeLock;
  private RemoteApp app;
  private boolean refreshPending;
  private String lastSummary = "";
  private final Runnable refresh = this::updateNotification;
  private final Runnable stateChanged = () -> {
    if (!refreshPending) { refreshPending = true; handler.postDelayed(refresh, 250); }
  };

  static String summary(RemoteApp app) {
    if (!app.connected) return app.state;
    if (app.replaying) return "正在恢复断线期间的消息…";
    if (!app.approvals.isEmpty()) {
      org.json.JSONObject p = Json.object(app.approvals.get(0), "params");
      String detail = p.optString("command", p.optString("reason", "文件修改或补充信息"));
      detail = detail.replaceAll("\\s+", " ");
      if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
      return "等待审批 · " + detail;
    }
    return app.busy || app.remoteRunning ? "远端正在运行" : "已连接 · 等待消息";
  }

  private void updateNotification() {
    refreshPending = false;
    if (app == null) return;
    app.liveStatus.update();
    String summary = summary(app);
    String signature = summary + "|" + app.threadId + "|" + app.threadTitle;
    if (signature.equals(lastSummary)) return;
    lastSummary = signature;
    NotificationManager nm = getSystemService(NotificationManager.class);
    PendingIntent open = PendingIntent.getActivity(this, 0,
        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder status = new Notification.Builder(this, "ssh")
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle("RemoteGPT · SSH")
        .setContentText(summary).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE);
    PendingIntent stop = PendingIntent.getService(this, 1,
        new Intent(this, ConnectionService.class).setAction("disconnect"), PendingIntent.FLAG_IMMUTABLE);
    status.addAction(new Notification.Action.Builder(null, "断开连接", stop).build());
    nm.notify(1, status.build());
    if (!app.connected || app.threadId.isEmpty()) { nm.cancel(2); return; }
    Notification.Builder conversation = new Notification.Builder(this, "conversation")
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle("RemoteGPT")
        .setContentText(summary).setStyle(new Notification.BigTextStyle().bigText(summary))
        .setContentIntent(open).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      String title = app.threadTitle.isEmpty() ? "RemoteGPT 对话" : app.threadTitle;
      android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher);
      Person remote = new Person.Builder().setName("RemoteGPT").setKey("remote-gpt").setBot(true).setIcon(icon).build();
      Intent bubble = new Intent(this, BubbleActivity.class).setAction(Intent.ACTION_VIEW);
      android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(this, "active-conversation")
          .setShortLabel(title).setLongLived(true).setPerson(remote).setIcon(icon).setIntent(bubble).build();
      getSystemService(android.content.pm.ShortcutManager.class).pushDynamicShortcut(shortcut);
      PendingIntent bubbleIntent = PendingIntent.getActivity(this, 2, bubble,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
      conversation.setShortcutId("active-conversation")
          .setStyle(new Notification.MessagingStyle(new Person.Builder().setName("你").build())
              .setConversationTitle(title).addMessage(summary, System.currentTimeMillis(), remote))
          .setBubbleMetadata(new Notification.BubbleMetadata.Builder(bubbleIntent, icon).setDesiredHeight(520).build());
    }
    nm.notify(2, conversation.build());
  }

  private final Runnable renew = new Runnable() {
    public void run() {
      if (wakeLock == null) return;
      wakeLock.acquire(10 * 60 * 1000L);
      handler.postDelayed(this, 5 * 60 * 1000L);
    }
  };

  @Override public void onDestroy() {
    handler.removeCallbacks(renew);
    handler.removeCallbacks(refresh);
    if (app != null) app.stateListeners.remove(stateChanged);
    getSystemService(NotificationManager.class).cancel(2);
    if (app != null) app.liveStatus.clear();
    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    wakeLock = null;
    super.onDestroy();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    app = (RemoteApp) getApplication();
    app.stateListeners.add(stateChanged);
    android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
    wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "RemoteSSH:connection");
    wakeLock.setReferenceCounted(false);
    renew.run();
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("ssh", "SSH 连接", NotificationManager.IMPORTANCE_LOW));
    NotificationChannel conversations = new NotificationChannel("conversation", "运行状态与审批气泡", NotificationManager.IMPORTANCE_DEFAULT);
    if (android.os.Build.VERSION.SDK_INT >= 29) conversations.setAllowBubbles(true);
    nm.createNotificationChannel(conversations);
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, ConnectionService.class).setAction("disconnect"),
            PendingIntent.FLAG_IMMUTABLE);
    startForeground(
        1,
        new Notification.Builder(this, "ssh")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name) + " · SSH")
            .setContentText("远端连接正在运行")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "断开", stop).build())
            .build());
    updateNotification();
  }

  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    if (i != null && "disconnect".equals(i.getAction())) {
      ((RemoteApp) getApplication()).disconnect();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }
}
