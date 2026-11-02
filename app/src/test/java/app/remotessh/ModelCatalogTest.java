package app.remotessh;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ModelCatalogTest {
  @Test public void discoversFutureModelsAcrossPagesInServerOrder() throws Exception {
    List<JSONObject> requests = new ArrayList<>();
    JSONArray result = ModelCatalog.load(params -> {
      requests.add(params);
      if (!params.has("cursor")) return Json.obj("data", new JSONArray()
          .put(Json.obj("model", "old")), "nextCursor", "page-2");
      assertEquals("page-2", params.getString("cursor"));
      return Json.obj("data", new JSONArray()
          .put(Json.obj("model", "gpt-6.1-sol", "displayName", "GPT-6.1 Sol"))
          .put(Json.obj("model", "future-model-99"))
          .put(Json.obj("model", "old"))
          .put(Json.obj("model", "hidden", "hidden", true)), "nextCursor", JSONObject.NULL);
    });
    assertEquals(2, requests.size());
    assertEquals(3, result.length());
    assertEquals("gpt-6.1-sol", ModelOptions.id(result.getJSONObject(1)));
    assertEquals("GPT-6.1 Sol", ModelOptions.displayName(result, "gpt-6.1-sol"));
    assertEquals("future-model-99", ModelOptions.id(result.getJSONObject(2)));
  }
  @Test public void preservesNewEffortsAndServerOrdering() {
    JSONObject model = Json.obj("model", JSONObject.NULL, "id", "future", "supportedReasoningEfforts",
        new JSONArray().put("low").put("future-effort").put("high").put("high"));
    assertEquals("future", ModelOptions.id(model));
    assertEquals(Arrays.asList("low", "future-effort", "high"), ModelOptions.efforts(model));
  }
  @Test(expected=IOException.class) public void rejectsRepeatedCursor() throws Exception {
    ModelCatalog.load(params -> Json.obj("data", new JSONArray(), "nextCursor", "loop"));
  }
  @Test(expected=IOException.class) public void rejectsMalformedCatalog() throws Exception {
    ModelCatalog.load(params -> new JSONObject());
  }
  @Test public void acceptsEmptyAndLegacySinglePage() throws Exception {
    assertEquals(0, ModelCatalog.load(params -> Json.obj("data", new JSONArray())).length());
  }
}
