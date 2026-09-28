package app.remotessh;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConversationStateTest {
  private RemoteApp app;
  private Method event;

  @Before
  public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.threadId = "active";
    event = RemoteApp.class.getDeclaredMethod("event", JSONObject.class);
    event.setAccessible(true);
  }

  private void emit(String method, JSONObject params) throws Exception {
    event.invoke(app, Json.obj("method", method, "params", params));
  }

  @Test public void replayDoesNotResubmitQueueOrAllowSteeringBeforeCatchup() throws Exception {
    app.profile = new Profile(); app.connected = true; app.busy = true; app.turnId = "run";
    assertTrue(app.queueMessage("保留到手动发送"));
    emit("remote/replayStarted", Json.obj());
    assertTrue(app.replaying); assertFalse(app.canSteer());
    emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("status", "completed")));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals("保留到手动发送", app.queuedMessage());
    assertEquals("", app.error);
    emit("turn/started", Json.obj("threadId", "active", "turn", Json.obj("id", "current")));
    emit("remote/replayCompleted", Json.obj());
    assertFalse(app.replaying); assertTrue(app.canSteer()); assertEquals("current", app.turnId);
  }

  @Test
  public void queuedTextWaitsForCompletionAndDoesNotIncludeDraftImages() throws Exception {
    java.io.PipedOutputStream server = new java.io.PipedOutputStream();
    java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
    RpcClient client = new RpcClient(new java.io.PipedInputStream(server), sent, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    java.lang.reflect.Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, client);
    app.profile = new Profile(); app.connected = true; app.busy = true; app.turnId = "run";
    client.start();
    try {
      assertTrue(app.queueMessage("稍后执行")); assertFalse(app.queueMessage("不要覆盖"));
      assertEquals(0, sent.size());
      app.draftImages.add("/tmp/unrelated.png");
      emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("status", "completed")));
      waitFor(() -> sent.toString().replace("\\/", "/").contains("turn/start"));
      JSONObject turnStart = null;
      for (String line : sent.toString("UTF-8").trim().split("\n")) {
        JSONObject message = new JSONObject(line);
        if (message.optString("method").equals("turn/start")) turnStart = message;
      }
      assertNotNull(turnStart);
      org.json.JSONArray input = turnStart.getJSONObject("params").getJSONArray("input");
      assertEquals(1, input.length()); assertEquals("稍后执行", input.getJSONObject(0).getString("text"));
      assertEquals("", app.queuedMessage()); assertEquals(1, app.draftImages.size());
    } finally { client.close(); server.close(); field.set(app, null); }
  }

  @Test
  public void queueIsScopedToConversationAndPausedOnDisconnect() {
    app.profile = new Profile(); app.connected = true; app.busy = true; app.turnId = "run";
    assertTrue(app.queueMessage("pending"));
    app.threadId = "other"; assertEquals("", app.queuedMessage());
    app.threadId = "active"; app.disconnect();
    assertEquals("pending", app.queuedMessage());
    app.sendQueuedMessage(); assertEquals("pending", app.queuedMessage());
  }

  @Test
  public void reasoningDeltasSurviveEmptyCompletion() throws Exception {
    emit("item/started", Json.obj("threadId", "active", "item", Json.obj("id", "r", "type", "reasoning")));
    emit("item/reasoning/textDelta", Json.obj("threadId", "active", "itemId", "r", "delta", "检查测试"));
    emit("item/reasoning/summaryPartAdded", Json.obj("threadId", "active", "itemId", "r"));
    emit("item/reasoning/summaryTextDelta", Json.obj("threadId", "active", "itemId", "r", "delta", "验证修复"));
    emit("item/completed", Json.obj("threadId", "active", "item", Json.obj("id", "r", "type", "reasoning", "summary", new org.json.JSONArray())));
    assertEquals("检查测试\n\n验证修复", app.items.get(0).detail);
  }

  @Test
  public void rejectedSteerKeepsActiveTurnAndDraft() throws Exception {
    java.io.PipedOutputStream server = new java.io.PipedOutputStream();
    java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
    RpcClient client = new RpcClient(new java.io.PipedInputStream(server), sent, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    java.lang.reflect.Field field = RemoteApp.class.getDeclaredField("rpc");
    field.setAccessible(true); field.set(app, client); client.start();
    app.connected = true; app.busy = true; app.turnId = "running";
    try {
      assertTrue(app.steer("先修测试"));
      assertFalse(app.steer("重复点击"));
      waitFor(() -> sent.size() > 0);
      JSONObject request = new JSONObject(sent.toString("UTF-8").trim());
      assertEquals("turn/steer", request.getString("method"));
      assertEquals("running", request.getJSONObject("params").getString("expectedTurnId"));
      assertFalse(request.getJSONObject("params").has("approvalPolicy"));
      server.write((Json.obj("id", request.get("id"), "error", Json.obj("code", -32600, "message", "turn ended")) + "\n").getBytes("UTF-8"));
      server.flush(); waitFor(() -> !app.steering);
      assertTrue(app.busy); assertEquals("running", app.turnId);
      assertEquals("先修测试", app.unsentText);
      assertTrue(app.items.isEmpty());
      assertTrue(app.steer("先修测试"));
      waitFor(() -> sent.toString().split("\n").length == 2);
      JSONObject retry = new JSONObject(sent.toString("UTF-8").trim().split("\n")[1]);
      server.write((Json.obj("id", retry.get("id"), "result", Json.obj("turnId", "running")) + "\n").getBytes("UTF-8"));
      server.flush(); waitFor(() -> !app.steering);
      assertTrue(app.busy); assertEquals("", app.unsentText);
      assertEquals("先修测试", app.items.get(0).text);
      emit("item/started", Json.obj("threadId", "active", "item", Json.obj("id", "echo", "type", "userMessage",
          "content", new org.json.JSONArray().put(Json.obj("type", "text", "text", "先修测试")))));
      assertEquals(1, app.items.size());
    } finally { client.close(); server.close(); field.set(app, null); }
  }

  @Test
  public void idleRuntimeOverridesAbandonedHistoricalTurn() throws Exception {
    app.profile = new Profile();
    Method apply = RemoteApp.class.getDeclaredMethod("applyThread", JSONObject.class, String.class, boolean.class);
    apply.setAccessible(true);
    apply.invoke(app, Json.obj("thread", Json.obj("id", "active", "cwd", "/projects/current",
        "status", Json.obj("type", "idle"), "turns", new org.json.JSONArray().put(
            Json.obj("id", "abandoned", "status", "inProgress")))), "active", false);
    assertFalse(app.busy);
    assertEquals("", app.turnId);
    assertEquals("/projects/current", app.currentProject());
    app.profile.cwd = "/projects/browsing";
    assertEquals("/projects/current", app.currentProject());
  }

  @Test
  public void onlyLastHistoricalTurnCanBeActiveOnOlderServers() throws Exception {
    app.profile = new Profile();
    Method apply = RemoteApp.class.getDeclaredMethod("applyThread", JSONObject.class, String.class, boolean.class);
    apply.setAccessible(true);
    apply.invoke(app, Json.obj("thread", Json.obj("id", "active", "turns", new org.json.JSONArray()
        .put(Json.obj("id", "old", "status", "inProgress"))
        .put(Json.obj("id", "last", "status", "completed")))), "active", false);
    assertFalse(app.busy);
  }

  @Test
  public void lifecycleClearsBusyAndInvalidatesClosedThread() throws Exception {
    app.busy = true;
    app.turnId = "old";
    emit("thread/status/changed", Json.obj("threadId", "active", "status", Json.obj("type", "idle")));
    assertFalse(app.busy);
    assertEquals("", app.turnId);
    java.lang.reflect.Field loaded = RemoteApp.class.getDeclaredField("loadedThreads");
    loaded.setAccessible(true);
    ((java.util.Set<String>) loaded.get(app)).add("active");
    emit("thread/closed", Json.obj("threadId", "active"));
    assertFalse(((java.util.Set<?>) loaded.get(app)).contains("active"));
    assertTrue(app.readOnly);
    assertFalse(app.busy);
  }

  @Test
  public void quotaNotificationsAreAccountWide() throws Exception {
    emit("account/rateLimits/updated", Json.obj("rateLimits", Json.obj("primary", Json.obj("usedPercent", 15))));
    assertEquals("剩余用量 · 主额度 85%", app.usage.summary());
  }

  @Test
  public void writerConflictReadsHistoryWithoutRetryingResume() throws Exception {
    java.io.PipedOutputStream server = new java.io.PipedOutputStream();
    java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
    RpcClient client =
        new RpcClient(
            new java.io.PipedInputStream(server, 16384),
            sent,
            new RpcClient.Events() {
              public void onEvent(JSONObject e) {}

              public void onClosed(Exception e) {}
            });
    java.lang.reflect.Field rpc = RemoteApp.class.getDeclaredField("rpc");
    rpc.setAccessible(true);
    rpc.set(app, client);
    app.profile = new Profile();
    app.connected = true;
    client.start();
    try {
      app.resume("busy-thread");
      waitFor(() -> sent.toString().replace("\\/", "/").contains("thread/resume"));
      server.write(
          (Json.obj(
                      "id",
                      1,
                      "error",
                      Json.obj(
                          "code",
                          -32600,
                          "message",
                          "thread busy-thread already has an active writer"))
                  + "\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      server.flush();
      waitFor(() -> sent.toString().replace("\\/", "/").contains("thread/read"));
      assertTrue(sent.toString().contains("includeTurns"));
      server.write(
          (Json.obj(
                      "id",
                      2,
                      "result",
                      Json.obj(
                          "thread",
                          Json.obj(
                              "id",
                              "busy-thread",
                              "cwd",
                              "/projects/work",
                              "turns",
                              new org.json.JSONArray()
                                  .put(
                                      Json.obj(
                                          "id",
                                          "turn",
                                          "status",
                                          "inProgress",
                                          "items",
                                          new org.json.JSONArray()
                                              .put(
                                                  Json.obj(
                                                      "id",
                                                      "a",
                                                      "type",
                                                      "agentMessage",
                                                      "text",
                                                      "existing history")))))))
                  + "\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      server.flush();
      waitFor(() -> app.readOnly);
      assertEquals("busy-thread", app.threadId);
      assertEquals("existing history", app.items.get(0).text);
      assertFalse(app.busy);
      assertEquals("", app.turnId);
      assertEquals(1, sent.toString().replace("\\/", "/").split("thread/resume", -1).length - 1);
      // Releasing the external writer allows an explicit retry of the same thread.
      app.resume("busy-thread");
      waitFor(() -> sent.toString().replace("\\/", "/").split("thread/resume", -1).length == 3);
      server.write((Json.obj("id", 3, "result", Json.obj("thread", Json.obj("id", "busy-thread",
          "cwd", "/projects/work", "status", Json.obj("type", "idle"), "turns", new org.json.JSONArray()))) + "\n")
          .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      server.flush();
      waitFor(() -> !app.busy);
      assertFalse(app.readOnly);
      assertEquals("busy-thread", app.threadId);
      app.newChat();
      assertFalse(sent.toString().replace("\\/", "/").contains("thread/unsubscribe"));
      assertEquals("", app.threadId);
    } finally {
      client.close();
      server.close();
      rpc.set(app, null);
    }
  }

  private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
    long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < until) {
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      Thread.sleep(5);
    }
    assertTrue("Async operation timed out", condition.getAsBoolean());
  }

  @Test
  public void operationErrorExpiresButDiagnosticRemains() {
    app.reportFailure(
        "thread/resume",
        Json.obj("threadId", "busy"),
        new RpcException(
            "thread/resume",
            Json.obj("code", -32600, "message", "thread busy already has an active writer")));
    assertTrue(app.error.contains("-32600"));
    assertTrue(app.error.contains("thread/resume"));
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofSeconds(7));
    assertEquals("", app.error);
    assertTrue(app.lastError.contains("-32600"));
  }

  @Test
  public void onlyExactWriterConflictTriggersFallback() {
    assertTrue(
        RemoteApp.isWriterConflict(
            new RpcException(
                "thread/resume",
                Json.obj("code", -32600, "message", "thread x already has an active writer"))));
    assertFalse(
        RemoteApp.isWriterConflict(
            new RpcException(
                "thread/resume", Json.obj("code", -32600, "message", "thread not found"))));
    assertFalse(
        RemoteApp.isWriterConflict(new java.io.IOException("already has an active writer")));
  }

  @Test
  public void readOnlyHistoryCannotSendOrStopAnotherClient() {
    app.connected = true;
    app.readOnly = true;
    app.send("must not send");
    assertTrue(app.items.isEmpty());
    assertFalse(app.busy);
    assertEquals("", app.unsentText);
  }

  @Test
  public void isolatesNotificationsFromOtherConversations() throws Exception {
    emit(
        "item/agentMessage/delta", Json.obj("threadId", "other", "itemId", "a", "delta", "ignore"));
    assertTrue(app.items.isEmpty());
  }

  @Test
  public void finalItemReplacesStreamedText() throws Exception {
    emit("item/agentMessage/delta", Json.obj("threadId", "active", "itemId", "a", "delta", "你好"));
    emit("item/agentMessage/delta", Json.obj("threadId", "active", "itemId", "a", "delta", "，世界"));
    assertEquals("你好，世界", app.items.get(0).text);
    emit(
        "item/completed",
        Json.obj(
            "threadId",
            "active",
            "item",
            Json.obj("id", "a", "type", "agentMessage", "text", "最终答案")));
    assertEquals(1, app.items.size());
    assertEquals("最终答案", app.items.get(0).text);
  }

  @Test
  public void serverEchoDeduplicatesOptimisticUserMessage() throws Exception {
    app.unsentText = "hello";
    app.items.add(new ChatItem("local-1", "userMessage", "hello"));
    emit(
        "item/started",
        Json.obj(
            "threadId",
            "active",
            "item",
            Json.obj(
                "id",
                "u",
                "type",
                "userMessage",
                "content",
                new org.json.JSONArray().put(Json.obj("type", "text", "text", "hello")))));
    assertEquals(1, app.items.size());
    assertEquals("u", app.items.get(0).id);
    assertEquals("", app.unsentText);
  }

  @Test
  public void resolvedApprovalIsRemovedByRequestId() throws Exception {
    event.invoke(
        app,
        Json.obj(
            "id",
            "request-1",
            "method",
            "item/fileChange/requestApproval",
            "params",
            Json.obj("threadId", "active")));
    assertEquals(1, app.approvals.size());
    emit("serverRequest/resolved", Json.obj("threadId", "active", "requestId", "request-1"));
    assertTrue(app.approvals.isEmpty());
  }

  @Test
  public void unknownApprovalTypeIsNotTreatedAsCommandApproval() throws Exception {
    event.invoke(
        app,
        Json.obj(
            "id", 2, "method", "item/newCapability/requestApproval", "params", new JSONObject()));
    assertTrue(app.approvals.isEmpty());
    assertTrue(app.error.contains("暂不支持"));
  }
}
