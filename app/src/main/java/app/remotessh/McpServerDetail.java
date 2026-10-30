package app.remotessh;

import java.util.*;
import org.json.*;

/** Read-only presentation of one server's full MCP inventory. */
final class McpServerDetail {
  private McpServerDetail() {}

  static String describe(JSONObject server) {
    StringBuilder out = new StringBuilder();
    line(out, R.string.ui_mcp_runtime_status, value(server, "runtimeStatus"));
    line(out, R.string.ui_mcp_auth_status, value(server, "authStatus"));
    line(out, R.string.ui_mcp_plugin, value(server, "pluginId"));
    line(out, R.string.ui_mcp_origin, value(server, "httpOrigin"));
    JSONObject info = server.optJSONObject("serverInfo");
    if (info != null) {
      section(out, UiText.get(R.string.ui_mcp_server_info));
      line(out, R.string.ui_mcp_title, value(info, "title"));
      line(out, R.string.ui_mcp_version, value(info, "version"));
      line(out, R.string.ui_mcp_website, value(info, "websiteUrl"));
      String description = value(info, "description");
      if (!description.isEmpty()) out.append('\n').append(description);
    }
    JSONObject tools = server.optJSONObject("tools");
    List<String> names = new ArrayList<>();
    if (tools != null) for (Iterator<String> it = tools.keys(); it.hasNext();) names.add(it.next());
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    section(out, UiText.get(R.string.ui_mcp_tools_count, names.size()));
    for (String name : names) {
      JSONObject tool = tools.optJSONObject(name);
      out.append('\n').append(name);
      if (tool != null) {
        String description = value(tool, "description");
        if (!description.isEmpty()) out.append("\n  ").append(description);
      }
    }
    String error = value(server, "toolsError");
    if (!error.isEmpty()) out.append('\n').append(error);
    inventory(out, server.optJSONArray("resources"), R.string.ui_mcp_resources_count, "uri");
    inventory(out, server.optJSONArray("resourceTemplates"), R.string.ui_mcp_templates_count, "uriTemplate");
    Object capabilities = server.opt("serverCapabilities");
    if (capabilities != null && capabilities != JSONObject.NULL) {
      section(out, UiText.get(R.string.ui_mcp_capabilities));
      out.append('\n').append(capabilities);
    }
    return out.toString().trim();
  }

  private static void inventory(StringBuilder out, JSONArray entries, int title, String uriKey) {
    int count = entries == null ? 0 : entries.length();
    section(out, UiText.get(title, count));
    for (int i = 0; i < count; i++) {
      JSONObject item = entries.optJSONObject(i);
      if (item == null) continue;
      String name = value(item, "name");
      if (name.isEmpty()) name = value(item, "title");
      out.append('\n').append(name);
      String uri = value(item, uriKey);
      if (!uri.isEmpty()) out.append("\n  ").append(uri);
      String description = value(item, "description");
      if (!description.isEmpty()) out.append("\n  ").append(description);
    }
  }

  private static void section(StringBuilder out, String title) {
    if (out.length() > 0) out.append("\n\n");
    out.append(title);
  }

  private static void line(StringBuilder out, int label, String value) {
    if (value.isEmpty()) return;
    if (out.length() > 0) out.append('\n');
    out.append(UiText.get(label)).append(": ").append(value);
  }

  private static String value(JSONObject object, String key) {
    if (object == null || object.isNull(key)) return "";
    return object.optString(key, "");
  }
}
