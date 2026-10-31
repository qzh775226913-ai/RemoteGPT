package app.remotessh;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compact in-message details; full raw content remains in ToolDetailDialog. */
final class ToolInlineDetailView extends LinearLayout {
  ToolInlineDetailView(Context context,DialogUi ui,ChatItem item){
    super(context);
    setOrientation(VERTICAL);
    setBackground(ui.rounded(ui.bg==Color.BLACK?0xff191919:0xffffffff,12));
    setPadding(ui.dp(11),ui.dp(8),ui.dp(11),ui.dp(9));
    if(item.type.equals("reasoning")){
      TextView prose=ui.text(item.detail.isEmpty()?UiText.get(R.string.ui_waiting_for_reasoning_summary):
          item.detail,14,ui.fg);
      prose.setLineSpacing(ui.dp(3),1);
      prose.setTextIsSelectable(true);
      addView(prose,new LayoutParams(-1,-2));
      return;
    }
    boolean diff=item.type.equals("fileChange");
    String raw=diff?diffText(item):pretty(item.detail);
    LinearLayout heading=ui.row();heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=ui.text(diff?fileName(item):UiPresentation.itemTitle(item),12,ui.muted);
    name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    heading.addView(name,new LayoutParams(0,-2,1));
    if(diff){
      DiffSummary changes=DiffSummary.fromItem(item);
      TextView numbers=ui.text("+"+changes.added()+"  −"+changes.removed(),12,ui.fg);
      numbers.setText(ToolActivityGroupView.tintStats(numbers.getText(),ui.bg==Color.BLACK));
      numbers.setPadding(ui.dp(8),0,0,0);
      heading.addView(numbers,new LayoutParams(-2,-2));
    }
    addView(heading,new LayoutParams(-1,ui.dp(26)));
    if(raw.isEmpty())return;
    TextView source=ui.text("",13,ui.fg);
    source.setTypeface(Typeface.MONOSPACE);
    source.setTextIsSelectable(true);
    source.setHorizontallyScrolling(true);
    source.setMaxLines(16);
    source.setLineSpacing(ui.dp(2),1);
    source.setText(ToolDetailDialog.highlight(raw,diff,ui.fg,ui.muted));
    HorizontalScrollView code=new HorizontalScrollView(context);
    code.setHorizontalScrollBarEnabled(false);
    code.addView(source,new HorizontalScrollView.LayoutParams(-2,-2));
    addView(code,new LayoutParams(-1,-2));
  }

  private static String fileName(ChatItem item){
    DiffSummary changes=DiffSummary.fromItem(item);
    return changes.files.isEmpty()?item.text:changes.files.get(0).path;
  }

  static String diffText(ChatItem item){
    DiffSummary changes=DiffSummary.fromItem(item);
    if(changes.files.isEmpty())return item.detail;
    StringBuilder source=new StringBuilder();
    for(DiffSummary.File file:changes.files)if(!file.diff.isEmpty()){
      if(source.length()>0)source.append('\n');
      source.append(file.diff);
    }
    return source.toString();
  }

  private static String pretty(String value){
    try{
      String trimmed=value.trim();
      if(trimmed.startsWith("{"))return new JSONObject(trimmed).toString(2);
      if(trimmed.startsWith("["))return new JSONArray(trimmed).toString(2);
    }catch(Exception ignored){}
    return value;
  }
}
