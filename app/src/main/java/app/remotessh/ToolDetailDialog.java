package app.remotessh;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.Window;
import android.view.ViewGroup;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Selectable, color-coded tool and unified-diff detail with live updates. */
final class ToolDetailDialog {
  private static final Pattern TOKEN=Pattern.compile("\\b(?:public|private|protected|class|interface|static|final|void|return|if|else|for|while|new|import|package|def|from|async|await|const|let|function|var|true|false|null|None)\\b");
  private static final Pattern STRING=Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
  private ToolDetailDialog() {}

  static void show(Activity activity, DialogUi ui, boolean wide, ChatItem item, Supplier<ChatItem> latest,
      Consumer<String> copy) {
    Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout layout=ui.column();layout.setBackgroundColor(ui.bg);
    LinearLayout header=ui.row();header.setPadding(ui.dp(12),ui.dp(4),ui.dp(8),ui.dp(4));
    String fileTitle=item.type.equals("fileChange")?ToolActivityGroupView.fileLabel(item):"";
    if(fileTitle.equals("文件更改"))fileTitle=UiPresentation.itemTitle(item);
    String title=item.type.equals("reasoning")?UiText.get(R.string.ui_reasoning):
        item.type.equals("fileChange")?fileTitle:
        isCode(item)?UiText.get(R.string.ui_execution_details):UiPresentation.itemTitle(item);
    TextView heading=ui.text(title,20,ui.fg);heading.setTypeface(null,Typeface.BOLD);
    heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    header.addView(heading,new LinearLayout.LayoutParams(0,ui.dp(48),1));heading.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(ui.icon("copy",UiText.get(R.string.ui_copy),()->{
      ChatItem source=latest.get();if(source==null)source=item;copy.accept(copyText(source));
    }),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
    header.addView(ui.icon("close",UiText.get(R.string.ui_close),dialog::dismiss),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
    layout.addView(header);
    boolean code=isCode(item),reasoning=item.type.equals("reasoning");TextView detail=ui.text("",code?13:16,ui.fg);
    if(code){detail.setTypeface(Typeface.MONOSPACE);detail.setHorizontallyScrolling(true);}
    detail.setTextIsSelectable(true);
    detail.setLineSpacing(ui.dp(3),1);
    detail.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(12));
    ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);
    LinearLayout reasoningBody=ui.column();
    if(reasoning)scroll.addView(reasoningBody,new ScrollView.LayoutParams(-1,-2));
    else if(code){
      ToolCodeViewport viewport=new ToolCodeViewport(activity,detail);viewport.setBackgroundColor(ui.panel);
      layout.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
    }else scroll.addView(detail,new ScrollView.LayoutParams(-1,-2));
    if(!code)layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(layout);
    Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(ui.bg));window.setGravity(Gravity.CENTER);}
    layout.setFitsSystemWindows(true);dialog.show();
    if(window!=null)window.setLayout(wide?ui.dp(720):ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
    Runnable update=new Runnable(){public void run(){
      if(!dialog.isShowing())return;
      ChatItem source=latest.get();if(source==null)source=item;
      String value=raw(source);
      if(!value.equals(layout.getTag())){
        layout.setTag(value);
        if(reasoning){reasoningBody.removeAllViews();reasoningBody.addView(new ToolInlineDetailView(activity,ui,source));}
        else detail.setText(code?highlight(value,source.type.equals("fileChange"),ui.fg,ui.muted):value);
      }
      layout.postDelayed(this,150);
    }};
    dialog.setOnDismissListener(d->layout.removeCallbacks(update));
    update.run();
  }

  static boolean isCode(ChatItem item){
    return item.type.equals("commandExecution") || item.type.equals("fileChange") || item.type.equals("mcpToolCall");
  }

  static String raw(ChatItem item) {
    if(item.type.equals("reasoning"))return item.detail.trim().isEmpty()?
        UiText.get(item.status.equals("inProgress")?R.string.ui_waiting_for_reasoning_summary:
            R.string.ui_the_server_provided_no_summary_for_this_reasoning_item_some_reaso):item.detail;
    if(item.type.equals("fileChange"))return item.detail.isEmpty()?ToolInlineDetailView.diffText(item):item.detail;
    if(isCode(item))return item.text+(item.detail.isEmpty()?"":"\n\n"+
        (item.type.equals("mcpToolCall")?pretty(item.detail):item.detail));
    if(item.type.equals("historicalError"))return item.detail;
    String text=(item.type.equals("webSearch") || item.type.equals("imageView") || item.type.equals("imageGeneration"))
        ?UiPresentation.itemTitle(item):item.text;
    return text+(item.detail.isEmpty()?"":"\n\n"+item.detail);
  }

  private static String pretty(String text){
    try{
      String value=text.trim();if(value.startsWith("{"))return new JSONObject(value).toString(2);
      if(value.startsWith("["))return new JSONArray(value).toString(2);
    }catch(Exception ignored){}
    return text;
  }

  private static String copyText(ChatItem item){
    return item.type.equals("historicalError") || item.type.equals("reasoning")?raw(item):
        item.text+(item.detail.isEmpty()?"":"\n\n"+item.detail);
  }

  static ChatItem latest(Map<String,ChatItem> items,String id){
    ChatItem direct=items.get(id);if(direct!=null)return direct;
    for(ChatItem group:items.values())for(ChatItem child:group.children)if(id.equals(child.id))return child;
    return null;
  }

  static CharSequence highlight(String value,boolean diff,int foreground,int muted) {
    SpannableString result=new SpannableString(value);
    boolean dark=Color.red(foreground)+Color.green(foreground)+Color.blue(foreground)>384;
    int offset=0;
    for(String line:value.split("\n",-1)){
      int color=foreground;
      if(diff){
        if(line.startsWith("+")&&!line.startsWith("+++"))color=dark?0xff69d68b:0xff087b38;
        else if(line.startsWith("-")&&!line.startsWith("---"))color=dark?0xfff07878:0xffac2f33;
        else if(line.startsWith("@@")||line.startsWith("diff --git")||line.startsWith("index "))color=muted;
      }
      if(!line.isEmpty())result.setSpan(new ForegroundColorSpan(color),offset,offset+line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if(diff && !line.isEmpty() && (line.startsWith("+") || line.startsWith("-")) &&
          !line.startsWith("+++") && !line.startsWith("---"))
        result.setSpan(new BackgroundColorSpan(line.startsWith("+")?0x2236a75a:0x22ce5555),
            offset,offset+line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if((!diff || color!=muted && !line.startsWith("+") && !line.startsWith("-")) && !line.isEmpty()) {
        Matcher token=TOKEN.matcher(line);
        while(token.find())result.setSpan(new ForegroundColorSpan(dark?0xff94b8f6:0xff245a9e),offset+token.start(),offset+token.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Matcher string=STRING.matcher(line);
        while(string.find())result.setSpan(new ForegroundColorSpan(dark?0xffe3ae73:0xff8c4a16),offset+string.start(),offset+string.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      offset+=line.length()+1;
    }
    return result;
  }
}
