package app.remotessh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BackgroundConnectionTest {
  @Test public void readOnlyPollingRefreshesContentWithoutClaimingWriter() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.connected = true; app.readOnly = true; app.threadId = "external";
    java.io.PipedOutputStream server = new java.io.PipedOutputStream();
    java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
    RpcClient client = new RpcClient(new java.io.PipedInputStream(server), sent, new RpcClient.Events() {
      public void onEvent(org.json.JSONObject e) {} public void onClosed(Exception e) {}
    });
    java.lang.reflect.Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, client);
    client.start();
    try {
      app.pollReadOnly(); app.pollReadOnly();
      waitFor(() -> sent.size() > 0);
      org.json.JSONObject request = new org.json.JSONObject(sent.toString("UTF-8").trim());
      assertEquals("thread/read", request.getString("method"));
      server.write((Json.obj("id", request.get("id"), "result", Json.obj("thread", Json.obj("id", "external",
          "status", Json.obj("type", "active"), "turns", new org.json.JSONArray().put(Json.obj("id", "t", "status", "inProgress",
          "items", new org.json.JSONArray().put(Json.obj("id", "m", "type", "agentMessage", "text", "更新后的输出"))))))) + "\n").getBytes("UTF-8"));
      server.flush(); waitFor(() -> !app.items.isEmpty());
      assertEquals("更新后的输出", app.items.get(0).text);
      assertTrue(app.readOnly); assertTrue(app.remoteRunning); assertFalse(app.busy);
      assertEquals(1, sent.toString().trim().split("\n").length);
    } finally { client.close(); server.close(); field.set(app, null); }
  }
  private void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(5);
    }
    assertTrue(condition.getAsBoolean());
  }

  @Test public void foregroundServiceReleasesWakeLockWhenStopped() throws Exception {
    org.robolectric.android.controller.ServiceController<ConnectionService> service = Robolectric.buildService(ConnectionService.class).create();
    java.lang.reflect.Field field = ConnectionService.class.getDeclaredField("wakeLock"); field.setAccessible(true);
    android.os.PowerManager.WakeLock lock = (android.os.PowerManager.WakeLock) field.get(service.get());
    assertTrue(lock.isHeld()); service.destroy(); assertFalse(lock.isHeld());
  }
  @Test public void backgroundingDoesNotDisconnectOrDiscardActiveTurn() {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true; app.busy = true; app.threadId = "active"; app.turnId = "turn";
    app.setForeground(false);
    assertTrue(app.connected); assertTrue(app.busy); assertEquals("turn", app.turnId);
    assertTrue(app.threadRunning(Json.obj("id", "active")));
    assertTrue(app.threadRunning(Json.obj("id", "other", "status", Json.obj("type", "active"))));
    assertFalse(app.threadRunning(Json.obj("id", "idle", "status", Json.obj("type", "idle"))));
    app.disconnect();
    assertFalse(app.threadRunning(Json.obj("id", "other", "status", Json.obj("type", "active"))));
  }
}
