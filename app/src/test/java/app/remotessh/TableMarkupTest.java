package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class TableMarkupTest {
  private TableMarkup.Table table(String raw){return TableMarkup.parts(raw).stream().filter(p->p.table!=null).findFirst().get().table;}
  @Test public void recognizesOptionalOuterPipesAndColumnAlignment() {
    TableMarkup.Table t=table("名称 | 数值 | 备注\n:--- | ---: | :---:\n转速 | 1200 | 正常");
    assertEquals(Arrays.asList(0,2,1),t.align);assertEquals(2,t.rows.size());assertEquals("1200",t.rows.get(1).get(1));
    assertEquals(1,table("| 名称 |\n| --- |\n| 内容 |").columns());
    assertEquals(1,table("| 名称 | 备注 |\n| --- | --- |\n").rows.size());
  }
  @Test public void escapedPipesEmptyCellsAndUnevenRowsFollowGfm() {
    TableMarkup.Table t=table("| 名称 | 表达式 |\n|---|---|\n| A\\|B | `x\\|y` |\n| | |\n| 缺项 |\n| 一 | 二 | 多余 |");
    assertEquals(Arrays.asList("A|B","`x|y`"),t.rows.get(1));assertEquals(Arrays.asList("",""),t.rows.get(2));
    assertEquals(Arrays.asList("缺项",""),t.rows.get(3));assertEquals(Arrays.asList("一","二"),t.rows.get(4));
  }
  @Test public void partsRetainExactCrLfSourceAndSeparateAdjacentProse() {
    String raw="说明\r\n\r\n| A | B |\r\n|---|---|\r\n| 1 | 2 |\r\n\r\n结尾";
    List<TableMarkup.Part> parts=TableMarkup.parts(raw);
    assertEquals(3,parts.size());assertNull(parts.get(0).table);assertNotNull(parts.get(1).table);assertNull(parts.get(2).table);
    assertEquals(raw,parts.stream().map(p->p.raw).reduce("",String::concat));
  }
  @Test public void incompleteDelimitersOrdinaryPipesAndIndentedCodeRemainText() {
    for(String raw:new String[]{"A | B\n---","A | B\n--- | 待续","普通 | 文本", "标题\n---", "    | A | B |\n    |---|---|", "A\\|B\n---"})
      assertFalse(raw,TableMarkup.parts(raw).stream().anyMatch(p->p.table!=null));
  }
  @Test public void explicitMathKeepsAbsoluteValuePipesAndTexNormCommandsIntact() {
    TableMarkup.Table t=table("| 公式 | 说明 |\n|---|---|\n| \\(|x|\\) | 绝对值 |\n| $\\|x\\|$ | 范数 |");
    assertEquals(2,t.columns());assertEquals("\\(|x|\\)",t.rows.get(1).get(0));
    assertEquals("$\\|x\\|$",t.rows.get(2).get(0));assertEquals("范数",t.rows.get(2).get(1));
  }
  @Test public void oversizedTablesRemainCompleteLiteralWithoutDroppingContent() {
    String raw="| A | B |\n|---|---|\n"+"| x | y |\n".repeat(140);
    assertFalse(TableMarkup.parts(raw).stream().anyMatch(p->p.table!=null));
    assertEquals(raw,TableMarkup.parts(raw).stream().map(p->p.raw).reduce("",String::concat));
    assertFalse(TableMarkup.parts("x".repeat(131073)+raw).stream().anyMatch(p->p.table!=null));
  }
}
