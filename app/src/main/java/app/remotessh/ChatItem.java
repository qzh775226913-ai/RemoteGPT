package app.remotessh;

import org.json.*;

public final class ChatItem {
  public final java.util.List<String> images = new java.util.ArrayList<>();
  final java.util.Map<String,String> imageFallbacks = new java.util.LinkedHashMap<>();
  public String id, type, text = "", detail = "", status = "";
  public String phase = "", turnId = "";
  public JSONObject interaction;
  public JSONArray changes, commandActions;
  public final java.util.List<ChatItem> children = new java.util.ArrayList<>();

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

  void updateFileChanges(JSONArray value) {
    changes=value;StringBuilder text=new StringBuilder();
    if(value!=null)for(int i=0;i<value.length();i++){
      JSONObject change=value.optJSONObject(i);
      if(change!=null)text.append(change.optString("path")).append('\n').append(change.optString("diff")).append('\n');
    }
    detail=text.toString();
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
    m.phase = j.optString("phase");
    m.turnId = j.optString("turnId");
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
        // Keep the path as the stable image identity and the server's inline bytes as a fallback.
        boolean inline=result.startsWith("data:image/") || result.startsWith("iVBORw0KGgo") ||
            result.startsWith("/9j/") || result.startsWith("UklGR");
        String mime=result.startsWith("/9j/")?"image/jpeg":result.startsWith("UklGR")?"image/webp":"image/png";
        String data=result.startsWith("data:") || result.startsWith("https://") ? result : "data:"+mime+";base64,"+result;
        if(!saved.isEmpty()){
          m.images.add(saved);if(inline)m.imageFallbacks.put(saved,data);
        }else if(!result.isEmpty())m.images.add(data);
        break;
      case "commandExecution":
        m.text = j.optString("command", "运行命令");
        m.detail = j.optString("aggregatedOutput");
        m.commandActions = j.optJSONArray("commandActions");
        break;
      case "fileChange":
        m.text = "文件更改";
        m.updateFileChanges(j.optJSONArray("changes"));
        break;
      case "reasoning":
        m.text = "思考过程";
        m.detail = readableText(j.opt("summary"));
        if (m.detail.isEmpty()) m.detail = readableText(j.opt("content"));
        if (m.detail.isEmpty()) m.detail = readableText(j.opt("text"));
        break;
      case "mcpToolCall":
        m.text = j.optString("server") + " · " + j.optString("tool");
        JSONObject toolResult = j.optJSONObject("result");
        JSONArray content = toolResult == null ? null : toolResult.optJSONArray("content");
        if(toolResult==null)m.detail=j.opt("result")==null?"":j.opt("result").toString();
        else {
          JSONObject readable=new JSONObject();
          for(java.util.Iterator<String> keys=toolResult.keys();keys.hasNext();) {
            String key=keys.next();if(key.equals("content"))continue;
            try{readable.put(key,toolResult.opt(key));}catch(JSONException ignored){}
          }
          JSONArray blocks=new JSONArray();
          if(content!=null)for(int i=0;i<content.length();i++) {
            JSONObject block=content.optJSONObject(i);
            blocks.put(block!=null && block.optString("type").equals("image")
                ? Json.obj("type","image","mimeType",block.optString("mimeType"),"note","图片见消息预览") : content.opt(i));
          }
          if(content!=null)try{readable.put("content",blocks);}catch(JSONException ignored){}
          m.detail=readable.toString();
        }
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
