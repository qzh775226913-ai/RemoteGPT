package app.remotessh;

import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MediaTest {
  @Test public void parsesImagesAlongsideTextAndGeneratedImages() {
    ChatItem item = ChatItem.from(Json.obj("id", "u", "type", "userMessage", "content", new JSONArray()
        .put(Json.obj("type", "text", "text", "看看这张图"))
        .put(Json.obj("type", "localImage", "path", "/home/u/photo.png"))));
    assertEquals("看看这张图", item.text);
    assertEquals("/home/u/photo.png", item.images.get(0));
    assertEquals("/tmp/generated.png", ChatItem.from(Json.obj("type", "imageGeneration", "savedPath", "/tmp/generated.png", "result", "base64")).images.get(0));
    assertEquals("data:image/png;base64,abc", ChatItem.from(Json.obj("type", "imageGeneration", "result", "abc")).images.get(0));
    assertEquals("/tmp/view.png", ChatItem.from(Json.obj("type", "imageView", "path", "/tmp/view.png")).images.get(0));
    ChatItem mcp = ChatItem.from(Json.obj("type", "mcpToolCall", "result", Json.obj("content", new JSONArray().put(Json.obj("type", "image", "mimeType", "image/png", "data", "abc")))));
    assertEquals("data:image/png;base64,abc", mcp.images.get(0));
  }

  @Test public void resolvesRemoteDownloadsWithoutUsingAndroidFilesystem() {
    assertEquals("/mnt/data/report.pdf", MediaFiles.remotePath("sandbox:/mnt/data/report.pdf", "/work"));
    assertEquals("/work/中文 图.png", MediaFiles.remotePath("%E4%B8%AD%E6%96%87%20%E5%9B%BE.png", "/work"));
    assertEquals("/tmp/a b.png", MediaFiles.remotePath("file:///tmp/a%20b.png", ""));
    for (String bad : new String[] {"content://private/data", "javascript:alert(1)", "file://other/tmp/a"}) {
      try { MediaFiles.remotePath(bad, "/work"); fail(bad); } catch (IllegalArgumentException expected) { }
    }
  }

  @Test public void boundsStreamsEvenWhenSizeIsUnknown() throws Exception {
    try { MediaFiles.copy(new ByteArrayInputStream(new byte[12]), new ByteArrayOutputStream(), 10); fail(); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("大小")); }
  }

  @Test public void remoteMarkdownLinkDispatchesToDownloadHandler() {
    final String[] opened = {""};
    android.text.Spanned text = (android.text.Spanned) Markdown.render("[下载文件](sandbox:/mnt/data/report.pdf)", 0, 0, source -> opened[0] = source);
    android.text.style.ClickableSpan[] links = text.getSpans(0, text.length(), android.text.style.ClickableSpan.class);
    assertEquals(1, links.length);
    links[0].onClick(null);
    assertEquals("sandbox:/mnt/data/report.pdf", opened[0]);
  }

  @Test public void imageOnlyTurnSendsRemotePathAndRetainsAttachmentOnError() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true; app.profile = new Profile(); app.threadId = "thread";
    app.draftImages.add("/home/u/photo.png");
    android.graphics.Bitmap preview = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888);
    app.draftImagePreviews.put("/home/u/photo.png", preview);
    PipedOutputStream server = new PipedOutputStream();
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    RpcClient rpc = new RpcClient(new PipedInputStream(server), sent, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, rpc);
    rpc.start();
    try {
      app.reasoningEffort = "high";
      app.send("");
      for (int i = 0; i < 100 && !sent.toString().contains("turn/start"); i++) Thread.sleep(10);
      JSONObject request = new JSONObject(sent.toString("UTF-8").trim());
      assertEquals("high", request.getJSONObject("params").getString("effort"));
      assertEquals("auto", request.getJSONObject("params").getString("summary"));
      JSONArray input = request.getJSONObject("params").getJSONArray("input");
      assertEquals(1, input.length());
      assertEquals("localImage", input.getJSONObject(0).getString("type"));
      assertEquals("/home/u/photo.png", input.getJSONObject(0).getString("path"));
      server.write((Json.obj("id", request.getLong("id"), "error", Json.obj("code", -1, "message", "test failure")) + "\n").getBytes("UTF-8"));
      server.flush();
      for (int i = 0; i < 100 && app.busy; i++) {
        Thread.sleep(10); Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
      }
      assertFalse(app.busy);
      assertEquals(1, app.draftImages.size());
      assertSame(preview, app.draftImagePreviews.get("/home/u/photo.png"));
      assertEquals("/home/u/photo.png", app.items.get(0).images.get(0));
    } finally { rpc.close(); server.close(); field.set(app, null); }
  }
}
