package app.remotessh;

import java.util.*;
import org.json.*;

/** Picker capabilities come from the connected model catalog, never a model-name allowlist. */
final class ServiceTierOptions {
  static final String STANDARD = "default";
  static List<JSONObject> tiers(JSONObject model) {
    Map<String, JSONObject> result = new LinkedHashMap<>();
    if (model == null) return new ArrayList<>();
    JSONArray tiers = model.optJSONArray("serviceTiers");
    if (tiers != null) {
      for (int i = 0; i < tiers.length(); i++) {
        JSONObject tier = tiers.optJSONObject(i);
        if (tier == null || tier.isNull("id")) continue;
        String id = tier.optString("id").trim();
        if (!id.isEmpty()) result.putIfAbsent(id, tier);
      }
    } else {
      JSONArray legacy = model.optJSONArray("additionalSpeedTiers");
      if (legacy != null) for (int i = 0; i < legacy.length(); i++) {
        String id = legacy.isNull(i) ? "" : legacy.optString(i).trim();
        if (!id.isEmpty() && !id.equals(STANDARD))
          result.putIfAbsent(id, Json.obj("id", id, "name", id));
      }
    }
    return new ArrayList<>(result.values());
  }
  static List<JSONObject> choices(JSONObject model, boolean supported, boolean extraEnabled) {
    List<JSONObject> choices = new ArrayList<>();
    choices.add(Json.obj("id", "", "name", UiText.get(R.string.ui_remote_default)));
    if (!supported) return choices;
    List<JSONObject> remote = tiers(model);
    boolean hasStandard = false;
    for (JSONObject tier : remote) if (tier.optString("id").equals(STANDARD)) hasStandard = true;
    // Older servers omit the protocol's implicit normal tier from additional tiers.
    if (!hasStandard) choices.add(Json.obj("id", STANDARD, "name", UiText.get(R.string.service_tier_standard)));
    for (JSONObject tier : remote)
      if (extraEnabled || tier.optString("id").equals(STANDARD)) choices.add(tier);
    return choices;
  }
  static boolean same(String a, String b) {
    return a.equals(b) || isFast(a) && isFast(b);
  }
  static boolean isFast(String id) { return id.equals("fast") || id.equals("priority"); }
  static String supportedId(JSONObject model, String choice) {
    if (choice.isEmpty() || choice.equals(STANDARD)) return choice;
    // Prefer the exact wire id when a server advertises both aliases.
    for (JSONObject tier : tiers(model)) if (tier.optString("id").equals(choice)) return choice;
    for (JSONObject tier : tiers(model)) if (same(tier.optString("id"), choice)) return tier.optString("id");
    return "";
  }
  static String label(String id, JSONObject model) {
    if (id.isEmpty()) return UiText.get(R.string.ui_remote_default);
    String supported = supportedId(model, id);
    for (JSONObject tier : tiers(model)) if (tier.optString("id").equals(supported)) {
      String name = tier.isNull("name") ? "" : tier.optString("name");
      return name.trim().isEmpty() ? supported : name;
    }
    if (id.equals(STANDARD)) return UiText.get(R.string.service_tier_standard);
    return id;
  }
  static String description(String id, JSONObject model) {
    String supported = supportedId(model, id);
    for (JSONObject tier : tiers(model)) if (tier.optString("id").equals(supported))
      return tier.isNull("description") ? "" : tier.optString("description");
    return "";
  }
  static JSONObject model(JSONArray models, String id) {
    for (int i = 0; i < models.length(); i++) {
      JSONObject m = models.optJSONObject(i);
      if (m != null && ModelOptions.id(m).equals(id)) return m;
    }
    return null;
  }
  static boolean hasProtocolMetadata(JSONArray models) {
    for (int i = 0; i < models.length(); i++) {
      JSONObject m = models.optJSONObject(i);
      if (hasMetadata(m)) return true;
    }
    return false;
  }
  static boolean hasMetadata(JSONObject model) {
    return model != null && (model.optJSONArray("serviceTiers") != null || model.optJSONArray("additionalSpeedTiers") != null);
  }
}
