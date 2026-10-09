package app.remotessh;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ModelSettingsTest {
  @Test public void readsProjectDefaultsAndEffectiveFlagOnLaterPage() {
    List<String> methods = new ArrayList<>();
    ModelSettings settings = ModelSettings.load(request -> {
      String method = request.getString("method"); methods.add(method);
      JSONObject p = request.getJSONObject("params");
      if (method.equals("configRequirements/read")) return new JSONObject();
      if (method.equals("config/read")) {
        assertEquals("/work/project", p.getString("cwd")); assertFalse(p.getBoolean("includeLayers"));
        return Json.obj("config", Json.obj("model", "configured-model", "service_tier", "fast"));
      }
      if (!p.has("cursor")) return Json.obj("data", new JSONArray(), "nextCursor", "second");
      assertEquals("second", p.getString("cursor"));
      return Json.obj("data", new JSONArray().put(Json.obj("name", "fast_mode", "enabled", false)));
    }, "/work/project");
    assertEquals(4, methods.size()); assertTrue(settings.configKnown);
    assertEquals("configured-model", settings.model); assertEquals("fast", settings.tier);
    assertFalse(settings.fastEnabled);
  }
  @Test public void unsupportedOptionalEndpointsKeepCatalogUsable() {
    ModelSettings settings = ModelSettings.load(request -> { throw new IOException("method not found"); }, "");
    assertFalse(settings.configKnown); assertTrue(settings.fastEnabled);
    assertFalse(settings.status.isEmpty());
  }
  @Test public void explicitDisabledProjectFlagCannotBeEnabledByGlobalFlag() {
    ModelSettings settings = ModelSettings.load(request -> request.getString("method").equals("config/read")
        ? Json.obj("config", Json.obj("features", Json.obj("fast_mode", false)))
        : Json.obj("data", new JSONArray().put(Json.obj("name", "fast_mode", "enabled", true))), "");
    assertFalse(settings.fastEnabled);
  }
  @Test public void incompleteFeaturePaginationDoesNotPublishPartialResult() {
    int[] calls = {0};
    ModelSettings settings = ModelSettings.load(request -> {
      if (request.getString("method").equals("config/read")) return Json.obj("config", new JSONObject());
      if (request.getString("method").equals("configRequirements/read")) return new JSONObject();
      calls[0]++;
      if (calls[0] == 1) return Json.obj("data", new JSONArray().put(Json.obj("name", "fast_mode", "enabled", false)), "nextCursor", "next");
      throw new IOException("offline");
    }, "");
    assertEquals(2, calls[0]); assertTrue(settings.fastEnabled);
  }
  @Test public void managedDefaultsAndManagedDisableAreRespected() {
    ModelSettings settings = ModelSettings.load(request -> {
      switch (request.getString("method")) {
        case "config/read": return Json.obj("config", Json.obj("model", "configured", "service_tier", "fast"));
        case "configRequirements/read": return Json.obj("requirements", Json.obj("featureRequirements", Json.obj("fast_mode", false),
            "models", Json.obj("newThread", Json.obj("model", "managed", "serviceTier", "default"))));
        default: return Json.obj("data", new JSONArray().put(Json.obj("name", "fast_mode", "enabled", true)));
      }
    }, "");
    assertEquals("configured", settings.model); assertEquals("managed", settings.managedModel);
    assertEquals("default", settings.tier); assertFalse(settings.fastEnabled);
  }
}
