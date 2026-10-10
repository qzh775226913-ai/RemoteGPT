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
  @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  @Config(qualifiers="w411dp-h891dp-xhdpi")
  public void paragraphBreaksAreShortAndStableDuringStreaming() {
    MessageBodyView body=new MessageBodyView(RuntimeEnvironment.getApplication(),0xff000000,0xff444444,0xffeeeeee,v->{},v->{});
    String source="\n\n第一段。\n\n\n  \n第二段。\n\n第三段。\n\n";body.setSource(source);
    android.widget.TextView prose=(android.widget.TextView)body.getChildAt(0);
    assertEquals("第一段。\n\n第二段。\n\n第三段。",prose.getText().toString());
    assertFalse(prose.getIncludeFontPadding());
    body.measure(android.view.View.MeasureSpec.makeMeasureSpec(600,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));body.layout(0,0,600,body.getMeasuredHeight());
    android.text.Layout layout=prose.getLayout();assertEquals(5,layout.getLineCount());
    int blank=layout.getLineBottom(1)-layout.getLineTop(1),line=layout.getLineBottom(0)-layout.getLineTop(0);
    assertTrue("Paragraph gaps must be smaller than a full blank text line: "+blank+" / "+line,blank<line*.6f);
    assertTrue("Paragraphs must retain visible separation",blank>=8);
    body.setSource(source+"后续文字。");assertSame(prose,body.getChildAt(0));
    assertEquals(3,((android.text.Spanned)prose.getText()).getSpans(0,prose.length(),android.text.style.LineHeightSpan.class).length);
  }

  @Test public void proseWhitespaceNeverChangesLiteralCodeCopiesOrLinks() {
    String[] copied={""},opened={""};
    MessageBodyView body=new MessageBodyView(RuntimeEnvironment.getApplication(),0xff000000,0xff444444,0xffeeeeee,v->opened[0]=v,v->copied[0]=v);
    body.setSource("\n\n说明 [文档](https://example.com)\n\n\n```text\n\n  原样内容  \n\n```\n\n结束\n\n");
    assertEquals(3,body.getChildCount());android.widget.TextView prose=(android.widget.TextView)body.getChildAt(0);
    Spanned text=(Spanned)prose.getText();text.getSpans(0,text.length(),ClickableSpan.class)[0].onClick(prose);assertEquals("https://example.com",opened[0]);
    android.widget.LinearLayout code=(android.widget.LinearLayout)body.getChildAt(1);
    ((android.widget.LinearLayout)code.getChildAt(0)).getChildAt(1).performClick();assertEquals("\n  原样内容  \n\n",copied[0]);
    assertEquals("说明 文档",prose.getText().toString());assertEquals("结束",((android.widget.TextView)body.getChildAt(2)).getText().toString());
    assertEquals(0,((android.widget.LinearLayout.LayoutParams)code.getLayoutParams()).bottomMargin);
  }

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
