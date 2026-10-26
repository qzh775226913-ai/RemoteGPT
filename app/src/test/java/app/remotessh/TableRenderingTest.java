package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TableRenderingTest {
  static final String WIDE="| 参数名称 | 建议数值 | 参数说明与适用范围 | 备注 |\n|:---|---:|:---:|---|\n| 桨叶数量 | 3 | 该列包含较长的中文说明，仍需保持整列宽度一致，不能挤到相邻列。 | 正常 |\n| 转速 | 1200 | 稳态条件 | 待确认 |";
  private MessageBodyView body(){return new MessageBodyView(RuntimeEnvironment.getApplication(),0xff171717,0xff626262,0xffeceef1,v->{},v->{});}
  private void layout(View view,int width) {
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());
  }
  private View find(View root,String description) {
    if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=find(((ViewGroup)root).getChildAt(i),description);if(result!=null)return result;}
    return null;
  }
  private int spans(View root,Class<?> kind) {
    int count=0;if(root instanceof TextView && ((TextView)root).getText() instanceof Spanned){Spanned text=(Spanned)((TextView)root).getText();count+=text.getSpans(0,text.length(),kind).length;}
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)count+=spans(((ViewGroup)root).getChildAt(i),kind);return count;
  }
  private TextView formulaText(View root) {
    if(root instanceof TextView && ((TextView)root).getText() instanceof Spanned) {
      Spanned text=(Spanned)((TextView)root).getText();
      if(text.getSpans(0,text.length(),MathSpan.class).length>0)return (TextView)root;
    }
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
      TextView found=formulaText(((ViewGroup)root).getChildAt(i));if(found!=null)return found;
    }
    return null;
  }
  private void await(BooleanSupplier ready) throws Exception {
    for(int i=0;i<1000;i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("Pending table formulas");
  }
  @Test public void wideGridKeepsColumnAndRowBoundsAlignedAndScrollsAsOneUnit() {
    MessageBodyView body=body();body.setSource(WIDE);layout(body,500);
    TableContentView table=(TableContentView)body.getChildAt(0);HorizontalScrollView scroll=(HorizontalScrollView)table.getChildAt(1);
    TableLayout grid=(TableLayout)scroll.getChildAt(0);assertTrue(grid.getWidth()>scroll.getWidth());
    TableRow header=(TableRow)grid.getChildAt(0);
    for(int r=1;r<grid.getChildCount();r++) {
      TableRow row=(TableRow)grid.getChildAt(r);
      for(int c=0;c<header.getChildCount();c++) {
        assertEquals(header.getChildAt(c).getLeft(),row.getChildAt(c).getLeft());
        assertEquals(header.getChildAt(c).getWidth(),row.getChildAt(c).getWidth());
        assertEquals(row.getHeight(),row.getChildAt(c).getHeight());
      }
    }
    scroll.scrollTo(grid.getWidth(),0);assertTrue(table.offset()>0);
  }
  @Test public void streamingRetainsGridCellsAndHorizontalPositionAndLatestCopy() {
    String[] copy={""};MessageBodyView body=new MessageBodyView(RuntimeEnvironment.getApplication(),0xff171717,0xff626262,0xffeceef1,v->{},v->copy[0]=v);
    body.setSource(WIDE);layout(body,500);TableContentView table=(TableContentView)body.getChildAt(0);
    HorizontalScrollView scroll=(HorizontalScrollView)table.getChildAt(1);TableLayout grid=(TableLayout)scroll.getChildAt(0);
    View firstCell=((TableRow)grid.getChildAt(0)).getChildAt(0);scroll.scrollTo(200,0);int offset=table.offset();assertTrue(offset>0);
    String updated=WIDE+"\n| 新增 | 300 | 新的一行 | 完成 |";body.setSource(updated);layout(body,500);
    assertSame(table,body.getChildAt(0));assertSame(firstCell,((TableRow)grid.getChildAt(0)).getChildAt(0));assertEquals(offset,table.offset());
    assertEquals(4,grid.getChildCount());find(body,"复制表格").performClick();assertEquals(updated,copy[0]);
  }
  @Test public void duplicateHeadersHaveIndependentOffsetsAcrossUpdates() {
    MessageBodyView body=body();body.setSource(WIDE+"\n\n说明\n\n"+WIDE);layout(body,500);
    TableContentView first=(TableContentView)body.getChildAt(0),second=(TableContentView)body.getChildAt(2);
    ((HorizontalScrollView)first.getChildAt(1)).scrollTo(100,0);((HorizontalScrollView)second.getChildAt(1)).scrollTo(200,0);
    int x=first.offset(),y=second.offset();assertTrue(x>0 && y>x);
    body.setSource(WIDE+"\n\n说明\n\n"+WIDE+"\n\n结尾");layout(body,500);
    assertSame(first,body.getChildAt(0));assertSame(second,body.getChildAt(2));assertEquals(x,first.offset());assertEquals(y,second.offset());
  }
  @Test public void tableMathLinksAndFencedLiteralTablesCoexist() throws Exception {
    MessageBodyView body=body();body.setSource("说明 \\(\\Omega r\\)\n\n| 变量 | 参考 |\n|---|---|\n| \\(\\Omega r\\) | [文档](https://example.com) |\n\n```text\n"+WIDE+"\n```\n结束");
    assertTrue(body.getChildAt(1) instanceof TableContentView);await(()->spans(body,MathSpan.class)==2);
    TextView prose=formulaText(body.getChildAt(0)),cell=formulaText(body.getChildAt(1));
    assertTrue("Table text must remain smaller than surrounding prose",cell.getTextSize()<prose.getTextSize());
    Spanned outside=(Spanned)prose.getText(),inside=(Spanned)cell.getText();
    assertTrue("The same formula needs a distinct smaller table layout",
        inside.getSpans(0,inside.length(),MathSpan.class)[0].formula.width
            <outside.getSpans(0,outside.length(),MathSpan.class)[0].formula.width);
    assertEquals(1,spans(body,ClickableSpan.class));assertNotNull(find(body,"复制内容块 1"));
    assertEquals(1,java.util.stream.IntStream.range(0,body.getChildCount()).filter(i->body.getChildAt(i) instanceof TableContentView).count());
  }
  @Test public void incompleteHeaderCanTurnIntoTableWithoutLosingSurroundingProse() {
    MessageBodyView body=body();body.setSource("| A | B |\n|---|");assertTrue(body.getChildAt(0) instanceof TextView);
    body.setSource("| A | B |\n|---|---|\n| 一 | 二 |\n\n结束");
    assertTrue(body.getChildAt(0) instanceof TableContentView);assertEquals("结束",((TextView)body.getChildAt(1)).getText().toString());
    body.setSource("纯文本");assertTrue(body.getChildAt(0) instanceof TextView);
  }
  @Test public void narrowTableFitsViewportWithoutForcedHorizontalScroll() {
    MessageBodyView body=body();body.setSource("| A | B |\n|---|---|\n| 1 | 2 |");layout(body,760);
    TableContentView table=(TableContentView)body.getChildAt(0);HorizontalScrollView scroll=(HorizontalScrollView)table.getChildAt(1);
    assertEquals(scroll.getWidth(),scroll.getChildAt(0).getWidth());scroll.scrollTo(1000,0);assertEquals(0,table.offset());
  }
  @Test public void lightConversationSurfacesAndTableScreenshot() throws Exception {conversation("light");}
  @Test @Config(qualifiers="w411dp-h891dp-night-xhdpi") public void darkConversationTableScreenshot() throws Exception {conversation("dark");}
  private void conversation(String theme) throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      MainActivity activity=controller.get();RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.profile.name="测试主机";
      app.connected=true;app.conversationStore.select("tables-preview");app.threadTitle="参数对比表";
      app.items.add(new ChatItem("question","userMessage","把参数整理成表格，保留计算公式。"));
      app.items.add(new ChatItem("reply","agentMessage","以下为参数对比，表格较宽时可左右滑动。\n\n"+WIDE+"\n| 角速度 | \\(\\Omega r\\) | 局部切向速度 | 稳态 |\n\n其中 \\(r\\) 表示半径。"));
      app.changed();View root=activity.findViewById(android.R.id.content);
      int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;
      for(int i=0;i<35;i++) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);Thread.sleep(15);
      }
      assertNotNull("Conversation must contain the rendered table",find(root,"复制表格"));
      if(theme.equals("light")) {
        java.lang.reflect.Field bg=MainActivity.class.getDeclaredField("bg"),panel=MainActivity.class.getDeclaredField("panel");bg.setAccessible(true);panel.setAccessible(true);
        assertNotEquals("Page background must distinguish white reply cards",Color.WHITE,bg.getInt(activity));
        assertNotEquals("Composer surface must distinguish the page",bg.getInt(activity),panel.getInt(activity));
      }
      Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
      java.io.File folder=new java.io.File("build/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());
      try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"table-conversation-"+theme+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    } finally {controller.pause().stop().destroy();}
  }
  @Test @Config(sdk=26) public void android8KeepsScrollableNativeTable() {wideGridKeepsColumnAndRowBoundsAlignedAndScrollsAsOneUnit();}
}
