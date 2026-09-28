package app.remotessh;

import org.json.*;

public final class ChatItem {
  public final java.util.List<String> images = new java.util.ArrayList<>();
  public String id, type, text = "", detail = "", status = "";

  public ChatItem(String id, String type, String text) {
    this.id = id;
    this.type = type;
    this.text = text;
  }

  public boolean user() {
    return type.equals("userMessage");
  }

  public boolean prose() {
    return user() || type.equals("agentMessage") || type.equals("plan");
  }

  private static String readableText(Object value) {
    if (value instanceof String) return ((String) value).trim();
    if (value instanceof JSONObject) return readableText(((JSONObject) value).opt("text"));
    if (value instanceof JSONArray) {
      java.util.List<String> parts = new java.util.ArrayList<>();
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        String part = readableText(array.opt(i));
        if (!part.isEmpty()) parts.add(part);
      }
      return String.join("\n\n", parts);
    }
    return "";
  }

  public static ChatItem from(JSONObject j) {
    ChatItem m = new ChatItem(j.optString("id"), j.optString("type"), j.optString("text"));
    m.status = j.optString("status");
    switch (m.type) {
      case "userMessage":
        JSONArray c = j.optJSONArray("content");
        StringBuilder b = new StringBuilder();
        if (c != null)
          for (int i = 0; i < c.length(); i++) {
            JSONObject v = c.optJSONObject(i);
            if (v != null && v.optString("type").equals("localImage")) m.images.add(v.optString("path"));
            if (v != null && v.optString("type").equals("image") && !v.optString("url").isEmpty()) m.images.add(v.optString("url"));
            if (v != null && v.has("text")) {
              if (b.length() > 0) b.append('\n');
              b.append(v.optString("text"));
            }
          }
        m.text = b.toString();
        break;
      case "imageView":
        m.text = "查看图片";
        if (!j.optString("path").isEmpty()) m.images.add(j.optString("path"));
        break;
      case "imageGeneration":
        m.text = "生成图片";
        String saved = j.isNull("savedPath") ? "" : j.optString("savedPath");
        String result = j.optString("result");
        if (!saved.isEmpty()) m.images.add(saved);
        else if (!result.isEmpty()) m.images.add(result.startsWith("data:") || result.startsWith("https://") ? result : "data:image/png;base64," + result);
        break;
      case "commandExecution":
        m.text = j.optString("command", "运行命令");
        m.detail = j.optString("aggregatedOutput");
        break;
      case "fileChange":
        m.text = "文件更改";
        JSONArray changes = j.optJSONArray("changes");
        StringBuilder d = new StringBuilder();
        if (changes != null)
          for (int i = 0; i < changes.length(); i++) {
            JSONObject v = changes.optJSONObject(i);
            if (v != null)
              d.append(v.optString("path")).append('\n').append(v.optString("diff")).append('\n');
          }
        m.detail = d.toString();
        break;
      case "reasoning":
        m.text = "思考过程";
        m.detail = readableText(j.opt("summary"));
        if (m.detail.isEmpty()) m.detail = readableText(j.opt("content"));
        if (m.detail.isEmpty()) m.detail = readableText(j.opt("text"));
        break;
      case "mcpToolCall":
        m.text = j.optString("server") + " · " + j.optString("tool");
        m.detail = j.opt("result") == null ? "" : j.opt("result").toString();
        JSONObject toolResult = j.optJSONObject("result");
        JSONArray content = toolResult == null ? null : toolResult.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) {
          JSONObject block = content.optJSONObject(i);
          if (block != null && block.optString("type").equals("image") && block.optString("mimeType").startsWith("image/"))
            m.images.add("data:" + block.optString("mimeType") + ";base64," + block.optString("data"));
        }
        break;
      case "webSearch":
        m.text = "搜索 · " + j.optString("query");
        break;
      default:
        if (m.text.isEmpty()) m.text = m.type;
    }
    return m;
  }
}
