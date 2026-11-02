package app.remotessh;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Two-level native activity disclosure and per-turn file change card. */
final class ToolActivityGroupView extends LinearLayout {
  private final DialogUi ui;
  private final Set<String> expanded;
  private final Consumer<ChatItem> openDetail;
  private ChatItem item;
  private final TextView header;
  private final LinearLayout content;
  private final boolean summary;
  private final RollingChangeNumbers numbers;
  private final Rect visibleBounds=new Rect();
  private final List<TextView> rollingRows=new ArrayList<>();

  ToolActivityGroupView(Activity activity,DialogUi ui,ChatItem item,Set<String> expanded,
      Consumer<ChatItem> openDetail){
    super(activity);this.ui=ui;this.item=item;this.expanded=expanded;this.openDetail=openDetail;
    summary=item.type.equals("fileSummary");
    numbers=new RollingChangeNumbers(ui.bg==Color.BLACK,this::invalidateNumbers);
    setOrientation(VERTICAL);
    if(summary){setBackground(ui.rounded(ui.panel,18));setPadding(ui.dp(12),ui.dp(3),ui.dp(12),ui.dp(3));}
    header=ui.compactSummary();
    if(summary){header.setTextSize(14);header.setTextColor(ui.fg);header.setBackgroundColor(Color.TRANSPARENT);
      header.setPadding(ui.dp(8),ui.dp(2),ui.dp(8),ui.dp(2));}
    addView(header,new LayoutParams(-1,-2));
    ui.clickable(header,()->{if(!expanded.add(item.id))expanded.remove(item.id);bind(this.item);});
    content=ui.column();addView(content,new LayoutParams(-1,-2));
    bind(item);
  }

  private void invalidateNumbers(){
    invalidateNumbers(header);
    for(TextView row:rollingRows)invalidateNumbers(row);
  }

