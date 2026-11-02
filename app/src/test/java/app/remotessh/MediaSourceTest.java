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

  @Test public void imageCacheIdentityIncludesTheItemAndRejectsAmbiguousPaths(){
    Profile profile=new Profile();profile.host="host";profile.user="user";
    ChatItem first=new ChatItem("first","imageView","");first.images.add("file:///work/photo.png");
    ChatItem unique=new ChatItem("unique","imageView","");unique.images.add("/work/other.png");
    Map<String,String> single=MediaFiles.imageScopes(Arrays.asList(first,unique),"/work",profile,"thread");
    assertEquals(ImageDiskCache.scope(profile,"thread","first"),single.get("/work/photo.png"));
    assertEquals(ImageDiskCache.scope(profile,"thread","unique"),single.get("/work/other.png"));
    ChatItem duplicate=new ChatItem("second","imageView","");duplicate.images.add("/work/photo.png");
    Map<String,String> multiple=MediaFiles.imageScopes(Arrays.asList(first,duplicate,unique),"/work",profile,"thread");
    assertEquals("",multiple.get("/work/photo.png"));assertFalse(multiple.get("/work/other.png").isEmpty());
  }

  @Test public void groupedImageCacheUsesTheSameIdentityAsTheVisibleTile(){
    UiText.initialize(RuntimeEnvironment.getApplication());Profile profile=new Profile();profile.host="host";
    ChatItem command=new ChatItem("cmd","commandExecution","ls");command.turnId="turn";
    ChatItem image=new ChatItem("img","imageView","");image.turnId="turn";image.images.add("/work/photo.png");
    List<ChatItem> shown=ToolActivityPresenter.present(Arrays.asList(command,image),"");
    assertEquals(1,shown.size());assertEquals("activityGroup",shown.get(0).type);
    Map<String,String> scopes=MediaFiles.imageScopes(shown,"/work",profile,"thread");
    assertEquals(ImageDiskCache.scope(profile,"thread",shown.get(0).id),scopes.get("/work/photo.png"));
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

  @Test public void groupedToolImagesRemainAvailableInLiveAndRestoredTurns() {
    UiText.initialize(RuntimeEnvironment.getApplication());
    JSONObject command=Json.obj("id","command","type","commandExecution","command",README_COMMAND);
    JSONObject tool=Json.obj("id","tool","type","mcpToolCall","server","sample","tool","image",
        "result",Json.obj("content",new JSONArray().put(Json.obj("type","image","mimeType","image/png","data","abc"))));
    JSONObject generation=Json.obj("id","generated","type","imageGeneration","savedPath","/work/generated.png");
    JSONObject view=Json.obj("id","view","type","imageView","path","/work/view.png");
    ThreadSnapshot snapshot=new ThreadSnapshot(Json.obj("thread",Json.obj("turns",new JSONArray()
        .put(Json.obj("id","turn","items",new JSONArray().put(command).put(tool).put(generation).put(view))))));
    assertNull(snapshot.failure);List<ChatItem> children=snapshot.turns.get(0);
    List<String> expected=Arrays.asList("data:image/png;base64,abc","/work/generated.png","/work/view.png");
    for(String runningTurn:Arrays.asList("turn","")){
      List<ChatItem> presented=ToolActivityPresenter.present(children,runningTurn);
      assertEquals(1,presented.size());assertEquals("activityGroup",presented.get(0).type);
      assertEquals("grouping must keep explicit images in event order",expected,MediaFiles.images(presented.get(0)));
      assertEquals(expected,MediaFiles.imageGallery(children,"/work"));
    }
    assertFalse("a command's literal badge must stay hidden",MediaFiles.images(children.get(0)).contains("https://example.test/verify/badge.svg"));
  }
}
