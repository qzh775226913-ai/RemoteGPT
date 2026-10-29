package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import ru.noties.jlatexmath.JLatexMathAndroid;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MathRenderingTest {
  private static final String EQUATION="\\boxed{\\frac{x_N}{\\bar{c}}\\approx\\frac{x_{ac,w}}{\\bar{c}}+\\eta_t\\frac{a_t}{a_w}V_H\\left(1-\\frac{d\\epsilon}{d\\alpha}\\right)}";
  @Before public void init(){JLatexMathAndroid.init(RuntimeEnvironment.getApplication());MathGlyphs.init(RuntimeEnvironment.getApplication().getAssets());}
  private MessageBodyView body(boolean dark) {
    return new MessageBodyView(RuntimeEnvironment.getApplication(),dark?Color.WHITE:Color.BLACK,0xff888888,0xff333333,v->{},v->{});
  }
  private void await(BooleanSupplier ready) throws Exception {
    long deadline=System.nanoTime()+10_000_000_000L;
    do {
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      if(ready.getAsBoolean())return;
      Thread.sleep(10);
    }while(System.nanoTime()<deadline);
    fail("Formula worker did not produce the expected view within 10s");
  }
  private HorizontalScrollView scroll(View root) {
    if(root instanceof HorizontalScrollView)return (HorizontalScrollView)root;
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
      HorizontalScrollView found=scroll(((ViewGroup)root).getChildAt(i));if(found!=null)return found;
    }
    return null;
  }
  private int spans(View root) {
    int count=0;
    if(root instanceof TextView && ((TextView)root).getText() instanceof Spanned) {
      Spanned text=(Spanned)((TextView)root).getText();count+=text.getSpans(0,text.length(),MathSpan.class).length;
    }
    if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)count+=spans(((ViewGroup)root).getChildAt(i));
    return count;
  }
  @Test public void commonExpressionsAndScreenshotFormulaHaveNativeLayout() {
    for(String tex:new String[]{EQUATION,"x_N", "\\eta_t", "\\frac{d\\epsilon}{d\\alpha}","\\sqrt{x^2+y^2}","\\sum_{i=1}^{n} i", "\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}","\\begin{aligned}a&=b+c\\\\d&=e\\end{aligned}"}) {
      MathRenderer.Formula result=MathRenderer.render(tex,true,32,Color.WHITE);
      assertNotNull(tex,result);assertTrue(result.height>0);assertTrue(result.baseline>0);assertTrue(result.baseline<=result.height);
    }
    MathRenderer.Formula fraction=MathRenderer.render("\\frac{a}{b}",true,32,Color.BLACK);
    MathRenderer.Formula plain=MathRenderer.render("a/b",true,32,Color.BLACK);
    assertTrue("A fraction has vertically stacked numerator and denominator",fraction.height>plain.height);
  }
  @Test public void longExplanationKeepsMoreThan64InlineAndDisplayExpressionsTypeset() throws Exception {
    MessageBodyView body=body(false);StringBuilder source=new StringBuilder();
    for(int i=0;i<100;i++)source.append("半径 \\(r_{"+i+"}\\)，");
    source.append("\\[\\boxed{dT=\\frac12\\rho BW^2c(C_L\\cos\\phi+C_D\\sin\\phi)\\,dr}\\]");
    source.append("最后变量 \\(T\\) 与 \\(v_i\\)。");
    body.setSource(source.toString());await(()->spans(body)==102 && scroll(body)!=null);
    body.setSource(source+"继续补充。");assertEquals(102,spans(body));
  }
  @Test public void unsafeUnsupportedAndExcessiveTexStaysLiteral() throws Exception {
    for(String tex:new String[]{"\\newcommand{\\x}{x}","\\input{/etc/passwd}","\\includegraphics{https://example.com/x}","\\jlmXML{file}","\\definecolor{x}{rgb}{1,0,0}","\\begin{document}x\\end{document}","{".repeat(33)+"x"+"}".repeat(33),"x".repeat(4097),"\\unknown{x}"}) {
      assertFalse(tex,MathRenderer.supported(tex));assertNull(MathRenderer.render(tex,true,32,Color.WHITE));
    }
    assertNull(MathRenderer.render("\\frac{a}",true,32,Color.WHITE));
    assertNull(MathRenderer.render("\\sqrt",true,32,Color.WHITE));
    MessageBodyView body=body(false);body.setSource("\\[\\unknown{x}\\]");
    assertTrue(body.getChildAt(0) instanceof MathContentView);
    TextView fallback=(TextView)((MathContentView)body.getChildAt(0)).getChildAt(0);
    assertEquals("\\[\\unknown{x}\\]",fallback.getText().toString());
  }
  @Test public void inlineMetricsSelectionAndCodeBlocksKeepOriginalSource() throws Exception {
    MessageBodyView body=body(false);
    body.setSource("其中 \\(x_N\\) 是焦点，$\\eta_t$ 是动压比。\n\n```tex\n\\frac{a}{b}\n```");
    await(()->spans(body)==2);
    MathContentView prose=(MathContentView)body.getChildAt(0);TextView text=(TextView)prose.getChildAt(0);
    assertEquals("其中 \\(x_N\\) 是焦点，$\\eta_t$ 是动压比。",text.getText().toString());assertTrue(text.isTextSelectable());
    Spanned rendered=(Spanned)text.getText();MathSpan span=rendered.getSpans(0,rendered.length(),MathSpan.class)[0];
    Paint.FontMetricsInt fm=new Paint.FontMetricsInt();span.getSize(text.getPaint(),rendered,0,1,fm);
    assertTrue(fm.ascent<=-span.formula.baseline);assertTrue(fm.descent>=span.formula.height-span.formula.baseline);
    assertTrue(body.getChildAt(1) instanceof LinearLayout);assertFalse(body.getChildAt(1) instanceof MathContentView);
  }
  @Test public void inlineMathPreservesMarkdownEmphasisAndLinkActions() throws Exception {
    String[] opened={""};MessageBodyView body=new MessageBodyView(RuntimeEnvironment.getApplication(),Color.BLACK,0xff888888,0xffeeeeee,v->opened[0]=v,v->{});
    body.setSource("**变量 \\(x_N\\)**，查看 [公式 $y^2$](https://example.com/math)");
    await(()->spans(body)==2);
    TextView text=(TextView)((MathContentView)body.getChildAt(0)).getChildAt(0);Spanned rendered=(Spanned)text.getText();
    assertEquals("变量 \\(x_N\\)，查看 公式 $y^2$",rendered.toString());
    assertEquals(1,rendered.getSpans(0,rendered.length(),android.text.style.StyleSpan.class).length);
    android.text.style.ClickableSpan[] links=rendered.getSpans(0,rendered.length(),android.text.style.ClickableSpan.class);
    assertEquals(1,links.length);links[0].onClick(text);assertEquals("https://example.com/math",opened[0]);
  }
  @Test public void displayMathScrollsInsteadOfClippingAndLongPressCopiesTex() throws Exception {
    String[] copied={""};MessageBodyView body=new MessageBodyView(RuntimeEnvironment.getApplication(),Color.WHITE,0xff888888,0xff222222,v->{},v->copied[0]=v);
    String raw="\\[\n"+EQUATION+"\n\n\\]";body.setSource(raw);
    await(()->scroll(body)!=null);
    layout(body,400);HorizontalScrollView horizontal=scroll(body);View formula=horizontal.getChildAt(0);
    assertTrue("Long formula keeps its full width",formula.getMeasuredWidth()>horizontal.getMeasuredWidth());
    assertTrue(formula.performLongClick());assertEquals(raw,copied[0]);
    horizontal.scrollTo(formula.getMeasuredWidth(),0);assertTrue(horizontal.getScrollX()>0);
    int offset=horizontal.getScrollX();body.setSource(raw+"\n后续文本");layout(body,400);
    assertEquals("Streaming must retain a formula's horizontal reading position",offset,scroll(body).getScrollX());
  }
  @Test public void streamingReplacementCannotPublishOldFormulaIntoNewText() throws Exception {
    MessageBodyView body=body(false);body.setSource("\\["+EQUATION);
    assertTrue(body.getChildAt(0) instanceof TextView);
    body.setSource("\\["+EQUATION+"\\]");body.setSource("新回复 \\(y^2\\)");
    await(()->spans(body)==1);
    assertNull(scroll(body));
    assertEquals("新回复 \\(y^2\\)",((TextView)((MathContentView)body.getChildAt(0)).getChildAt(0)).getText().toString());
  }
  @Test public void appendingStreamingTextKeepsAlreadyTypesetFormulaVisible() throws Exception {
    MessageBodyView body=body(false);String first="变量 \\(x_N\\)";body.setSource(first);
    await(()->spans(body)==1);
    View existing=body.getChildAt(0);body.setSource(first+"，继续说明。");
    assertSame(existing,body.getChildAt(0));
    assertEquals("Completed math must not flash back to literal TeX on each delta",1,spans(body));
    body.setSource(first+"，新变量 \\(y^2\\)");
    assertEquals("Existing math stays visible while the new formula is pending",1,spans(body));
    await(()->spans(body)==2);
  }
  @Test public void deltaAndCompletedSnapshotRetainTexExactlyAcrossHistory() throws Exception {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.threadId="math-thread";
    Method event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
    String first="\\[\\frac{x_N}{",second="\\bar{c}}\\]";
    for(String chunk:new String[]{first,second})event.invoke(app,Json.obj("method","item/agentMessage/delta","params",Json.obj("threadId","math-thread","itemId","answer","delta",chunk)));
    assertEquals(first+second,app.items.get(0).text);
    Method apply=RemoteApp.class.getDeclaredMethod("applyThread",JSONObject.class,String.class,boolean.class);apply.setAccessible(true);
    apply.invoke(app,Json.obj("thread",Json.obj("id","math-thread","status",Json.obj("type","idle"),"turns",new JSONArray().put(Json.obj("id","turn","status","completed","items",new JSONArray().put(Json.obj("id","answer","type","agentMessage","text",first+second)))))),"math-thread",true);
    assertEquals(first+second,app.items.get(0).text);
  }
  @Test public void detachedViewsRejectLateLayoutAndResumeWhenReattached() throws Exception {
    org.robolectric.android.controller.ActivityController<android.app.Activity> controller=
        Robolectric.buildActivity(android.app.Activity.class).setup();
    try {
      MessageBodyView body=body(false);controller.get().setContentView(body);
      body.setSource("变量 \\(x_N\\)");
      controller.get().setContentView(new FrameLayout(controller.get()));
      MathRenderer.submit(java.util.Collections.emptyList(),32,Color.BLACK,v->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals("Detached views cannot accept a stale drawable",0,spans(body));
      controller.get().setContentView(body);await(()->spans(body)==1);
      assertEquals("变量 \\(x_N\\)",((TextView)((MathContentView)body.getChildAt(0)).getChildAt(0)).getText().toString());
    } finally {controller.pause().stop().destroy();}
  }
  @Test @Config(sdk=26) public void android8AndLargerFontSizesKeepCompleteFormulaBounds() {
    MathRenderer.Formula normal=MathRenderer.render(EQUATION,true,32,Color.BLACK);
    MathRenderer.Formula large=MathRenderer.render(EQUATION,true,48,Color.BLACK);
    assertNotNull(normal);assertNotNull(large);
    assertTrue(large.width>normal.width*1.4f);assertTrue(large.height>normal.height*1.4f);
    assertTrue(large.baseline<large.height);
  }
  @Test public void lightAndDarkReferenceScreenshots() throws Exception {
    for(boolean dark:new boolean[]{false,true}) {
      MessageBodyView body=body(dark);
      body.setSource("## 二、从全机焦点公式理解\n\n在忽略部分高阶干扰效应时，全机焦点位置可近似表示为：\n\n\\["+EQUATION+"\\]\n\n其中：\n\n- \\(x_N\\)：全机焦点位置\n- \\(x_{ac,w}\\)：机翼气动焦点位置\n- \\(\\eta_t\\)：平尾动压比\n- \\(a_t/a_w\\)：平尾与机翼升力线斜率之比\n- \\(V_H\\)：平尾容积系数\n- \\(d\\epsilon/d\\alpha\\)：下洗角随迎角的变化率");
      await(()->scroll(body)!=null && spans(body)==6);layout(body,760);
      Bitmap bitmap=Bitmap.createBitmap(760,body.getMeasuredHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(dark?Color.BLACK:Color.WHITE);body.draw(canvas);
      File folder=new File("build/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());
      try(FileOutputStream out=new FileOutputStream(new File(folder,"formula-reference-"+(dark?"dark":"light")+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
      bitmap.recycle();
    }
  }
  private void layout(View view,int width) {
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());
  }
}
