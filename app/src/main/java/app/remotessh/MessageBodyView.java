package app.remotessh;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.LinkMovementMethod;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.LineHeightSpan;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** Native text and copyable fenced blocks; remote content is never executed. */
final class MessageBodyView extends LinearLayout {
  static final class Block {
    final boolean code; final String language, text; final TableMarkup.Table table;
    Block(boolean code, String language, String text) { this.code=code; this.language=language; this.text=text; this.table=null; }
    Block(TableMarkup.Table table) {this.code=false;this.language="";this.text=table.raw;this.table=table;}
  }
  private final int fg, muted, panel;
  private final int textSize, lineSpacing;
  private final Consumer<String> open, copy;
  private String source;

  MessageBodyView(Context context, int fg, int muted, int panel, Consumer<String> open, Consumer<String> copy) {
    this(context, fg, muted, panel, open, copy, 16, 5);
  }
  MessageBodyView(Context context, int fg, int muted, int panel, Consumer<String> open, Consumer<String> copy,
      int textSize, int lineSpacing) {
    super(context); setOrientation(VERTICAL);
    this.fg=fg; this.muted=muted; this.panel=panel; this.open=open; this.copy=copy;
    this.textSize=textSize; this.lineSpacing=lineSpacing;
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private TextView text(String value, int size, int color) {
    TextView view=new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(color);
    return view;
  }
  private CharSequence proseText(String value) {
    // Display whitespace separately from the canonical source used for copying.
    // A Markdown paragraph break is a small gap, not another full text line.
    return paragraphText(Markdown.render(proseDisplay(value),muted,panel,open,copy),dp(4));
  }
  static String proseDisplay(String value) {
    return value.trim().replace("\r\n","\n").replaceAll("\\n[\\t ]*\\n(?:[\\t ]*\\n)*","\n\n");
  }
  static CharSequence paragraphText(CharSequence value,int blankHeight) {
    SpannableStringBuilder rendered=new SpannableStringBuilder(value);
    for(int i=1;i<rendered.length();i++)if(rendered.charAt(i)=='\n' && rendered.charAt(i-1)=='\n')
      rendered.setSpan(new ParagraphGap(blankHeight),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return rendered;
  }
  private static final class ParagraphGap implements LineHeightSpan {
    final int height;
    ParagraphGap(int height){this.height=height;}
    @Override public void chooseHeight(CharSequence text,int start,int end,int spanStartV,int v,android.graphics.Paint.FontMetricsInt fm) {
      fm.ascent=fm.top=-height;fm.descent=fm.bottom=0;
    }
  }

  static List<Block> blocks(String source) {
    List<Block> result=new ArrayList<>(); StringBuilder buffer=new StringBuilder();
    String fence="", language="";
    String[] lines=source.split("\n", -1);
    Pattern opening=Pattern.compile("^ {0,3}(`{3,}|~{3,})([^\\r]*)\\r?$");
    for (int i=0;i<lines.length;i++) {
      String line=lines[i]; Matcher match=opening.matcher(line);
      boolean close=!fence.isEmpty() && line.matches(" {0,3}" + Pattern.quote(fence.substring(0,1))
          + "{" + fence.length() + ",}[ \\t\\r]*");
      if (close || (fence.isEmpty() && match.matches())) {
        if (close) { result.add(new Block(true, language, buffer.toString())); fence=""; language=""; }
        else {
          if (buffer.length()>0) result.add(new Block(false, "", buffer.toString()));
          fence=match.group(1); language=match.group(2).trim();
        }
        buffer.setLength(0); continue;
      }
      buffer.append(line); if (i<lines.length-1) buffer.append('\n');
    }
    if (!fence.isEmpty() || buffer.length()>0) result.add(new Block(!fence.isEmpty(), language, buffer.toString()));
    return result;
  }

  private static List<Block> contentBlocks(String source) {
    List<Block> result=new ArrayList<>();
    for(Block block:blocks(source)) {
      if(block.code){result.add(block);continue;}
      for(TableMarkup.Part part:TableMarkup.parts(block.text))
        result.add(part.table==null?new Block(false,"",part.raw):new Block(part.table));
    }
    return result;
  }

  void setSource(String value) {
    if (value.equals(source)) return;
    source=value; List<Block> parts=contentBlocks(value);
    boolean math=parts.size()==1 && !parts.get(0).code && parts.get(0).table==null && MathMarkup.containsMath(parts.get(0).text);
    if(math && getChildCount()==1 && getChildAt(0) instanceof MathContentView) {
      ((MathContentView)getChildAt(0)).setSource(parts.get(0).text);return;
    }
    // Ordinary streaming prose keeps its existing selectable view.
    if(!math && parts.size()==1 && !parts.get(0).code && parts.get(0).table==null && getChildCount()==1 && getChildAt(0) instanceof TextView) {
      TextView prose=(TextView)getChildAt(0);CharSequence rendered=proseText(parts.get(0).text);
      prose.setText(rendered);prose.setVisibility(rendered.length()==0?GONE:VISIBLE);return;
    }
    Map<String,ArrayDeque<TableContentView>> existing=new HashMap<>();
    for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof TableContentView) {
      TableContentView table=(TableContentView)getChildAt(i);
      existing.computeIfAbsent(table.key(),key->new ArrayDeque<>()).add(table);
    }
    removeAllViews(); int index=0;
    for (Block block : parts) {
      if(block.table!=null) {
        ArrayDeque<TableContentView> candidates=existing.get(block.table.key);
        TableContentView table=candidates==null || candidates.isEmpty()
            ?new TableContentView(getContext(),fg,muted,panel,open,copy):candidates.remove();
        table.setTable(block.table);addView(table,new LayoutParams(-1,-2));continue;
      }
      if (!block.code) {
        if(MathMarkup.containsMath(block.text)) {
          MathContentView formulas=new MathContentView(getContext(),fg,muted,panel,open,copy,textSize,lineSpacing);
          formulas.setSource(block.text);addView(formulas,new LayoutParams(-1,-2));continue;
        }
        TextView prose=text("", textSize, fg); prose.setLineSpacing(dp(lineSpacing),1);
        prose.setIncludeFontPadding(false);
        CharSequence rendered=proseText(block.text);prose.setText(rendered);
        prose.setVisibility(rendered.length()==0?GONE:VISIBLE);
        prose.setTextIsSelectable(true); prose.setMovementMethod(LinkMovementMethod.getInstance());
        addView(prose,new LayoutParams(-1,-2)); continue;
      }
      LinearLayout box=new LinearLayout(getContext()); box.setOrientation(VERTICAL);
      GradientDrawable background=new GradientDrawable(); background.setColor(panel); background.setCornerRadius(dp(12));
      box.setBackground(background); box.setPadding(dp(12),dp(4),dp(12),dp(12));
      LayoutParams bp=new LayoutParams(-1,-2); addView(box,bp);
      LinearLayout header=new LinearLayout(getContext()); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
      TextView label=text(block.language.isEmpty() ? "文本" : block.language,12,muted);
      header.addView(label,new LayoutParams(0,-2,1));
      TextView button=text("复制",13,fg); button.setGravity(android.view.Gravity.CENTER); button.setMinHeight(dp(48));
      button.setPadding(dp(12),0,dp(12),0); button.setContentDescription("复制内容块 " + (++index));
      button.setEnabled(!block.text.isEmpty()); PressFeedback.apply(button,(fg & 0xffffff)>0x808080);
      button.setOnClickListener(v -> copy.accept(block.text)); header.addView(button); box.addView(header);
      TextView code=text(block.text,14,fg); code.setTypeface(Typeface.MONOSPACE); code.setTextIsSelectable(true);
      code.setHorizontallyScrolling(true); code.setContentDescription("可复制内容块 " + index);
      HorizontalScrollView horizontal=new HorizontalScrollView(getContext()); horizontal.setFillViewport(true);
      horizontal.addView(code,new HorizontalScrollView.LayoutParams(-2,-2)); box.addView(horizontal,new LayoutParams(-1,-2));
    }
    boolean previous=false;
    for(int i=0;i<getChildCount();i++) {
      android.view.View child=getChildAt(i);LayoutParams params=(LayoutParams)child.getLayoutParams();
      params.topMargin=child.getVisibility()!=GONE && previous?dp(8):0;
      if(child.getVisibility()!=GONE)previous=true;
    }
  }
}
