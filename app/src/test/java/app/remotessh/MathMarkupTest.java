package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class MathMarkupTest {
  @Test public void explicitInlineAndDisplayDelimitersRetainCanonicalSource() {
    String source="其中 \\(x_N\\) 与 $a_t/a_w$：\n\\[\\frac{x_N}{\\bar c}\\]\n$$\\eta_t V_H$$";
    List<MathMarkup.Part> parts=MathMarkup.parts(source);
    assertEquals(4,parts.stream().filter(p->p.kind!=MathMarkup.Kind.TEXT).count());
    assertEquals(2,parts.stream().filter(p->p.kind==MathMarkup.Kind.DISPLAY).count());
    assertEquals(source,parts.stream().map(p->p.raw).reduce("",String::concat));
    assertEquals("x_N",parts.get(1).tex);
  }
  @Test public void codeCurrencyEscapesAndLinkDestinationsAreNotEquations() {
    for(String source:new String[]{"价格 $5 和 $10", "Cost $5, $10 and $20", "`$x$` and `\\(y\\)`", "`` $x$ ` \\(y\\) ``", "\\$x\\$", "\\\\(x\\\\)", "[link](https://example.com/$x$)", "```tex\n$x$\n```"})
      assertFalse(source,MathMarkup.containsMath(source));
  }
  @Test public void incompleteStreamsStayLiteralUntilMatchingCloseArrives() {
    String head="说明 \\[\\frac{x_N}{";
    assertFalse(MathMarkup.containsMath(head));
    assertFalse(MathMarkup.containsMath(head+"\\bar{c}} $not-yet$"));
    assertTrue(MathMarkup.containsMath(head+"\\bar{c}}\\]"));
    assertFalse(MathMarkup.containsMath("价格 $5"));
    assertTrue(MathMarkup.containsMath("价格 $5；变量 \\(x\\)"));
  }
  @Test public void emptyOrOversizedInputAndManyExpressionsHaveBoundedWork() {
    assertFalse(MathMarkup.containsMath("$$$$ \\( \\)"));
    String source="$x$ ".repeat(600);
    assertEquals(512,MathMarkup.parts(source).stream().filter(p->p.kind!=MathMarkup.Kind.TEXT).count());
    assertEquals(source,MathMarkup.parts(source).stream().map(p->p.raw).reduce("",String::concat));
    assertFalse(MathMarkup.containsMath("a".repeat(131073)+"$x$"));
  }
}
