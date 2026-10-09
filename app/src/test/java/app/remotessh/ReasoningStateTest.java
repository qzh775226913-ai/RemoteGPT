package app.remotessh;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReasoningStateTest {
  private RemoteApp app;
  private Method event, apply;

  @Before public void setup() throws Exception {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.threadId = "active";
    event = RemoteApp.class.getDeclaredMethod("event", JSONObject.class); event.setAccessible(true);
    apply = RemoteApp.class.getDeclaredMethod("applyThread", JSONObject.class, String.class, boolean.class);
    apply.setAccessible(true);
  }

  private JSONObject reasoning(String summary) {
    return Json.obj("id", "r", "type", "reasoning", "summary", new JSONArray().put(summary));
  }

  private void emit(String method, JSONObject params) throws Exception {
    event.invoke(app, Json.obj("method", method, "params", params));
  }

  private void snapshot(String id, String runtime, String turnStatus, String summary, boolean readonly) throws Exception {
    apply.invoke(app, Json.obj("thread", Json.obj("id", id, "status", Json.obj("type", runtime),
        "turns", new JSONArray().put(Json.obj("id", "t", "status", turnStatus,
            "items", new JSONArray().put(reasoning(summary)))))), id, readonly);
  }

  @Test public void emptyHistoryRefreshPreservesReceivedSummary() throws Exception {
    emit("item/completed", Json.obj("threadId", "active", "item", reasoning("已检查接口")));
    snapshot("active", "idle", "completed", "", true);
    assertEquals("已检查接口", app.items.get(0).detail);
    assertEquals("completed", app.items.get(0).status);
    snapshot("active", "idle", "completed", "远端更新的摘要", false);
    assertEquals("远端更新的摘要", app.items.get(0).detail);
  }

  @Test public void sameItemIdInAnotherConversationDoesNotBorrowSummary() throws Exception {
    emit("item/completed", Json.obj("threadId", "active", "item", reasoning("第一条对话")));
    snapshot("other", "idle", "completed", "", true);
    assertEquals("", app.items.get(0).detail);
    snapshot("active", "idle", "completed", "", true);
    assertEquals("第一条对话", app.items.get(0).detail);
  }

  @Test public void historyShowsWaitingOnlyForActuallyActiveTurn() throws Exception {
    snapshot("active", "active", "inProgress", "", true);
    assertEquals("inProgress", app.items.get(0).status);
    snapshot("active", "idle", "inProgress", "", true);
    assertEquals("completed", app.items.get(0).status);
  }

  @Test public void deltaWithoutStartWaitsAndFinishesAtTurnCompletion() throws Exception {
    emit("item/reasoning/summaryTextDelta", Json.obj("threadId", "active", "itemId", "r", "summaryIndex", 0, "delta", "检查配置"));
    assertEquals("inProgress", app.items.get(0).status);
    emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("id", "t", "status", "completed")));
    assertEquals("completed", app.items.get(0).status);
    assertEquals("检查配置", app.items.get(0).detail);
  }

  @Test public void finalTurnSnapshotSuppliesSummaryAndDoesNotDuplicateItem() throws Exception {
    emit("item/started", Json.obj("threadId", "active", "item", reasoning("")));
    emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("id", "t", "status", "completed",
        "items", new JSONArray().put(reasoning("仅结束快照返回的摘要")))));
    assertEquals(1, app.items.size());
    assertEquals("仅结束快照返回的摘要", app.items.get(0).detail);
    assertEquals("completed", app.items.get(0).status);
  }

  @Test public void emptyFinalSnapshotKeepsStreamedSummary() throws Exception {
    emit("item/reasoning/summaryTextDelta", Json.obj("threadId", "active", "itemId", "r", "delta", "已有摘要"));
    emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("status", "completed",
        "items", new JSONArray().put(reasoning("")))));
    assertEquals("已有摘要", app.items.get(0).detail);
    assertEquals("completed", app.items.get(0).status);
  }

  @Test public void replayedOldCompletionDoesNotFinishCurrentBaselineReasoning() throws Exception {
    snapshot("active", "active", "inProgress", "", false);
    emit("remote/replayStarted", Json.obj("threadId", "active"));
    emit("turn/completed", Json.obj("threadId", "active", "turn", Json.obj("id", "old", "status", "completed")));
    assertEquals("inProgress", app.items.get(0).status);
  }
}
