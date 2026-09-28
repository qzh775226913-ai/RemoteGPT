package app.remotessh;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.UUID;
import org.json.JSONObject;

/** A separate, standard Live Update; the existing conversation bubble is untouched. */
public final class LiveStatusNotifications {
  static final int ID = 3;
  static final String CHANNEL = "live-status";
  static final String ATTENTION_CHANNEL = "live-attention";
  // Public Android extra, named in the official Live Update guide. The symbolic
  // constant becomes public in 36.1; its payload is understood by Android 16.
  static final String PROMOTED = "android.requestPromotedOngoing";
  static final String ACTION = "app.remotessh.LIVE_ACTION";
  private final RemoteApp app;
  private final boolean card;
  private final int notificationId;
  private final java.util.Map<String, LiveStatusNotifications> cards = new java.util.LinkedHashMap<>();
  private int nextId = ID;
  int idFor(String thread) {
    LiveStatusNotifications value = cards.get(thread); return value == null ? -1 : value.notificationId;
  }
  private String lastSignature = "", runToken = "", owner = "", turn = "";
  private boolean active, dismissed;
  private String postedChannel = "";
  private final java.util.Set<String> alerted = new java.util.HashSet<>();

  public LiveStatusNotifications(RemoteApp app) { this(app, false, ID); }
  private LiveStatusNotifications(RemoteApp app, boolean card, int id) {
    this.app=app; this.card=card; this.notificationId=id;
  }
  private LiveStatusNotifications card(String id) {
    return cards.computeIfAbsent(id, key -> {
      int assigned=nextId; nextId = nextId == ID ? 1001 : nextId + 2;
      return new LiveStatusNotifications(app, true, assigned);
    });
  }

  static boolean running(RemoteApp app) {
    return app.currentThreadRunning();
  }

  // Called synchronously on state changes, so a brief idle boundary between
  // queued turns cannot disappear inside the notification update throttle.
  void observe() {
    if (!card) {
      for (String id : app.conversationIds()) app.inConversation(id, () -> card(id).observe());
      return;
    }
    boolean now = running(app);
    String newOwner = (app.profile == null ? "" : app.profile.id) + ":" + app.threadId;
    if (now && (!active || !owner.equals(newOwner)
        || (!turn.isEmpty() && !app.turnId.isEmpty() && !turn.equals(app.turnId)))) {
      runToken = UUID.randomUUID().toString();
      dismissed = false;
      lastSignature = "";
      alerted.clear();
    }
    active = now;
    owner = newOwner;
    turn = app.turnId;
  }

  private static ChatItem latest(RemoteApp app) {
    for (int i = app.items.size() - 1; i >= 0; i--) {
      ChatItem item = app.items.get(i);
      // Do not show an earlier turn as progress for a newly submitted message.
      if (item.user()) break;
      if (!item.text.trim().isEmpty() || !item.detail.trim().isEmpty()) return item;
    }
    return null;
  }

  static String stage(RemoteApp app) {
    if (app.replaying) return "同步中";
    if (!app.approvals.isEmpty()) return isApproval(app.approvals.get(0)) ? "待审批" : "待回答";
    if (app.state.startsWith("正在停止")) return "停止中";
    ChatItem item = latest(app);
    if (item != null) {
      if (item.type.equals("reasoning")) return "思考中";
      if (item.type.equals("commandExecution") || item.type.equals("mcpToolCall")) return "执行中";
      if (item.type.equals("fileChange")) return "修改中";
      if (item.type.equals("agentMessage")) return "回复中";
      if (item.type.equals("webSearch")) return "搜索中";
    }
    return "运行中";
  }

  static String title(RemoteApp app) {
    String name = app.threadTitle.replaceAll("\\s+", " ").trim();
    return (name.isEmpty() ? "RemoteGPT" : shorten(name, 12)) + " · " + stage(app);
  }

  static String preview(RemoteApp app) {
    // Native Live Updates own font sizes; keep their title and payload compact.
    return excerpt(details(app).replaceAll("\n[ \t]*\n+", "\n"), 240, 4);
  }

  static String details(RemoteApp app) {
    if (app.replaying) return "正在恢复断线期间的消息";
    if (!app.approvals.isEmpty()) {
      JSONObject request = app.approvals.get(0);
      JSONObject p = Json.object(request, "params");
      if (!isApproval(request)) {
        org.json.JSONArray questions = p.optJSONArray("questions");
        StringBuilder text = new StringBuilder();
        if (questions != null) for (int i = 0; i < questions.length(); i++) {
          JSONObject q = questions.optJSONObject(i);
          if (q != null) {
            if (text.length() > 0) text.append("\n\n");
            text.append(q.optString("question", q.optString("header")));
          }
        }
        return text.length() == 0 ? "需要补充信息，请填写后继续。" : shorten(text.toString(), 900);
      }
      String command = shorten(p.optString("command", p.optString("grantRoot", "文件修改")), 500);
      String reason = shorten(p.optString("reason"), 250);
      String cwd = shorten(p.optString("cwd"), 140);
      return (reason.isEmpty() ? "" : reason + "\n\n") + command
          + (cwd.isEmpty() ? "" : "\n目录：" + cwd);

    }
    ChatItem item = latest(app);
    if (item == null) return "已收到消息，等待远端最新进展…";
    if (item.type.equals("reasoning")) return excerpt(item.detail.isEmpty() ? "远端正在思考…" : item.detail, 600, 8);
    if (item.prose()) return excerpt(item.text, 600, 8);
    return shorten(item.text, 180) + (item.detail.isEmpty() ? "" : "\n\n" + excerpt(item.detail, 480, 6));
  }

