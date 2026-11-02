package app.remotessh;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Complete selectable detail inside an expanded activity, without a second heading. */
final class ToolInlineDetailView extends LinearLayout {
  ToolInlineDetailView(Context context,DialogUi ui,ChatItem item){
    super(context);
    setOrientation(VERTICAL);
    String raw=ToolDetailDialog.raw(item);
    if(raw.isEmpty())return;
    if(item.type.equals("reasoning")){
      for(MessageBodyView.Block block:MessageBodyView.blocks(raw)){
        if(block.code)addCode(context,ui,block.text,false);
        else addProse(ui,block.text);
      }
    }else if(ToolDetailDialog.isCode(item))addCode(context,ui,raw,item.type.equals("fileChange"));
    else addProse(ui,raw);
  }

  private void addProse(DialogUi ui,String value){
    TextView prose=ui.text(value,14,ui.fg);prose.setLineSpacing(ui.dp(3),1);
    prose.setTextIsSelectable(true);prose.setPadding(ui.dp(4),ui.dp(3),ui.dp(4),ui.dp(3));
    addView(prose,new LayoutParams(-1,-2));
  }

  private void addCode(Context context,DialogUi ui,String raw,boolean diff){
    TextView source=ui.text("",13,ui.fg);
    source.setTypeface(Typeface.MONOSPACE);
    source.setTextIsSelectable(true);
    source.setHorizontallyScrolling(true);
    source.setLineSpacing(ui.dp(2),1);
    source.setText(ToolDetailDialog.highlight(raw,diff,ui.fg,ui.muted));
    source.setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6));
    HorizontalScrollView code=new HorizontalScrollView(context);
    code.setHorizontalScrollBarEnabled(false);code.setFillViewport(true);
    code.setBackground(ui.rounded(ui.bg==Color.BLACK?0xff191919:0xfff3f4f6,10));
    code.addView(source,new HorizontalScrollView.LayoutParams(-2,-2));
    addView(code,new LayoutParams(-1,-2));
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

}
