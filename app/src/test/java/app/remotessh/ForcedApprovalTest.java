package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class ForcedApprovalTest {
  private RemoteApp app;
  private RpcClient rpc;
  private ByteArrayOutputStream sent;
  private PipedOutputStream server;
  private Method event;

  @Before public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.connected = true; app.threadId = "A";
    app.busy = true; app.turnId = "turn-A"; app.permissionMode = PermissionMode.DEFAULT;
    sent = new ByteArrayOutputStream(); server = new PipedOutputStream();
    rpc = client(new PipedInputStream(server), sent); rpc.start();
    event = RemoteApp.class.getDeclaredMethod("event", JSONObject.class); event.setAccessible(true);
    app.changed();
  }

  private RpcClient client(InputStream input, OutputStream output) throws Exception {
    RpcClient client = new RpcClient(input, output, new RpcClient.Events() {
      public void onEvent(JSONObject event) {}
      public void onClosed(Exception error) {}
    });
    Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, client);
    return client;
  }

  @After public void close() throws Exception {
    app.disconnect(); rpc.close(); server.close();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  private JSONObject request(String id, String thread, String method) {
    return Json.obj("id", id, "method", method, "params", Json.obj("threadId", thread, "turnId", "turn-" + thread, "command", "fixture command"));
  }
  private void emit(JSONObject value) throws Exception { event.invoke(app, value); }
  private void emit(String method, JSONObject params) throws Exception { emit(Json.obj("method", method, "params", params)); }
  private void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5);
    }
    assertTrue(condition.getAsBoolean());
  }
  private List<JSONObject> output() {
    List<JSONObject> values = new ArrayList<>();
    for (String line : sent.toString().split("\n")) if (!line.isEmpty()) {
      try { values.add(new JSONObject(line)); } catch (JSONException ignored) { /* concurrent trailing write */ }
    }
    return values;
  }
  private long answers() { return output().stream().filter(value -> !value.has("method")).count(); }
  private JSONObject turnRequest() throws Exception {
    await(() -> output().stream().anyMatch(value -> value.optString("method").equals("turn/start")));
    return output().stream().filter(value -> value.optString("method").equals("turn/start")).findFirst().get();
  }
  private void reply(JSONObject request, JSONObject result, boolean error) throws Exception {
    server.write((Json.obj("id", request.opt("id"), error ? "error" : "result", result) + "\n").getBytes("UTF-8")); server.flush();
  }

  @Test public void acceptsCurrentAndFuturePermissionRequestsButKeepsQuestionsInteractive() throws Exception {
    emit(request("command", "A", "item/commandExecution/requestApproval"));
    assertTrue(app.forcedApprovals.enable("A"));
    assertEquals(PermissionMode.FULL, app.permissionMode);
    assertNull(app.visibleApproval());
    await(() -> answers() == 1 && app.approvals.isEmpty());
    emit(request("file", "A", "item/fileChange/requestApproval"));
    JSONObject question = request("question", "A", "item/tool/requestUserInput");
    emit(question);
    await(() -> answers() == 2 && app.approvals.size() == 1);
    assertSame(question, app.visibleApproval());
    for (JSONObject value : output()) if (!value.has("method"))
      assertEquals("accept", value.getJSONObject("result").getString("decision"));
  }

  @Test public void backgroundGrantAndExceptionNeverLeakToSelectedConversation() throws Exception {
    JSONObject background = request("background", "B", "item/commandExecution/requestApproval");
    emit(background);
    app.observer = () -> { assertEquals("A", app.threadId); assertFalse(app.forcedApprovals.active()); };
    RuntimeException expected = new RuntimeException("scoped failure");
    try {
      app.inConversation("B", () -> {
        assertTrue(app.forcedApprovals.enable("B"));
        throw expected;
      });
      fail("Expected failure");
    } catch (RuntimeException actual) { assertSame(expected, actual); }
    await(() -> answers() == 1);
    assertEquals("A", app.threadId); assertEquals(PermissionMode.DEFAULT, app.permissionMode);
    assertFalse(app.forcedApprovals.active());
    app.inConversation("B", () -> assertTrue(app.forcedApprovals.active()));
    JSONObject selected = request("selected", "A", "item/fileChange/requestApproval");
    emit(selected);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertSame(selected, app.visibleApproval()); assertEquals(1, answers());
    app.observer = null;
  }

  @Test public void duplicateRequestIsAnsweredOnceAndSettledCopiesAreRemoved() throws Exception {
    JSONObject first = request("same", "A", "item/commandExecution/requestApproval");
    emit(first); assertTrue(app.forcedApprovals.enable("A"));
    emit(new JSONObject(first.toString()));
    await(() -> answers() == 1 && app.approvals.isEmpty());
    assertTrue(app.forcedApprovals.active());
  }

  @Test public void replayDoesNotApproveUntilBarrierCompletesAndCompletionDoesNotEndGrant() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    emit("remote/replayStarted", Json.obj("threadId", "A"));
    emit(request("replayed", "A", "item/commandExecution/requestApproval"));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, answers()); assertNull(app.visibleApproval());
    emit("remote/replayCompleted", Json.obj("threadId", "A"));
    await(() -> answers() == 1);
    emit("turn/completed", Json.obj("threadId", "A", "turn", Json.obj("id", "turn-A", "status", "completed")));
    assertTrue(app.forcedApprovals.active());
  }

  @Test public void nextSuccessfulTurnAppliesFullPermissionAndEndsTemporaryGrant() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.busy = false; app.send("next turn");
    JSONObject request = turnRequest();
    assertEquals("dangerFullAccess", request.getJSONObject("params").getJSONObject("sandboxPolicy").getString("type"));
    assertTrue(app.forcedApprovals.active());
    reply(request, Json.obj("turn", Json.obj("id", "new-turn")), false);
    await(() -> !app.forcedApprovals.active());
    assertEquals(PermissionMode.FULL, app.effectivePermissionMode);
    JSONObject manual = request("new-permission", "A", "item/commandExecution/requestApproval");
    emit(manual); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertSame(manual, app.visibleApproval()); assertEquals(0, answers());
  }

  @Test public void lateStartCannotApplyANewerPermissionOrClearANewerGrant() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.busy = false; app.send("sent under full");
    JSONObject request = turnRequest();
    app.choosePermission(PermissionMode.READ_ONLY);
    reply(request, Json.obj("turn", Json.obj("id", "new-turn")), false);
    await(() -> app.turnId.equals("new-turn"));
    assertEquals(PermissionMode.READ_ONLY, app.permissionMode);
    assertEquals(PermissionMode.FULL, app.effectivePermissionMode);
    assertTrue(app.forcedApprovals.active());
    sent.reset(); app.busy = false; app.send("sent under read only");
    request = turnRequest();
    assertEquals("readOnly", request.getJSONObject("params").getJSONObject("sandboxPolicy").getString("type"));
    reply(request, Json.obj("turn", Json.obj("id", "read-turn")), false);
    await(() -> !app.forcedApprovals.active());
    assertEquals(PermissionMode.READ_ONLY, app.effectivePermissionMode);
  }

  @Test public void rejectedTurnDoesNotPretendNewPermissionIsEffective() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.busy = false; app.send("rejected turn");
    reply(turnRequest(), Json.obj("code", -32602, "message", "fixture rejected"), true);
    await(() -> !app.busy);
    assertTrue(app.forcedApprovals.active());
    assertNotEquals(PermissionMode.FULL, app.effectivePermissionMode);
  }

  @Test public void failedApprovalWriteRestoresManualPromptWithoutAutomaticRetryLoop() throws Exception {
    rpc.close();
    rpc = client(new ByteArrayInputStream(new byte[0]), new OutputStream() {
      public void write(int value) throws IOException { throw new IOException("fixture write failure"); }
    });
    JSONObject request = request("failed", "A", "item/fileChange/requestApproval");
    emit(request); assertTrue(app.forcedApprovals.enable("A"));
    await(() -> !app.forcedApprovals.active());
    assertSame(request, app.visibleApproval());
    assertTrue(app.error.contains("fixture write failure"));
  }

  @Test public void disconnectClearsGrantAndNoNewThreadInheritsIt() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.newChat(); assertFalse(app.forcedApprovals.active());
    app.inConversation("A", () -> assertTrue(app.forcedApprovals.active()));
    app.disconnect();
    assertFalse(app.forcedApprovalState.enabled);
    app.connected = true; app.changed();
    assertFalse(app.forcedApprovals.active());
  }

  @Test public void readonlyHistoryCannotEnableAutomaticPermissions() {
    app.readOnly = true;
    assertFalse(app.forcedApprovals.enable("A"));
    assertEquals(PermissionMode.DEFAULT, app.permissionMode);
  }

  @Test public void readonlyTransitionCannotHideAnApprovalThatCannotBeSubmitted() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.readOnly = true;
    JSONObject request = request("readonly", "A", "item/fileChange/requestApproval");
    emit(request); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertSame(request, app.visibleApproval()); assertEquals(0, answers());
  }

  @Test public void invalidRequestIdentityCannotBeAutomaticallyAllowedOrHidden() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    JSONObject request = request("invalid", "A", "item/fileChange/requestApproval");
    request.put("id", JSONObject.NULL);
    emit(request); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertSame(request, app.visibleApproval()); assertEquals(0, answers());
  }

  @Test public void grantMadeAfterTurnSubmissionSurvivesTheEarlierSuccess() throws Exception {
    app.busy = false; app.permissionMode = PermissionMode.FULL; app.send("already submitted");
    JSONObject request = turnRequest();
    assertTrue(app.forcedApprovals.enable("A"));
    reply(request, Json.obj("turn", Json.obj("id", "older-submission")), false);
    await(() -> app.turnId.equals("older-submission"));
    assertTrue(app.forcedApprovals.active());
  }

  @Test public void backgroundTurnSuccessEndsOnlyItsOwnGrant() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    app.busy = false; app.send("A next turn");
    JSONObject request = turnRequest();
    app.newChat(); app.threadId = "B"; app.busy = true; app.turnId = "turn-B"; app.changed();
    assertTrue(app.forcedApprovals.enable("B"));
    app.observer = () -> { assertEquals("B", app.threadId); assertTrue(app.forcedApprovals.active()); };
    reply(request, Json.obj("turn", Json.obj("id", "new-A")), false);
    await(() -> !app.conversationStore.get("A").forcedApproval.enabled);
    assertEquals("B", app.threadId); assertTrue(app.forcedApprovals.active());
    assertEquals("turn-B", app.turnId);
    app.observer = null;
  }
}
