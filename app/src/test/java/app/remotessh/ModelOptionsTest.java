package app.remotessh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
public class ModelOptionsTest {
  @Test public void usesOnlyServerAdvertisedEfforts() {
    JSONObject model = Json.obj("supportedReasoningEfforts", new JSONArray()
        .put(Json.obj("reasoningEffort", "low")).put(Json.obj("reasoningEffort", "high")));
    assertEquals(java.util.Arrays.asList("low", "high"), ModelOptions.efforts(model));
    assertTrue(ModelOptions.efforts(new JSONObject()).isEmpty());
    assertEquals("High", ModelOptions.label("high"));
    assertEquals("future", ModelOptions.label("future"));
  }
  @Test public void emptySummaryFallsBackToReadableContentOnly() {
    ChatItem item = ChatItem.from(Json.obj("id", "r", "type", "reasoning", "summary", new JSONArray().put(""),
        "content", new JSONArray().put(Json.obj("type", "reasoning_text", "text", "先检查配置"))));
    assertEquals("先检查配置", item.detail);
    assertEquals("", ChatItem.from(Json.obj("type", "reasoning", "content", new JSONArray().put(JSONObject.NULL))).detail);
  }
}
