package app.remotessh;

import java.text.DateFormat;
import java.util.*;
import org.json.*;

/** Account quota, independent of thread token/context usage. */
public final class UsageLimits {
  private final Map<String, JSONObject> buckets = new LinkedHashMap<>();
  public String status = "连接后查看剩余用量";
  public boolean loading;

  public void clear() {
    buckets.clear();
    loading = false;
    status = "连接后查看剩余用量";
  }

  public void update(JSONObject payload, boolean snapshot) {
    if (snapshot) buckets.clear();
    JSONObject all = payload.optJSONObject("rateLimitsByLimitId");
    if (all != null) {
      Iterator<String> keys = all.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        merge(key, all.optJSONObject(key));
      }
    }
    JSONObject single = payload.optJSONObject("rateLimits");
    if (single != null) {
      String id = single.isNull("limitId") ? "codex" : single.optString("limitId", "codex");
      if (all == null || !all.has(id)) merge(id, single);
    }
    status = "远端未提供用量数据";
  }

  private void merge(String key, JSONObject incoming) {
    if (incoming == null) return;
    JSONObject current = buckets.get(key);
    if (current == null) current = new JSONObject();
    Iterator<String> keys = incoming.keys();
    while (keys.hasNext()) {
      String field = keys.next();
      // Notifications are sparse; null does not erase an observed quota window.
      if (!incoming.isNull(field)) try { current.put(field, incoming.opt(field)); } catch (JSONException ignored) {}
    }
    buckets.put(key, current);
  }

  public String summary() {
    JSONObject bucket = buckets.get("codex");
    if (bucket == null && !buckets.isEmpty()) bucket = buckets.values().iterator().next();
    String windows = bucket == null ? "" : windows(bucket, false);
    if (windows.isEmpty()) return loading ? UiText.get(R.string.ui_loading_remaining_usage) : UiPresentation.usageStatus(status);
    return UiText.get(R.string.ui_remaining_usage_1_s, windows) + (loading ? UiText.get(R.string.ui_refreshing) : "");
  }

  public String details() {
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, JSONObject> entry : buckets.entrySet()) {
      JSONObject b = entry.getValue();
      if (result.length() > 0) result.append("\n\n");
      result.append(b.isNull("limitName") ? entry.getKey() : b.optString("limitName", entry.getKey()));
      String value = windows(b, true);
      result.append("\n").append(value.isEmpty() ? UiText.get(R.string.ui_no_usage_window_was_provided) : value);
    }
    if (!status.equals("远端未提供用量数据") || result.length() == 0)
      result.append(result.length() == 0 ? "" : "\n\n").append(UiPresentation.usageStatus(status));
    return result.toString();
  }

  private static String windows(JSONObject b, boolean detail) {
    List<String> values = new ArrayList<>();
    for (String key : new String[] {"primary", "secondary"}) {
      JSONObject w = b.optJSONObject(key);
      if (w == null || w.isNull("usedPercent")) continue;
      double used = w.optDouble("usedPercent", Double.NaN);
      if (!Double.isFinite(used)) continue;
      long minutes = w.optLong("windowDurationMins", 0);
      String label = minutes <= 0 ? (key.equals("primary") ? UiText.get(R.string.ui_primary_quota) : UiText.get(R.string.ui_secondary_quota))
          : minutes % 1440 == 0 ? UiText.quantity(R.plurals.ui_days, (int) Math.min(Integer.MAX_VALUE, minutes / 1440))
          : minutes % 60 == 0 ? UiText.quantity(R.plurals.ui_hours, (int) Math.min(Integer.MAX_VALUE, minutes / 60)) : UiText.quantity(R.plurals.ui_minutes, (int) Math.min(Integer.MAX_VALUE, minutes));
      String value = UiText.get(R.string.ui_1_s_2_s, label, java.text.NumberFormat.getPercentInstance(UiText.locale()).format(Math.round(Math.max(0, Math.min(100, 100 - used))) / 100.0));
      long resets = w.optLong("resetsAt", 0);
      if (detail && resets > 0) value = UiText.get(R.string.ui_1_s_resets_2_s, value, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, UiText.locale()).format(new Date(resets * 1000)));
      values.add(value);
    }
    return String.join(detail ? "\n" : " · ", values);
  }
}