  private void invalidateNumbers(TextView view){
    CharSequence text=view.getText(),settled=numbers.settledText(text);
    // Only live frames may skip clipped rows. Replacing completed spans also
    // discards their cached animation frame without rebinding expanded details.
    if(settled!=text)view.setText(settled);
    else if(view.getGlobalVisibleRect(visibleBounds))view.invalidate();
  }

  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();numbers.attach();}
  @Override protected void onDetachedFromWindow(){
    numbers.detach();
    super.onDetachedFromWindow();
  }

  void bind(ChatItem item){
    this.item=item;
    boolean running=!summary && item.status.equals("inProgress");
    if(!summary && !running)numbers.settle();
    boolean open=expanded.contains(item.id);
    for(ChatItem child:item.children)if(child.type.equals("fileChange") && hasFileStats(child))
      numbers.style("file:"+child.id,fileLabel(child),running && open,false);
    String label=(summary?item.text+"  "+item.detail:item.text)+(open?"  ⌄":"  ›");
    String groupIcon="book";
    for(ChatItem child:item.children)if(child.type.equals("fileChange")){groupIcon="pencil";break;}
    else if(child.type.equals("commandExecution") || child.type.equals("mcpToolCall"))groupIcon="terminal";
    CharSequence title=AppIcons.compactLabel(getContext(),summary?label:"◦  "+label,summary?null:groupIcon,
        open?"chevronDown":"chevron",summary?ui.fg:ui.muted,ui.dp(15));
    header.setText(numbers.style("header",title,running,summary));
    header.setContentDescription(label);
    rollingRows.clear();
    content.removeAllViews();
    content.setVisibility(open?View.VISIBLE:View.GONE);
    if(!open)return;
    boolean all=expanded.contains(item.id+"-all");
    int count=summary && !all?Math.min(3,item.children.size()):item.children.size();
    for(int i=0;i<count;i++)addChild(item.children.get(i));
    if(summary && item.children.size()>3){
      TextView more=ui.compactSummary();String moreLabel=all?UiText.get(R.string.ui_collapse_files)+"  ⌃":
          UiText.get(R.string.ui_more_files_count,item.children.size()-count)+"  ›";
      more.setText(AppIcons.compactLabel(getContext(),moreLabel,null,all?"chevronDown":"chevron",ui.fg,ui.dp(15),all));
      more.setTextColor(ui.fg);more.setBackgroundColor(Color.TRANSPARENT);
      more.setPadding(ui.dp(8),ui.dp(2),ui.dp(8),ui.dp(2));
      ui.clickable(more,()->{if(!expanded.add(item.id+"-all"))expanded.remove(item.id+"-all");bind(this.item);});
      content.addView(more,new LayoutParams(-1,-2));
    }
  }

  private void addChild(ChatItem child){
    LinearLayout disclosure=ui.column();
    LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);
    String icon=child.type.equals("fileChange")?"pencil":child.type.equals("commandExecution")?"terminal":
        child.type.equals("webSearch")?"search":child.type.equals("reasoning")?"book":
            child.type.equals("imageView")?"book":"computer";
    int iconHeight=ui.dp(summary?28:32);
    row.addView(new IconView(getContext(),icon,ui.muted),new LayoutParams(ui.dp(30),iconHeight));
    TextView text=ui.text("",13,ui.fg);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    String label=child.type.equals("fileChange")?fileLabel(child):child.type.equals("reasoning")?
        ToolActivityPresenter.reasoningPreview(child):UiPresentation.itemTitle(child);
    boolean file=child.type.equals("fileChange");
    text.setText(file && hasFileStats(child)?numbers.style("file:"+child.id,label,!summary && item.status.equals("inProgress"),false):label);
    if(file)rollingRows.add(text);
    row.addView(text,new LayoutParams(0,-2,1));
    row.addView(new IconView(getContext(),expanded.contains(child.id)?"chevronDown":"chevron",ui.muted),
        new LayoutParams(ui.dp(22),iconHeight));
    row.setContentDescription(label);
    ui.clickable(row,()->{if(!expanded.add(child.id))expanded.remove(child.id);bind(this.item);});
    int rowHeight=summary?Math.max(ui.dp(30),(int)Math.ceil(text.getPaint().getFontMetrics().bottom-
        text.getPaint().getFontMetrics().top)+ui.dp(6)):ui.dp(34);
    disclosure.addView(row,new LayoutParams(-1,rowHeight));
    if(expanded.contains(child.id)){
      ToolInlineDetailView detail=new ToolInlineDetailView(getContext(),ui,child);
      LayoutParams codeParams=new LayoutParams(-1,-2);codeParams.setMargins(ui.dp(2),ui.dp(2),ui.dp(2),0);
      disclosure.addView(detail,codeParams);
      TextView full=ui.text(UiText.get(child.type.equals("reasoning")?
          R.string.ui_reasoning:R.string.ui_execution_details)+"  ↗",12,ui.muted);
      full.setText(AppIcons.label(getContext(),full.getText(),null,"arrowUpRight",ui.muted,ui.dp(14)));
      full.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
      full.setPadding(0,ui.dp(3),ui.dp(7),ui.dp(3));
      ui.clickable(full,()->openDetail.accept(child));
      LayoutParams fullParams=new LayoutParams(-1,-2);fullParams.setMargins(ui.dp(2),0,ui.dp(2),ui.dp(3));
      disclosure.addView(full,fullParams);
    }
    content.addView(disclosure,new LayoutParams(-1,-2));
  }

  static String fileLabel(ChatItem child){
    DiffSummary diff=DiffSummary.fromItem(child);
    if(diff.files.isEmpty())return child.text;
    DiffSummary.File file=diff.files.get(0);
    String path=child.text.equals(file.path)?child.text:file.path;
    if(file.diff.isEmpty())return path;
    return path+(diff.files.size()>1?" · "+diff.files.size():"")+"  +"+diff.added()+" −"+diff.removed();
  }

  static boolean hasFileStats(ChatItem child){
    DiffSummary diff=DiffSummary.fromItem(child);
    return !diff.files.isEmpty() && !diff.files.get(0).diff.isEmpty();
  }

  boolean countsAnimating(){return numbers.animating();}
  boolean countAnimating(String scope,int index,String sign){return numbers.animating(scope,index,sign);}
  boolean countTransitioned(String scope,int index,String sign,String from,String to){
    return numbers.transitioned(scope,index,sign,from,to);
  }

  static CharSequence tintStats(CharSequence label,boolean dark){
    SpannableString s=new SpannableString(label);
    java.util.regex.Matcher pair=java.util.regex.Pattern.compile("(\\+\\d+)\\s+([−-]\\d+)(?=\\s*(?:›)?\\s*$)").matcher(label);
    while(pair.find()){
      s.setSpan(new ForegroundColorSpan(dark?0xff64df83:0xff168c45),pair.start(1),pair.end(1),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      s.setSpan(new ForegroundColorSpan(dark?0xffff7777:0xffba3434),pair.start(2),pair.end(2),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return s;
  }
}
