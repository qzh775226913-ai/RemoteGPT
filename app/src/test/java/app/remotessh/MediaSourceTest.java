package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MediaSourceTest {
  static final String README_COMMAND = "cat > README.md <<'EOF'\n"
      + "[![Verify](https://example.test/verify/badge.svg)](https://example.test/actions)\n"
      + "[![Release](https://example.test/release)](https://example.test/releases)\n"
      + "![Android 8+](https://example.test/android.svg)\nEOF";

  @Test public void commandBadgesNeverCreateMediaOrGalleryEntries() {
    ChatItem command=ChatItem.from(Json.obj("id","cmd","type","commandExecution","command",README_COMMAND));
    assertTrue(MediaFiles.images(command).isEmpty());
    assertTrue(MediaFiles.imageGallery(Collections.singletonList(command),"/work").isEmpty());
    assertEquals(README_COMMAND,command.text);
    for(String type:Arrays.asList("reasoning","fileChange","mcpToolCall","webSearch","unknownTool")) {
      ChatItem tool=new ChatItem(type,type,"![literal](https://example.test/not-an-attachment.png)");
      assertTrue(type,MediaFiles.images(tool).isEmpty());
    }
  }

  @Test public void proseExcludesFencesInlineCodeAndEscapedImageExamples() {
    String source="![real](/real.png)\n```md\n![code](/code.png)\n```\n"
        + "~~~text\n![other fence](/other.png)\n~~~\n"
        + "`![inline](/inline.png)` and ``![nested ` code](/nested.png)``\n"
        + "\\![escaped](/escaped.png)\n![second](/second.png)";
    for(String type:Arrays.asList("agentMessage","userMessage","plan")) {
      ChatItem item=new ChatItem("m",type,source);
      assertEquals(Arrays.asList("/real.png","/second.png"),MediaFiles.images(item));
      assertEquals(source,item.text);
    }
    ChatItem incomplete=new ChatItem("stream","agentMessage","![real](/real.png)\n```md\n![example](/hidden.png)");
    assertEquals(Collections.singletonList("/real.png"),MediaFiles.images(incomplete));
    incomplete.text+="\n```\n![new](/new.png)";
    assertEquals(Arrays.asList("/real.png","/new.png"),MediaFiles.images(incomplete));
  }

  @Test public void explicitToolAndUserImagesRemainAvailableAndDeduplicated() {
    ChatItem tool=new ChatItem("tool","mcpToolCall","![example](/not-an-attachment.png)");
    tool.images.add("data:image/png;base64,abc");
    ChatItem view=ChatItem.from(Json.obj("id","view","type","imageView","path","file:///work/view.png"));
    ChatItem user=ChatItem.from(Json.obj("id","u","type","userMessage","content",new JSONArray()
        .put(Json.obj("type","localImage","path","/work/view.png"))));
    assertEquals(Collections.singletonList("data:image/png;base64,abc"),MediaFiles.images(tool));
    assertEquals(Arrays.asList("data:image/png;base64,abc","/work/view.png"),
        MediaFiles.imageGallery(Arrays.asList(tool,view,user),"/work"));
  }

  @Test public void historyConversionUsesTheSameMediaRulesAsLiveItems() {
    JSONObject command=Json.obj("id","cmd","type","commandExecution","command",README_COMMAND);
    JSONObject photo=Json.obj("id","photo","type","agentMessage","text","![photo](/work/photo.png)");
    ThreadSnapshot snapshot=new ThreadSnapshot(Json.obj("thread",Json.obj("turns",new JSONArray()
        .put(Json.obj("id","turn","items",new JSONArray().put(command).put(photo))))));
    assertNull(snapshot.failure);
    assertTrue(MediaFiles.images(snapshot.turns.get(0).get(0)).isEmpty());
    assertEquals(Collections.singletonList("/work/photo.png"),MediaFiles.imageGallery(snapshot.turns.get(0),"/work"));
  }
}
