package app.remotessh;

import java.util.*;
import org.json.*;

/** Optional discovery must not make an older server unusable for chat. */
final class ModelSettings {
  String model = "", managedModel = "", tier = "", cwd = "", status = "";
  boolean configKnown, globalFeaturesKnown, fastEnabled = true, globalFastEnabled = true;
  static ModelSettings load(ModelCatalog.PageSource source, String cwd) {
    ModelSettings settings = new ModelSettings(); settings.cwd = cwd;
    try {
      JSONObject params = Json.obj("includeLayers", false);
      if (!cwd.isEmpty()) params.put("cwd", cwd);
      JSONObject config = source.read(Json.obj("method", "config/read", "params", params)).optJSONObject("config");
      if (config == null) throw new IllegalStateException("远端未返回有效配置");
      settings.model = config.isNull("model") ? "" : config.optString("model");
      settings.tier = config.isNull("service_tier") ? "" : config.optString("service_tier");
      settings.configKnown = true;
      JSONObject features = config.optJSONObject("features");
      if (features != null && features.has("fast_mode") && !features.isNull("fast_mode"))
        settings.fastEnabled = features.optBoolean("fast_mode");
    } catch (Exception e) { settings.status = "远端默认配置暂不可用"; }
    try {
      JSONObject response = source.read(Json.obj("method", "configRequirements/read", "params", new JSONObject()));
      JSONObject requirements = response.optJSONObject("requirements");
      if (requirements != null) {
        JSONObject features = requirements.optJSONObject("featureRequirements");
        if (features != null && features.has("fast_mode") && !features.isNull("fast_mode")) {
          settings.globalFeaturesKnown = true;
          settings.globalFastEnabled &= features.optBoolean("fast_mode");
          settings.fastEnabled &= settings.globalFastEnabled;
        }
        JSONObject defaults = Json.object(Json.object(requirements, "models"), "newThread");
        if (!defaults.isNull("model")) settings.managedModel = defaults.optString("model");
        if (!defaults.isNull("serviceTier")) settings.tier = defaults.optString("serviceTier");
      }
    } catch (Exception ignored) { /* Older servers do not expose managed defaults. */ }
    try {
      Set<String> seen = new HashSet<>(); String cursor = "";
      boolean enabled = settings.globalFastEnabled;
      do {
        JSONObject params = Json.obj("limit", 100);
        if (!cursor.isEmpty()) params.put("cursor", cursor);
        JSONObject page = source.read(Json.obj("method", "experimentalFeature/list", "params", params));
        JSONArray data = page.optJSONArray("data");
        if (data == null) throw new IllegalStateException("远端功能列表缺少 data");
        for (int i = 0; i < data.length(); i++) {
          JSONObject feature = data.optJSONObject(i);
          if (feature != null && feature.optString("name").equals("fast_mode") && feature.has("enabled"))
            enabled = enabled && feature.optBoolean("enabled");
        }
        cursor = page.isNull("nextCursor") ? "" : page.optString("nextCursor");
        if (!cursor.isEmpty() && (!seen.add(cursor) || seen.size() > 1000)) throw new IllegalStateException("功能分页异常");
      } while (!cursor.isEmpty());
      settings.globalFastEnabled = enabled;
      settings.globalFeaturesKnown = true;
      settings.fastEnabled &= enabled;
    } catch (Exception ignored) { /* Positive model metadata remains useful without this optional endpoint. */ }
    return settings;
  }
}
