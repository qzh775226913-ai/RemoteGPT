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
    if (id.isEmpty()) return UiText.get(R.string.ui_default_model);
    for (int i = 0; i < models.length(); i++) {
      JSONObject model = models.optJSONObject(i);
      if (model != null && id(model).equals(id) && !model.isNull("displayName")
          && !model.optString("displayName").trim().isEmpty()) return model.optString("displayName");
    }
    return id;
  }
  static String label(String effort) {
    switch (effort) {
      case "": return UiText.get(R.string.ui_default);
      case "none": return UiText.get(R.string.reasoning_effort_none);
      case "minimal": return UiText.get(R.string.reasoning_effort_minimal);
      case "low": return UiText.get(R.string.reasoning_effort_low);
      case "medium": return UiText.get(R.string.reasoning_effort_medium_414);
      case "high": return UiText.get(R.string.reasoning_effort_high);
      case "xhigh": return UiText.get(R.string.reasoning_effort_extra_high);
      case "max": return UiText.get(R.string.reasoning_effort_maximum);
      case "ultra": return UiText.get(R.string.reasoning_effort_ultra);
      default: return effort;
    }
  }
}
