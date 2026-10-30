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
public class ContextUsageTest {
  private JSONObject usage(String thread, long last, long window) {
    return Json.obj("method", "thread/tokenUsage/updated", "params", Json.obj(
        "threadId", thread, "turnId", "turn-1", "tokenUsage", Json.obj(
            "last", Json.obj("totalTokens", last, "inputTokens", last - 10,
                "cachedInputTokens", 2, "outputTokens", 10, "reasoningOutputTokens", 3),
            "total", Json.obj("totalTokens", last * 2), "modelContextWindow", window)));
  }

  @Test public void serverUsageIsClampedAndUnknownWindowNeverShowsFakePercentage() {
    ContextUsage context = new ContextUsage();
    assertFalse(context.known);
    assertEquals(0f, context.fraction(), 0f);
    context.update(Json.object(usage("A", 60, 100), "params"));
    assertTrue(context.known);
    assertEquals(.6f, context.fraction(), .001f);
    assertEquals(120, context.total);
    context.update(Json.object(usage("A", 150, 100), "params"));
    assertEquals(1f, context.fraction(), 0f);
    context.update(Json.object(usage("A", 10, 0), "params"));
    assertFalse(context.known);
  }

  @Test public void backgroundEventAndNestedFailureCannotLeakContextToSelectedThread() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile();
    app.threadId = "A";
    app.changed();
    app.inConversation("B", () -> app.changed());
    Method event = RemoteApp.class.getDeclaredMethod("event", JSONObject.class);
    event.setAccessible(true);
    event.invoke(app, usage("B", 80, 100));
    assertEquals("A", app.threadId);
    assertFalse(app.contextUsage.known);
    assertEquals(.8f, app.conversationStore.get("B").contextUsage.fraction(), .001f);
    try {
      app.inConversation("B", () -> { app.contextUsage.update(Json.object(usage("B", 30, 100), "params"));
        throw new IllegalStateException("synthetic"); });
      fail();
    } catch (IllegalStateException expected) { assertEquals("synthetic", expected.getMessage()); }
    assertEquals("A", app.threadId);
    assertFalse(app.contextUsage.known);
    assertEquals(.3f, app.conversationStore.get("B").contextUsage.fraction(), .001f);
  }
}
