package app.remotessh;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Read the complete server catalog before replacing the last usable snapshot. */
final class ModelCatalog {
  interface PageSource { JSONObject read(JSONObject params) throws Exception; }

  static JSONArray load(PageSource source) throws Exception {
    Map<String, JSONObject> entries = new LinkedHashMap<>();
    Set<String> cursors = new HashSet<>();
    String cursor = "";
    do {
      JSONObject params = Json.obj("limit", 100);
      if (!cursor.isEmpty()) params.put("cursor", cursor);
      JSONObject page = source.read(params);
      JSONArray data = page.optJSONArray("data");
      if (data == null) throw new IOException("远端模型列表缺少 data");
      for (int i = 0; i < data.length(); i++) {
        JSONObject entry = data.optJSONObject(i);
        if (entry == null || entry.optBoolean("hidden")) continue;
        String id = ModelOptions.id(entry);
        if (!id.isEmpty()) entries.put(id, entry);
      }
      cursor = page.isNull("nextCursor") ? "" : page.optString("nextCursor", "");
      if (!cursor.isEmpty() && (!cursors.add(cursor) || cursors.size() > 1000))
        throw new IOException("远端模型分页游标异常");
    } while (!cursor.isEmpty());
    return new JSONArray(entries.values());
  }
}
