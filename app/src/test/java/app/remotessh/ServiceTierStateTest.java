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
public class ServiceTierStateTest {
  RemoteApp app; RpcClient rpc; ByteArrayOutputStream output;
  @Before public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication(); app.profile = new Profile(); app.connected = true;
    app.threadId = "A"; app.model = "modern"; app.effectiveModel = "modern";
    app.modelSettings.configKnown = true; app.modelSettings.model = "configured";
    app.inheritedTierKnown = true; app.inheritedServiceTier = "default";
    app.models = new JSONArray().put(Json.obj("model", "modern", "isDefault", true, "serviceTiers",
        new JSONArray().put(Json.obj("id", "priority", "name", "Fast", "description", "remote details"))))
        .put(Json.obj("model", "configured", "serviceTiers", new JSONArray().put(Json.obj("id", "future-speed", "name", "Future"))))
        .put(Json.obj("model", "ordinary", "serviceTiers", new JSONArray()));
    output = new ByteArrayOutputStream();
    rpc = new RpcClient(new ByteArrayInputStream(new byte[0]), output, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    Field f = RemoteApp.class.getDeclaredField("rpc"); f.setAccessible(true); f.set(app, rpc);
  }
  @After public void close() { rpc.close(); }
  void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
    long end = System.nanoTime() + 4_000_000_000L;
    while (!condition.getAsBoolean() && System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5); }
    assertTrue(condition.getAsBoolean());
  }
  JSONObject request(int index) throws Exception {
    waitFor(() -> output.toString().split("\n").length > index && output.toString().endsWith("\n"));
    return new JSONObject(output.toString("UTF-8").split("\n")[index]);
  }
  void reply(JSONObject request, JSONObject value) throws Exception {
    Method m = RpcClient.class.getDeclaredMethod("receive", JSONObject.class); m.setAccessible(true);
    m.invoke(rpc, Json.obj("id", request.get("id"), "result", value));
    waitFor(() -> app.unsentText.isEmpty()); app.busy = false;
  }
  @Test public void sendsAdvertisedTierThenExplicitStandardAndRestoresDefault() throws Exception {
    app.chooseServiceTier("priority"); app.send("fast"); JSONObject first = request(0);
    assertEquals("priority", first.getJSONObject("params").getString("serviceTier"));
    reply(first, Json.obj("turn", Json.obj("id", "t1"))); assertTrue(app.tierOverrideApplied);
    app.chooseServiceTier("default"); app.send("ordinary"); JSONObject second = request(1);
    assertEquals("default", second.getJSONObject("params").getString("serviceTier"));
    reply(second, Json.obj("turn", Json.obj("id", "t2")));
    app.inheritedServiceTier = "priority";
    app.chooseServiceTier("default"); app.send("ordinary again"); JSONObject third = request(2);
    reply(third, Json.obj("turn", Json.obj("id", "t3"))); assertTrue(app.tierOverrideApplied);
    app.chooseServiceTier(""); app.send("restore remote"); JSONObject fourth = request(3);
    assertEquals("priority", fourth.getJSONObject("params").getString("serviceTier"));
  }
  @Test public void untouchedDefaultDoesNotSendAnOverride() throws Exception {
    app.send("default"); assertFalse(request(0).getJSONObject("params").has("serviceTier"));
  }
  @Test public void switchingAwayFromAnInheritedFastModelClearsUnsupportedTier() throws Exception {
    app.effectiveServiceTier = "priority"; app.inheritedServiceTier = "priority";
    app.model = "ordinary"; app.send("other model");
    assertEquals("default", request(0).getJSONObject("params").getString("serviceTier"));
  }
  @Test public void legacyCatalogKeepsExactAdvertisedRequestValue() throws Exception {
    app.models = new JSONArray().put(Json.obj("model", "modern", "additionalSpeedTiers", new JSONArray().put("fast")));
    app.chooseServiceTier("fast"); app.send("legacy");
    assertEquals("fast", request(0).getJSONObject("params").getString("serviceTier"));
  }
  @Test public void pendingResponseDoesNotOverwriteSelectionForNextTurn() throws Exception {
    app.chooseServiceTier("priority"); app.send("fast"); JSONObject first = request(0);
    app.chooseServiceTier("default"); reply(first, Json.obj("turn", Json.obj("id", "t1")));
    assertEquals("default", app.serviceTier); assertTrue(app.tierOverrideApplied);
    app.send("ordinary next"); assertEquals("default", request(1).getJSONObject("params").getString("serviceTier"));
  }
  @Test public void oneProjectsFeatureGateDoesNotBlockAnotherConversation() throws Exception {
    app.modelSettings.cwd = "/project-A"; app.modelSettings.fastEnabled = false;
    app.threadCwd = "/project-B"; app.chooseServiceTier("priority"); app.send("other project");
    assertEquals("priority", request(0).getJSONObject("params").getString("serviceTier"));
  }
  @Test public void failedFeatureRefreshKeepsKnownRemoteDisable() throws Exception {
    app.modelSettings.globalFeaturesKnown = true; app.modelSettings.globalFastEnabled = false; app.modelSettings.fastEnabled = false;
    app.refreshModelSettings();
    Method receive = RpcClient.class.getDeclaredMethod("receive", JSONObject.class); receive.setAccessible(true);
    receive.invoke(rpc, Json.obj("id", request(0).get("id"), "result", Json.obj("config", Json.obj("model", "modern"))));
    receive.invoke(rpc, Json.obj("id", request(1).get("id"), "result", new JSONObject()));
    receive.invoke(rpc, Json.obj("id", request(2).get("id"), "error", Json.obj("code", -32601, "message", "unavailable")));
    waitFor(() -> !app.modelSettingsLoading);
    assertFalse(app.speedSelectionEnabled()); assertTrue(app.modelSettings.globalFeaturesKnown);
  }
  @Test public void metadataChangesRejectSendWithoutLosingDraftOrCreatingTask() {
    app.serviceTier = "priority"; app.model = "ordinary"; app.send("keep me");
    assertEquals(0, output.size()); assertFalse(app.busy); assertTrue(app.items.isEmpty());
    assertTrue(app.error.contains("重新选择"));
    app.model = "modern"; app.modelSettings.fastEnabled = false; app.send("disabled");
    assertEquals(0, output.size());
  }
  @Test public void defaultModelCapabilitiesUseConfigurationInsteadOfCatalogRecommendation() {
    app.threadId = ""; app.effectiveModel = ""; app.model = "";
    assertEquals("configured", ModelOptions.id(app.selectedModel("")));
    app.modelSettings.configKnown = false;
    assertNull(app.selectedModel(""));
  }
  @Test public void newThreadAndFirstTurnUseSameSelectedTier() throws Exception {
    app.threadId = ""; app.inheritedTierKnown = false; app.chooseServiceTier("priority");
    app.send("new"); JSONObject start = request(0);
    assertEquals("thread/start", start.getString("method"));
    assertEquals("priority", start.getJSONObject("params").getString("serviceTier"));
    Method receive = RpcClient.class.getDeclaredMethod("receive", JSONObject.class); receive.setAccessible(true);
    receive.invoke(rpc, Json.obj("id", start.get("id"), "result", Json.obj("model", "modern", "serviceTier", "priority", "thread", Json.obj("id", "new", "cwd", ""))));
    JSONObject turn = request(1);
    assertEquals("priority", turn.getJSONObject("params").getString("serviceTier"));
    assertEquals("default", app.inheritedServiceTier);
  }
  @Test public void choicesStayIsolatedAndSurviveReconnectWithOldReplayBaseline() throws Exception {
    app.chooseServiceTier("priority"); app.changed(); app.newChat();
    assertEquals("", app.serviceTier);
    app.inConversation("A", () -> assertEquals("priority", app.serviceTier));
    app.threadId = ""; app.changed();
    // A new Application facade has no in-memory state from the previous connection.
    RemoteApp fresh = new RemoteApp();
    Method attach = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", android.content.Context.class);
    attach.setAccessible(true); attach.invoke(fresh, app);
    fresh.profile = app.profile; fresh.vault = app.vault; fresh.connected = true;
    fresh.inConversation("A", () -> {
      assertEquals("priority", fresh.serviceTier);
      try {
        Method apply = RemoteApp.class.getDeclaredMethod("applyThread", JSONObject.class, String.class, boolean.class); apply.setAccessible(true);
        apply.invoke(fresh, Json.obj("model", "modern", "serviceTier", "default", "remoteReplay", true,
            "thread", Json.obj("id", "A", "turns", new JSONArray())), "A", false);
      } catch (Exception e) { throw new RuntimeException(e); }
      assertEquals("priority", fresh.serviceTier); assertEquals("default", fresh.inheritedServiceTier);
    });
  }
  @Test public void optionalSettingsResponsesFromAnotherConnectionAreIgnored() throws Exception {
    app.refreshModelSettings(); JSONObject config = request(0);
    Field generation = RemoteApp.class.getDeclaredField("generation"); generation.setAccessible(true); generation.setInt(app, 99);
    Method receive = RpcClient.class.getDeclaredMethod("receive", JSONObject.class); receive.setAccessible(true);
    receive.invoke(rpc, Json.obj("id", config.get("id"), "result", Json.obj("config", Json.obj("model", "stale"))));
    Thread.sleep(50); Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals("configured", app.modelSettings.model);
  }
}
