package app.remotessh;

import org.json.*;

public final class Json {
  private Json() {}

  public static JSONObject obj(Object... kv) {
    JSONObject j = new JSONObject();
    try {
      for (int i = 0; i < kv.length; i += 2) j.put((String) kv[i], kv[i + 1]);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return j;
  }

  public static JSONObject object(JSONObject j, String key) {
    JSONObject v = j.optJSONObject(key);
    return v == null ? new JSONObject() : v;
  }
}
