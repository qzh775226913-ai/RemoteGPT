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
  private final java.util.Map<String,Integer> bubbleIds = new java.util.LinkedHashMap<>();
  private final java.util.Map<String,String> bubbleSignatures = new java.util.HashMap<>();
  private int nextBubble = 1000;
  private final java.util.Map<String,String> conversationShortcuts = new java.util.HashMap<>();
  private final java.util.Map<String,String> shortcutLabels = new java.util.HashMap<>();
  private void releaseShortcut(String thread) {
    String id = conversationShortcuts.remove(thread);
    shortcutLabels.remove(thread);
    if (id != null && android.os.Build.VERSION.SDK_INT >= 30) {
      android.content.pm.ShortcutManager shortcuts = getSystemService(android.content.pm.ShortcutManager.class);
      shortcuts.removeDynamicShortcuts(java.util.Collections.singletonList(id));
      shortcuts.removeLongLivedShortcuts(java.util.Collections.singletonList(id));
    }
  }
  private void updateBubbles() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    java.util.List<String> current = app.conversationIds();
    for (String old : new java.util.ArrayList<>(bubbleIds.keySet())) {
      if (!app.connected || !current.contains(old)) {
        nm.cancel(bubbleIds.remove(old)); bubbleSignatures.remove(old); releaseShortcut(old);
      }
    }
    if (!app.connected) return;
    for (String thread : current) app.inConversation(thread, () -> {
      int id = bubbleIds.computeIfAbsent(thread, key -> bubbleIds.isEmpty() ? 2 : (nextBubble += 2));
      if (!LiveStatusNotifications.running(app)) {
        nm.cancel(id); bubbleSignatures.remove(thread); releaseShortcut(thread); return;
      }
      String summary = LiveStatusNotifications.preview(app);
      String signature = UiText.locale().toLanguageTag() + summary + app.threadTitle + LiveStatusNotifications.stage(app);
      if (signature.equals(bubbleSignatures.get(thread))) return;
      bubbleSignatures.put(thread, signature);
      postBubble(nm, id, summary);
    });
  }
  private final Runnable pollStatus = new Runnable() {
    public void run() {
      if (app != null) {app.pollPassiveStatus();app.pollConversationJournals();}
      handler.postDelayed(this, 5000);
    }
  };
  private final Runnable refresh = this::updateNotification;
  private final Runnable stateChanged = () -> {
    if (!refreshPending) { refreshPending = true; handler.postDelayed(refresh, 250); }
  };

  static String summary(RemoteApp app) {
    if (!app.connected) return UiPresentation.status(app.state);
    if (app.recoveryInProgress()) return UiPresentation.status(app.recoveryStatus());
    if (!app.approvals.isEmpty()) {
      String detail = LiveStatusNotifications.details(app);
      detail = detail.replaceAll("\\s+", " ");
      if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
      return (LiveStatusNotifications.isApproval(app.approvals.get(0)) ? UiText.get(R.string.ui_awaiting_approval) : UiText.get(R.string.ui_awaiting_answer)) + detail;
    }
    return app.currentThreadRunning() ? UiText.get(R.string.ui_remote_task_is_running) : UiText.get(R.string.ui_connected_waiting_for_a_message);
  }

  private void updateNotification() {
    refreshPending = false;
    if (app == null) return;
    renameChannels();
    app.liveStatus.update();
    updateBubbles();
    String summary = summary(app);
    String signature = UiText.locale().toLanguageTag() + summary + "|" + app.threadId + "|" + app.threadTitle;
    if (signature.equals(lastSummary)) return;
    lastSummary = signature;
    NotificationManager nm = getSystemService(NotificationManager.class);
    PendingIntent open = PendingIntent.getActivity(this, 0,
        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder status = new Notification.Builder(this, "ssh")
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("RemoteGPT · SSH")
        .setContentText(summary).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE);
    PendingIntent stop = PendingIntent.getService(this, 1,
        new Intent(this, ConnectionService.class).setAction("disconnect"), PendingIntent.FLAG_IMMUTABLE);
    status.addAction(new Notification.Action.Builder(null, UiText.get(R.string.ui_disconnect), stop).build());
    nm.notify(1, status.build());
  }

  private void renameChannels() {
    NotificationManager manager = getSystemService(NotificationManager.class);
    String[] ids = {"ssh", "conversation"};
    String[] names = {UiText.get(R.string.ui_ssh_connection), UiText.get(R.string.ui_task_status_and_approval_bubbles)};
    for (int i = 0; i < ids.length; i++) {
      NotificationChannel channel = manager.getNotificationChannel(ids[i]);
      if (channel != null) { channel.setName(names[i]); manager.createNotificationChannel(channel); }
    }
  }

  private void postBubble(NotificationManager nm, int id, String summary) {
    PendingIntent open = PendingIntent.getActivity(this, id,
        app.conversationIntent(MainActivity.class, app.threadId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder conversation = new Notification.Builder(this, "conversation")
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(LiveStatusNotifications.title(app))
        .setContentText(summary).setStyle(new Notification.BigTextStyle().bigText(summary))
        .setContentIntent(open).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      String title = LiveStatusNotifications.title(app);
      android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher);
      Person remote = new Person.Builder().setName("RemoteGPT").setKey("remote-gpt").setBot(true).setIcon(icon).build();
      Intent bubble = app.conversationIntent(BubbleActivity.class, app.threadId);
      String shortcutId = "conversation-" + (app.profile == null ? "" : app.profile.id) + "-" + app.threadId;
      if (!title.equals(shortcutLabels.get(app.threadId))) {
        android.content.pm.ShortcutManager shortcuts = getSystemService(android.content.pm.ShortcutManager.class);
        shortcuts.pushDynamicShortcut(new android.content.pm.ShortcutInfo.Builder(this, shortcutId)
            .setShortLabel(title).setLongLived(true).setPerson(remote).setIcon(icon).setIntent(bubble).build());
        conversationShortcuts.put(app.threadId, shortcutId);
        shortcutLabels.put(app.threadId, title);
        // NotificationManager caches long-lived conversation shortcuts. Keep that
        // cached identity for bubbles, but remove its launcher-menu publication.
        handler.postDelayed(() -> shortcuts.removeDynamicShortcuts(java.util.Collections.singletonList(shortcutId)), 500);
      }
      conversation.setShortcutId(shortcutId);
      PendingIntent bubbleIntent = PendingIntent.getActivity(this, id, bubble,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
      conversation.setStyle(new Notification.MessagingStyle(new Person.Builder().setName(UiText.get(R.string.ui_you)).build())
              .setConversationTitle(title).addMessage(summary, System.currentTimeMillis(), remote))
          .setBubbleMetadata(new Notification.BubbleMetadata.Builder(bubbleIntent, icon).setDesiredHeight(app.approvals.isEmpty() ? 144 : 208).build());
    }
    nm.notify(id, conversation.build());
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
    handler.removeCallbacks(pollStatus);
    handler.removeCallbacks(refresh);
    if (app != null) app.stateListeners.remove(stateChanged);
    for (int id : bubbleIds.values()) getSystemService(NotificationManager.class).cancel(id);
    for (String thread : new java.util.ArrayList<>(conversationShortcuts.keySet())) releaseShortcut(thread);
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
    handler.postDelayed(pollStatus, 5000);
    android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
    wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "RemoteSSH:connection");
    wakeLock.setReferenceCounted(false);
    renew.run();
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("ssh", UiText.get(R.string.ui_ssh_connection), NotificationManager.IMPORTANCE_LOW));
    NotificationChannel conversations = new NotificationChannel("conversation", UiText.get(R.string.ui_task_status_and_approval_bubbles), NotificationManager.IMPORTANCE_DEFAULT);
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name) + " · SSH")
            .setContentText(UiText.get(R.string.ui_remote_connection_is_active))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, UiText.get(R.string.ui_disconnect_365), stop).build())
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
