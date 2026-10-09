package app.remotessh;

import static org.junit.Assert.*;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MessageBodyViewTest {
  @Test public void blocksPreserveCopyTextAndIgnoreShorterEmbeddedFence() {
    var blocks=MessageBodyView.blocks("说明\n\n````sh\nprintf '$HOME'\n```\necho done\n````\n结束");
    assertEquals(3,blocks.size());assertFalse(blocks.get(0).code);
    assertTrue(blocks.get(1).code);assertEquals("sh",blocks.get(1).language);
    assertEquals("printf '$HOME'\n```\necho done\n",blocks.get(1).text);
    assertEquals("结束",blocks.get(2).text);
  }
  @Test public void streamingFenceAndTildeBlocksAreCopyable() {
    var blocks=MessageBodyView.blocks("~~~text\n第一行\n第二行");
    assertEquals(1,blocks.size());assertTrue(blocks.get(0).code);assertEquals("第一行\n第二行",blocks.get(0).text);
    assertEquals("echo ready\n",MessageBodyView.blocks("  ```\necho ready\n  ```").get(0).text);
  }
  @Test public void inlineCodeCopiesLiteralWhileLinksKeepTheirDestination() {
    String[] copied={""},opened={""};
    Spanned text=(Spanned)Markdown.render("复制 `git status`，打开 [文档](https://example.com)",0,0,v->opened[0]=v,v->copied[0]=v);
    ClickableSpan[] spans=text.getSpans(0,text.length(),ClickableSpan.class);assertEquals(2,spans.length);
    spans[0].onClick(new android.view.View(RuntimeEnvironment.getApplication()));assertEquals("git status",copied[0]);
    spans[1].onClick(new android.view.View(RuntimeEnvironment.getApplication()));assertEquals("https://example.com",opened[0]);
  }
}
