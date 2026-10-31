package app.remotessh;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selectable, color-coded tool and unified-diff detail with live updates. */
final class ToolDetailDialog {
  private static final Pattern TOKEN=Pattern.compile("\\b(?:public|private|protected|class|interface|static|final|void|return|if|else|for|while|new|import|package|def|from|async|await|const|let|function|var|true|false|null|None)\\b");
  private static final Pattern STRING=Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
  private ToolDetailDialog() {}

  static void show(Activity activity, DialogUi ui, boolean wide, ChatItem item, Supplier<ChatItem> latest,
      Consumer<String> copy) {
    LinearLayout layout=ui.column();layout.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(20));
    TextView heading=ui.text(UiPresentation.itemTitle(item),14,ui.fg);
    heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    heading.setPadding(ui.dp(2),0,ui.dp(2),ui.dp(10));
    layout.addView(heading);
    TextView detail=ui.text("",13,ui.fg);detail.setTypeface(Typeface.MONOSPACE);
    detail.setTextIsSelectable(true);detail.setHorizontallyScrolling(true);
    detail.setLineSpacing(ui.dp(3),1);
    detail.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));
    HorizontalScrollView horizontal=new HorizontalScrollView(activity);horizontal.addView(detail);
    horizontal.setBackground(ui.rounded(ui.panel,14));
    ScrollView scroll=new ScrollView(activity);scroll.addView(horizontal);
    layout.addView(scroll,new LinearLayout.LayoutParams(-1,ui.dp(380)));
    layout.addView(ui.action(UiText.get(R.string.ui_copy),()->{
      ChatItem source=latest.get();if(source==null)source=item;
      copy.accept(source.type.equals("reasoning")?raw(source):source.text+
          (source.detail.isEmpty()?"":"\n\n"+source.detail));
    }));
    Dialog dialog=ui.sheet(item.type.equals("reasoning")?UiText.get(R.string.ui_reasoning):
        UiText.get(R.string.ui_execution_details),layout,wide);
    Runnable update=new Runnable(){public void run(){
      if(!dialog.isShowing())return;
      ChatItem source=latest.get();if(source==null)source=item;
      String value=raw(source);
      if(!value.equals(detail.getTag())){detail.setTag(value);detail.setText(highlight(value,source.type.equals("fileChange"),ui.fg,ui.muted));}
      detail.postDelayed(this,150);
    }};
    dialog.setOnDismissListener(d->detail.removeCallbacks(update));
    update.run();
  }

  private static String raw(ChatItem item) {
    if(item.type.equals("reasoning"))return item.detail.trim().isEmpty()?
        UiText.get(item.status.equals("inProgress")?R.string.ui_waiting_for_reasoning_summary:
            R.string.ui_the_server_provided_no_summary_for_this_reasoning_item_some_reaso):item.detail;
    return UiPresentation.itemTitle(item)+(item.detail.isEmpty()?"":"\n\n"+item.detail);
  }

  static CharSequence highlight(String value,boolean diff,int foreground,int muted) {
    SpannableString result=new SpannableString(value);
    int offset=0;
    for(String line:value.split("\n",-1)){
      int color=foreground;
      if(diff){
        if(line.startsWith("+")&&!line.startsWith("+++"))color=0xff69d68b;
        else if(line.startsWith("-")&&!line.startsWith("---"))color=0xfff07878;
        else if(line.startsWith("@@")||line.startsWith("diff --git")||line.startsWith("index "))color=muted;
      }
      if(!line.isEmpty())result.setSpan(new ForegroundColorSpan(color),offset,offset+line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if(diff && !line.isEmpty() && (line.startsWith("+") || line.startsWith("-")) &&
          !line.startsWith("+++") && !line.startsWith("---"))
        result.setSpan(new BackgroundColorSpan(line.startsWith("+")?0x2236a75a:0x22ce5555),
            offset,offset+line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if(diff && color!=muted && !line.isEmpty()) {
        Matcher token=TOKEN.matcher(line);
        while(token.find())result.setSpan(new ForegroundColorSpan(0xff94b8f6),offset+token.start(),offset+token.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Matcher string=STRING.matcher(line);
        while(string.find())result.setSpan(new ForegroundColorSpan(0xffe3ae73),offset+string.start(),offset+string.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      offset+=line.length()+1;
    }
    return result;
  }
}
