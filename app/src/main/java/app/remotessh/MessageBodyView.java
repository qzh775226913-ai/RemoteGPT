package app.remotessh;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.LinkMovementMethod;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** Native text and copyable fenced blocks; remote content is never executed. */
final class MessageBodyView extends LinearLayout {
  static final class Block {
    final boolean code; final String language, text;
    Block(boolean code, String language, String text) { this.code=code; this.language=language; this.text=text; }
  }
  private final int fg, muted, panel;
  private final Consumer<String> open, copy;
  private String source;

  MessageBodyView(Context context, int fg, int muted, int panel, Consumer<String> open, Consumer<String> copy) {
    super(context); setOrientation(VERTICAL);
    this.fg=fg; this.muted=muted; this.panel=panel; this.open=open; this.copy=copy;
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private TextView text(String value, int size, int color) {
    TextView view=new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(color);
    return view;
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

  void setSource(String value) {
    if (value.equals(source)) return;
    source=value; List<Block> parts=blocks(value);
    // Ordinary streaming prose keeps its existing selectable view.
    if(parts.size()==1 && !parts.get(0).code && getChildCount()==1 && getChildAt(0) instanceof TextView) {
      ((TextView)getChildAt(0)).setText(Markdown.render(parts.get(0).text,muted,panel,open,copy));return;
    }
    removeAllViews(); int index=0;
    for (Block block : parts) {
      if (!block.code) {
        TextView prose=text("", 16, fg); prose.setLineSpacing(dp(5),1);
        prose.setText(Markdown.render(block.text, muted, panel, open, copy));
        prose.setTextIsSelectable(true); prose.setMovementMethod(LinkMovementMethod.getInstance());
        addView(prose,new LayoutParams(-1,-2)); continue;
      }
      LinearLayout box=new LinearLayout(getContext()); box.setOrientation(VERTICAL);
      GradientDrawable background=new GradientDrawable(); background.setColor(panel); background.setCornerRadius(dp(12));
      box.setBackground(background); box.setPadding(dp(12),dp(4),dp(12),dp(12));
      LayoutParams bp=new LayoutParams(-1,-2); bp.topMargin=dp(8); bp.bottomMargin=dp(12); addView(box,bp);
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
  }
}
