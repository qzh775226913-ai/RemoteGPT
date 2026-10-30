package app.remotessh;

import org.json.JSONObject;

/** Last server-reported context usage for one conversation, never account quota. */
final class ContextUsage {
  long window, used, input, cachedInput, output, reasoningOutput, total;
  boolean known;

  void update(JSONObject event) {
    JSONObject usage = event.optJSONObject("tokenUsage");
    if (usage == null) return;
    JSONObject last = usage.optJSONObject("last");
    JSONObject all = usage.optJSONObject("total");
    long size = usage.optLong("modelContextWindow", 0);
    if (last == null || size <= 0) { known = false; return; }
    window = size;
    used = Math.max(0, last.optLong("totalTokens", 0));
    input = Math.max(0, last.optLong("inputTokens", 0));
    cachedInput = Math.max(0, last.optLong("cachedInputTokens", 0));
    output = Math.max(0, last.optLong("outputTokens", 0));
    reasoningOutput = Math.max(0, last.optLong("reasoningOutputTokens", 0));
    total = all == null ? 0 : Math.max(0, all.optLong("totalTokens", 0));
    known = true;
  }

  float fraction() { return known ? Math.min(1f, (float) used / window) : 0f; }
}
