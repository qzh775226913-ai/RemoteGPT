package app.remotessh;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ModelSettingsTest {
  @Test public void questionSupportIsScopedToNewThreadAndPreservesInstructions() throws Exception {
    ModelSettings settings = ModelSettings.load(request -> {
      if(request.getString("method").equals("config/read"))return Json.obj("config",Json.obj("developer_instructions","existing instructions"));
      if(request.getString("method").equals("configRequirements/read"))return new JSONObject();
      return Json.obj("data",new JSONArray().put(Json.obj("name","default_mode_request_user_input","stage","underDevelopment","enabled",false)));
    },"/work");
    JSONObject params=Json.obj("cwd","/work","config",Json.obj("other","kept"));settings.applyQuestionSupport(params);
    assertTrue(params.getJSONObject("config").getBoolean("features.default_mode_request_user_input"));
    assertEquals("kept",params.getJSONObject("config").getString("other"));
    assertTrue(params.getString("developerInstructions").startsWith("existing instructions\n"));
    assertTrue(params.getString("developerInstructions").contains("blocking request_user_input"));
    settings.questionToolAllowed=false;JSONObject disabled=new JSONObject();settings.applyQuestionSupport(disabled);assertEquals(0,disabled.length());
    ModelSettings older=new ModelSettings();older.configKnown=true;JSONObject legacy=new JSONObject();older.applyQuestionSupport(legacy);assertEquals(0,legacy.length());
  }
  @Test public void managedDisableAndIncompleteDiscoveryDoNotEnableQuestionTool() {
    ModelSettings managed=ModelSettings.load(request->{
      if(request.getString("method").equals("config/read"))return Json.obj("config",new JSONObject());
      if(request.getString("method").equals("configRequirements/read"))return Json.obj("requirements",Json.obj("featureRequirements",Json.obj("default_mode_request_user_input",false)));
      return Json.obj("data",new JSONArray().put(Json.obj("name","default_mode_request_user_input","enabled",false)));
    },"");
    JSONObject params=new JSONObject();managed.applyQuestionSupport(params);assertEquals(0,params.length());
    ModelSettings partial=ModelSettings.load(request->{
      if(request.getString("method").equals("config/read"))return Json.obj("config",new JSONObject());
      if(request.getString("method").equals("configRequirements/read"))return new JSONObject();
      if(request.getJSONObject("params").has("cursor"))throw new IOException("offline");
      return Json.obj("data",new JSONArray().put(Json.obj("name","default_mode_request_user_input","enabled",false)),"nextCursor","next");
    },"");
    params=new JSONObject();partial.applyQuestionSupport(params);assertEquals(0,params.length());
  }
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
