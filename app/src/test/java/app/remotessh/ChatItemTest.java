package app.remotessh;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.junit.Test;

public class ChatItemTest {
  @Test
  public void restoresUserTextFromHistory() {
    ChatItem m =
        ChatItem.from(
            Json.obj(
                "type",
                "userMessage",
                "id",
                "1",
                "content",
                new JSONArray().put(Json.obj("type", "text", "text", "你好"))));
    assertEquals("你好", m.text);
    assertTrue(m.user());
  }

  @Test
  public void restoresCommandOutput() {
    ChatItem m =
        ChatItem.from(
            Json.obj(
                "type",
                "commandExecution",
                "id",
                "2",
                "command",
                "pwd",
                "aggregatedOutput",
                "/workspace",
                "status",
                "completed"));
    assertEquals("/workspace", m.detail);
    assertFalse(m.prose());
  }

  @Test
  public void restoresFileDiff() {
    ChatItem m =
        ChatItem.from(
            Json.obj(
                "type",
                "fileChange",
                "id",
                "3",
                "changes",
                new JSONArray().put(Json.obj("path", "app.java", "diff", "+new"))));
    assertTrue(m.detail.contains("app.java"));
    assertTrue(m.detail.contains("+new"));
  }
}
