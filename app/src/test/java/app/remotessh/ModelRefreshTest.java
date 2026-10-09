package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ModelRefreshTest {
  RemoteApp app;
  RpcClient rpc;
  ByteArrayOutputStream output;
  @Before public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true;
    output = new ByteArrayOutputStream();
    rpc = new RpcClient(new ByteArrayInputStream(new byte[0]), output, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, rpc);
    app.models = new JSONArray().put(Json.obj("model", "old"));
  }
  @After public void close() { rpc.close(); }
  void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5);
    }
    assertTrue(condition.getAsBoolean());
  }
  JSONObject request(int index) throws Exception {
    waitFor(() -> output.toString().split("\n").length > index && output.toString().endsWith("\n"));
    return new JSONObject(output.toString("UTF-8").split("\n")[index]);
  }
  void reply(JSONObject request, String type, JSONObject value) throws Exception {
    Method receive = RpcClient.class.getDeclaredMethod("receive", JSONObject.class); receive.setAccessible(true);
    receive.invoke(rpc, Json.obj("id", request.get("id"), type, value));
  }
  @Test public void refreshIsAtomicAndCanDiscoverNewModelsWithoutReconnect() throws Exception {
    app.refreshModels(); app.refreshModels();
    JSONObject first = request(0);
    assertEquals("model/list", first.getString("method"));
    reply(first, "result", Json.obj("data", new JSONArray().put(Json.obj("model", "new")), "nextCursor", "next"));
    JSONObject second = request(1);
    assertEquals("next", second.getJSONObject("params").getString("cursor"));
    assertEquals("old", ModelOptions.id(app.models.getJSONObject(0)));
    reply(second, "result", Json.obj("data", new JSONArray().put(Json.obj("model", "future"))));
    waitFor(() -> !app.modelsLoading);
    assertEquals(2, app.models.length());
    app.refreshModels();
    reply(request(2), "result", Json.obj("data", new JSONArray().put(Json.obj("model", "gpt-6.1-sol"))));
    waitFor(() -> !app.modelsLoading);
    assertEquals("gpt-6.1-sol", ModelOptions.id(app.models.getJSONObject(0)));
  }
  @Test public void errorKeepsPreviousCatalogAndAllowsRetry() throws Exception {
    app.refreshModels();
    reply(request(0), "error", Json.obj("code", -1, "message", "unavailable"));
    waitFor(() -> !app.modelsLoading);
    assertEquals("old", ModelOptions.id(app.models.getJSONObject(0)));
    assertTrue(app.modelsStatus.contains("刷新失败"));
    app.refreshModels();
    reply(request(1), "result", Json.obj("data", new JSONArray()));
    waitFor(() -> !app.modelsLoading);
    assertEquals(0, app.models.length());
  }
  @Test public void staleResponseCannotReplaceAnotherConnectionsCatalog() throws Exception {
    app.refreshModels(); JSONObject pending = request(0);
    Field g = RemoteApp.class.getDeclaredField("generation"); g.setAccessible(true); g.setInt(app, g.getInt(app) + 1);
    reply(pending, "result", Json.obj("data", new JSONArray().put(Json.obj("model", "stale"))));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
    assertEquals("old", ModelOptions.id(app.models.getJSONObject(0)));
  }
}
