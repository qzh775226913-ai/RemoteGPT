package app.remotessh;

import java.util.*;
import org.json.*;

final class ModelOptions {
  static String id(JSONObject model) { return model.isNull("model") || model.optString("model").trim().isEmpty()
      ? model.optString("id", "").trim() : model.optString("model").trim(); }
  static List<String> efforts(JSONObject model) {
    List<String> values = new ArrayList<>();
    if (model == null) return values;
    JSONArray supported = model.optJSONArray("supportedReasoningEfforts");
    if (supported != null) for (int i = 0; i < supported.length(); i++) {
      JSONObject entry = supported.optJSONObject(i);
      String effort = entry == null ? supported.optString(i) : entry.optString("reasoningEffort");
      if (!effort.isEmpty() && !values.contains(effort)) values.add(effort);
    }
    return values;
  }
  static String displayName(JSONArray models, String id) {
    if (id.isEmpty()) return "默认模型";
    for (int i = 0; i < models.length(); i++) {
      JSONObject model = models.optJSONObject(i);
      if (model != null && id(model).equals(id) && !model.isNull("displayName")
          && !model.optString("displayName").trim().isEmpty()) return model.optString("displayName");
    }
    return id;
  }
  static String label(String effort) {
    switch (effort) {
      case "": return "默认";
      case "none": return "None";
      case "minimal": return "Minimal";
      case "low": return "Low";
      case "medium": return "Medium";
      case "high": return "High";
      case "xhigh": return "Extra High";
      case "max": return "Max";
      case "ultra": return "Ultra";
      default: return effort;
    }
  }
}
