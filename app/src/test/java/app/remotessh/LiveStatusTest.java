package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.os.Looper;
import java.io.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LiveStatusTest {
  private RemoteApp app;
  private ByteArrayOutputStream sent;
  private RpcClient rpc;
  @Before public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.connected = true; app.busy = true;
    app.threadId = "task"; app.turnId = "turn";
    sent = new ByteArrayOutputStream();
    rpc = new RpcClient(new ByteArrayInputStream(new byte[0]), sent, new RpcClient.Events() {
      public void onEvent(JSONObject event) {} public void onClosed(Exception error) {}
    });
    var field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, rpc);
  }
  @After public void close() { rpc.close(); }
  private Notification live() {
    return Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveStatusNotifications.ID);
  }
  private JSONObject approval() {
    return Json.obj("id", 7, "method", "item/commandExecution/requestApproval", "params",
        Json.obj("command", "ls /project", "reason", "检查项目", "threadId", "task", "turnId", "turn"));
  }
  @Test public void runningCardIsPromotablePayloadAndFinishingRemovesOnlyLiveCard() {
    var service = Robolectric.buildService(ConnectionService.class).create();
    try {
      Notification n = live(); assertNotNull(n);
      assertTrue(n.extras.getBoolean(LiveStatusNotifications.PROMOTED));
      assertTrue((n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
      assertFalse((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0);
      assertNull(n.contentView); assertNull(n.getBubbleMetadata());
      assertEquals(Notification.BigTextStyle.class.getName(), n.extras.getString(Notification.EXTRA_TEMPLATE));
      assertNotNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(2).getBubbleMetadata());
      app.busy = false; app.turnId = ""; app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      assertNull(live());
      assertNotNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(2));
    } finally { service.destroy(); }
  }
  @Test public void replacingSameIdApprovalInvalidatesOldActionAndDoubleTapSendsOnce() throws Exception {
    app.approvals.add(approval()); app.liveStatus.update();
    PendingIntent old = live().actions[0].actionIntent;
    app.approvals.clear(); app.approvals.add(approval()); app.changed(); app.liveStatus.update();
    PendingIntent current = live().actions[0].actionIntent;
    assertNotEquals(old, current);
    old.send(); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(0, sent.size());
    current.send(); current.send();
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (sent.size() == 0 && System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5);
    }
    assertEquals(1, sent.toString("UTF-8").trim().split("\n").length);
    assertEquals("accept", new JSONObject(sent.toString("UTF-8").trim()).getJSONObject("result").getString("decision"));
  }
  @Test public void replayAndInputQuestionsNeverExposeApprovalButtons() {
    app.approvals.add(approval()); app.replaying = true; app.liveStatus.update();
    assertTrue(live().actions == null || live().actions.length == 0);
    app.replaying = false; app.approvals.clear();
    app.approvals.add(Json.obj("id", 8, "method", "item/tool/requestUserInput", "params", Json.obj()));
    app.liveStatus.update();
    assertEquals("填写信息", live().actions[0].title.toString());
  }
  @Test public void dismissedRunStaysHiddenAcrossUpdatesAndNextRunCanAppear() throws Exception {
    app.liveStatus.update();
    live().deleteIntent.send(); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertNull(live()); app.liveStatus.update(); assertNull(live());
    app.busy = false; app.changed();
    app.busy = true; app.turnId = "next"; app.changed(); app.liveStatus.update();
    assertNotNull(live());
  }
  @Test public void disconnectMakesOldApprovalInert() throws Exception {
    app.approvals.add(approval()); app.liveStatus.update();
    PendingIntent old = live().actions[0].actionIntent;
    app.disconnect(); old.send(); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, sent.size());
  }
  @Test public void streamingProgressReplacesPromptAndKeepsNewestText() {
    app.threadTitle = "用户最初的问题";
    app.items.add(new ChatItem("u", "userMessage", app.threadTitle));
    var service = Robolectric.buildService(ConnectionService.class).create();
    try {
      assertNull(live().extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
      ChatItem thinking = new ChatItem("r", "reasoning", "思考过程");
      thinking.detail = "检查项目结构"; app.items.add(thinking); app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      assertTrue(live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("检查项目结构"));
      thinking.detail += "，找到关键问题"; app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      assertTrue(live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("找到关键问题"));
      ChatItem command = new ChatItem("c", "commandExecution", "./gradlew test");
      command.detail = "旧输出".repeat(600) + "最新测试结果"; app.items.add(command); app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      assertTrue(live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().endsWith("最新测试结果"));
      ChatItem reply = new ChatItem("a", "agentMessage", "正在修复"); app.items.add(reply); app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      reply.text += "，现在已经完成"; app.changed();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      assertEquals("正在修复，现在已经完成", live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString());
      assertTrue((live().flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
      app.items.add(new ChatItem("u2", "userMessage", "新问题")); app.liveStatus.update();
      assertFalse(LiveStatusNotifications.details(app).contains("已经完成"));
    } finally { service.destroy(); }
  }

  @Test public void newApprovalAlertsOnceAndReappearsAfterHidingProgress() throws Exception {
    app.liveStatus.update(); live().deleteIntent.send();
    Shadows.shadowOf(Looper.getMainLooper()).idle(); assertNull(live());
    app.approvals.add(approval()); app.liveStatus.update();
    assertEquals(LiveStatusNotifications.ATTENTION_CHANNEL, live().getChannelId());
    assertEquals(NotificationManager.IMPORTANCE_HIGH, app.getSystemService(NotificationManager.class)
        .getNotificationChannel(LiveStatusNotifications.ATTENTION_CHANNEL).getImportance());
    assertEquals(0, live().flags & Notification.FLAG_ONLY_ALERT_ONCE);
    assertEquals("允许一次", live().actions[0].title.toString());
    Json.object(app.approvals.get(0), "params").put("reason", "新的描述"); app.liveStatus.update();
    assertTrue((live().flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
    live().deleteIntent.send(); Shadows.shadowOf(Looper.getMainLooper()).idle();
    app.liveStatus.update(); assertNull(live());
    app.approvals.clear(); app.approvals.add(approval()); app.changed(); app.liveStatus.update();
    assertNotNull(live()); assertEquals(0, live().flags & Notification.FLAG_ONLY_ALERT_ONCE);
    app.approvals.clear(); app.liveStatus.update();
    assertEquals(LiveStatusNotifications.CHANNEL, live().getChannelId());
  }

  @Test public void questionsShowActualQuestionAndRequestAnAlert() {
    app.approvals.add(Json.obj("id", 8, "method", "item/tool/requestUserInput", "params",
        Json.obj("questions", new org.json.JSONArray().put(Json.obj("question", "选择哪个部署环境？")))));
    app.liveStatus.update();
    assertEquals("RemoteGPT · 待回答", live().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    assertTrue(live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("选择哪个部署环境？"));
    assertEquals("填写信息", live().actions[0].title.toString());
    assertEquals(0, live().flags & Notification.FLAG_ONLY_ALERT_ONCE);
  }

  @Test public void ordinaryProgressUsesAllSpaceWithoutRedundantActions() {
    ChatItem command = new ChatItem("c", "commandExecution", "./gradlew test");
    command.detail = "过时的输出\n".repeat(25) + "最新结果\n测试通过";
    app.items.add(command); app.liveStatus.update();
    assertTrue(live().actions == null || live().actions.length == 0);
    String body = live().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
    assertTrue(body.endsWith("最新结果\n测试通过"));
    assertTrue(body.split("\n").length <= 8);
    assertNotNull(live().contentIntent);
    app.approvals.add(approval()); app.liveStatus.update();
    assertEquals(2, live().actions.length);
    assertEquals("允许一次", live().actions[0].title.toString());
    assertEquals("拒绝", live().actions[1].title.toString());
    app.approvals.clear(); app.liveStatus.update();
    assertTrue(live().actions == null || live().actions.length == 0);
  }

}