  // Bound both characters and lines so terminal output uses the expanded area
  // for recent progress instead of pushing it below a long stale log prefix.
  private static String excerpt(String value, int chars, int lines) {
    String text = value.replace("\r\n", "\n").replace('\r', '\n').trim();
    int boundary = text.length();
    for (int i = 0; i < lines; i++) {
      boundary = text.lastIndexOf('\n', boundary - 1);
      if (boundary < 0) return tail(text, chars);
    }
    return tail("…" + text.substring(boundary + 1), chars);
  }

  // Keep the newest streaming text visible even when an item is very long.
  private static String tail(String text, int limit) {
    text = text.trim();
    if (text.length() <= limit) return text;
    int start = text.length() - limit;
    if (Character.isLowSurrogate(text.charAt(start))) start++;
    return "…" + text.substring(start);
  }

  private static String shorten(String s, int limit) {
    s = s.trim(); return s.length() <= limit ? s : s.substring(0, limit) + "…";
  }

  private PendingIntent action(String token, String choice) {
    Intent intent = new Intent(app, LiveActionReceiver.class).setAction(ACTION)
        .setData(new Uri.Builder().scheme("remotegpt").authority("live")
            .appendPath(token).appendPath(choice).build());
    return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public void update() {
    if (!card) {
      if (!app.connected) { clear(); return; }
      for (String id : app.conversationIds()) app.inConversation(id, () -> card(id).update());
      return;
    }
    observe();
    NotificationManager manager = app.getSystemService(NotificationManager.class);
    boolean enabled = app.getSharedPreferences("background", Context.MODE_PRIVATE).getBoolean("liveStatus", true);
    if (!active || !enabled) { clear(); return; }
    manager.createNotificationChannel(new NotificationChannel(CHANNEL, "流体云 · 任务与审批", NotificationManager.IMPORTANCE_DEFAULT));
    JSONObject request = app.approvals.isEmpty() || app.replaying ? null : app.approvals.get(0);
    String token = request == null ? "" : app.notificationApprovalToken(request);
    boolean newRequest = request != null && !alerted.contains(token);
    if (newRequest) dismissed = false;
    if (dismissed) { clear(); return; }
    String channel = request == null ? CHANNEL : ATTENTION_CHANNEL;
    manager.createNotificationChannel(new NotificationChannel(ATTENTION_CHANNEL,
        "流体云 · 审批与询问提醒", NotificationManager.IMPORTANCE_HIGH));
    boolean answering = request != null && app.isAnswering(request);
    String stage = answering ? "提交中" : stage(app), details = preview(app);
    String signature = runToken + stage + details + token + app.threadTitle + answering;
    if (signature.equals(lastSignature)) return;
    lastSignature = signature;
    Intent open = app.conversationIntent(MainActivity.class, app.threadId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent content = PendingIntent.getActivity(app, notificationId, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Bundle extras = new Bundle(); extras.putBoolean(PROMOTED, true);
    Notification.Builder builder = new Notification.Builder(app, channel)
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle(title(app))
        .setContentText(tail(details.replace('\n', ' '), 180))
        .setStyle(new Notification.BigTextStyle().bigText(details))
        .setOngoing(true).setOnlyAlertOnce(!newRequest).setShowWhen(false)
        .setCategory(request == null ? Notification.CATEGORY_PROGRESS : Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
        .setContentIntent(content).setDeleteIntent(action(runToken, "hide")).addExtras(extras);
    if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(stage);
    if (request != null && !answering && isApproval(request)) {
      Notification.Action.Builder allow = new Notification.Action.Builder(null, "允许一次", action(token, "accept"));
      Notification.Action.Builder decline = new Notification.Action.Builder(null, "拒绝", action(token, "decline"));
      if (Build.VERSION.SDK_INT >= 31) { allow.setAuthenticationRequired(true); decline.setAuthenticationRequired(true); }
      builder.addAction(allow.build()).addAction(decline.build());
    } else if (request != null && !answering) {
      builder.addAction(new Notification.Action.Builder(null, "填写信息", content).build());
    }
    // A channel change requires reposting; ordinary deltas only update the same card.
    if (!postedChannel.isEmpty() && !postedChannel.equals(channel)) manager.cancel(notificationId);
    manager.notify(notificationId, builder.build());
    postedChannel = channel;
    if (newRequest) alerted.add(token);
  }

  static boolean isApproval(JSONObject request) {
    String method = request.optString("method");
    return method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval");
  }

  void handle(String token, String choice) {
    if (!card) {
      if (choice.equals("hide")) {
        for (LiveStatusNotifications c : cards.values()) if (token.equals(c.runToken)) c.handle(token, choice);
      } else handleApproval(token, choice);
      return;
    }
    if (choice.equals("hide")) {
      if (token.equals(runToken)) { dismissed = true; clear(); }
    } else handleApproval(token, choice);
  }

  private void handleApproval(String token, String choice) {
    if (!choice.equals("accept") && !choice.equals("decline")) return;
    KeyguardManager keyguard = app.getSystemService(KeyguardManager.class);
    if (keyguard != null && keyguard.isDeviceLocked()) return;
    app.answerNotification(token, choice);
  }

  void clear() {
    if (!card) { for (LiveStatusNotifications c : cards.values()) c.clear(); return; }
    app.getSystemService(NotificationManager.class).cancel(notificationId);
    lastSignature = "";
    postedChannel = "";
  }
}
