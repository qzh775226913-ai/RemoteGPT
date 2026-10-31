package app.remotessh;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
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
  private ValueAnimator countRoll;
  private float rollProgress;
  private boolean summaryRollDone;
  private final Rect visibleBounds=new Rect();

  ToolActivityGroupView(Activity activity,DialogUi ui,ChatItem item,Set<String> expanded,
      Consumer<ChatItem> openDetail){
    super(activity);this.ui=ui;this.item=item;this.expanded=expanded;this.openDetail=openDetail;
    summary=item.type.equals("fileSummary");
    setOrientation(VERTICAL);
    if(summary){setBackground(ui.rounded(ui.panel,18));setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));}
    header=ui.compactSummary();
    if(summary){header.setTextSize(14);header.setTextColor(ui.fg);header.setBackgroundColor(Color.TRANSPARENT);}
    addView(header,new LayoutParams(-1,-2));
    ui.clickable(header,()->{if(!expanded.add(item.id))expanded.remove(item.id);bind(this.item);});
    content=ui.column();addView(content,new LayoutParams(-1,-2));
    bind(item);
  }

  private void startRoll(){
    if(countRoll!=null)return;
    countRoll=ValueAnimator.ofFloat(0f,1f);
    countRoll.setDuration(summary?1000:2200);
    countRoll.setRepeatCount(summary?0:ValueAnimator.INFINITE);
    countRoll.setInterpolator(new android.view.animation.LinearInterpolator());
    countRoll.addUpdateListener(a->{
      rollProgress=(float)a.getAnimatedValue();
      if(header.getGlobalVisibleRect(visibleBounds))header.invalidate();
    });
    if(summary)countRoll.addListener(new android.animation.AnimatorListenerAdapter(){
      private boolean cancelled;
      @Override public void onAnimationCancel(android.animation.Animator animation){cancelled=true;}
      @Override public void onAnimationEnd(android.animation.Animator animation){
        if(!cancelled){summaryRollDone=true;countRoll=null;rollProgress=0;bind(item);}
      }
    });
    countRoll.start();
  }

  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(rolling())startRoll();}
  @Override protected void onDetachedFromWindow(){
    if(countRoll!=null){countRoll.cancel();countRoll=null;}
    super.onDetachedFromWindow();
  }

  void bind(ChatItem item){
    this.item=item;
    if(rolling() && getWindowToken()!=null)startRoll();
    if(!rolling() && countRoll!=null){countRoll.cancel();countRoll=null;rollProgress=0;}
    boolean open=expanded.contains(item.id);
    String label=(summary?item.text+"  "+item.detail:item.text)+(open?"  ⌄":"  ›");
    CharSequence title=summary?label:AppIcons.label(getContext(),"✎  "+label,"pencil",null,ui.muted,ui.dp(16));
    header.setText(rolling()?rollingStats(title):tintStats(title,ui.bg==Color.BLACK));
    header.setContentDescription(label);
    content.removeAllViews();
    content.setVisibility(open?View.VISIBLE:View.GONE);
    if(!open)return;
    boolean all=expanded.contains(item.id+"-all");
    int count=summary && !all?Math.min(3,item.children.size()):item.children.size();
    for(int i=0;i<count;i++)addChild(item.children.get(i));
    if(summary && item.children.size()>3){
      TextView more=ui.compactSummary();more.setText(all?UiText.get(R.string.ui_collapse_files)+"  ⌃":
          UiText.get(R.string.ui_more_files_count,item.children.size()-count)+"  ›");
      more.setTextColor(ui.fg);more.setBackgroundColor(Color.TRANSPARENT);
      ui.clickable(more,()->{if(!expanded.add(item.id+"-all"))expanded.remove(item.id+"-all");bind(this.item);});
      content.addView(more,new LayoutParams(-1,-2));
    }
  }

  private void addChild(ChatItem child){
    LinearLayout disclosure=ui.column();
    LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);
    String icon=child.type.equals("fileChange")?"pencil":child.type.equals("commandExecution")?"terminal":
        child.type.equals("webSearch")?"search":child.type.equals("imageView")?"book":"computer";
    row.addView(new IconView(getContext(),icon,ui.muted),new LayoutParams(ui.dp(30),ui.dp(32)));
    TextView text=ui.text("",13,ui.fg);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    String label=child.type.equals("fileChange")?fileLabel(child):UiPresentation.itemTitle(child);
    text.setText(colors(label));
    row.addView(text,new LayoutParams(0,-2,1));
    row.addView(new IconView(getContext(),expanded.contains(child.id)?"chevronDown":"chevron",ui.muted),
        new LayoutParams(ui.dp(22),ui.dp(32)));
    row.setContentDescription(label);
    ui.clickable(row,()->{if(!expanded.add(child.id))expanded.remove(child.id);bind(this.item);});
    disclosure.addView(row,new LayoutParams(-1,ui.dp(34)));
    if(expanded.contains(child.id)){
      String code=child.type.equals("fileChange")?diffText(child):child.detail;
      if(!code.isEmpty()){
        TextView source=ui.text("",13,ui.fg);
        source.setTypeface(Typeface.MONOSPACE);
        source.setTextIsSelectable(true);
        source.setHorizontallyScrolling(true);
        source.setMaxLines(16);
        source.setLineSpacing(ui.dp(3),1);
        source.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
        source.setText(ToolDetailDialog.highlight(code,child.type.equals("fileChange"),ui.fg,ui.muted));
        HorizontalScrollView codeScroll=new HorizontalScrollView(getContext());
        codeScroll.setHorizontalScrollBarEnabled(false);
        codeScroll.setBackground(ui.rounded(ui.bg,12));
        codeScroll.addView(source,new HorizontalScrollView.LayoutParams(-2,-2));
        LayoutParams codeParams=new LayoutParams(-1,-2);codeParams.setMargins(ui.dp(30),ui.dp(3),ui.dp(4),0);
        disclosure.addView(codeScroll,codeParams);
      }
      TextView full=ui.compactSummary();full.setText(UiText.get(R.string.ui_execution_details)+"  ↗");
      full.setTextColor(ui.muted);ui.clickable(full,()->openDetail.accept(child));
      LayoutParams fullParams=new LayoutParams(-1,-2);fullParams.setMargins(ui.dp(30),ui.dp(2),0,ui.dp(6));
      disclosure.addView(full,fullParams);
    }
    content.addView(disclosure,new LayoutParams(-1,-2));
  }

  private static String diffText(ChatItem child){
    DiffSummary diff=DiffSummary.fromItem(child);
    if(diff.files.isEmpty())return child.detail;
    StringBuilder source=new StringBuilder();
    for(DiffSummary.File file:diff.files)if(!file.diff.isEmpty()){
      if(source.length()>0)source.append('\n');
      source.append(file.diff);
    }
    return source.toString();
  }

  static String fileLabel(ChatItem child){
    DiffSummary diff=DiffSummary.fromItem(child);
    if(diff.files.isEmpty())return child.text;
    DiffSummary.File file=diff.files.get(0);
    String path=child.text.equals(file.path)?child.text:file.path;
    if(file.diff.isEmpty())return path;
    return path+(diff.files.size()>1?" · "+diff.files.size():"")+"  +"+diff.added()+" −"+diff.removed();
  }

  private CharSequence colors(String label){
    return tintStats(label,ui.bg==Color.BLACK);
  }

  private boolean rolling(){return summary?!summaryRollDone:
      item.status.equals("inProgress") && item.text.matches("(?s).*[+−]\\d+.*");}

  private CharSequence rollingStats(CharSequence label){
    SpannableString styled=new SpannableString(label);
    java.util.regex.Matcher counts=java.util.regex.Pattern.compile("[+−]\\d+").matcher(label);
    while(counts.find())styled.setSpan(new RollingCountSpan(counts.group(),
        counts.group().startsWith("+")?(ui.bg==Color.BLACK?0xff64df83:0xff168c45):
            (ui.bg==Color.BLACK?0xffff7777:0xffba3434)),counts.start(),counts.end(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return styled;
  }

  private final class RollingCountSpan extends ReplacementSpan {
    private final String sign,digits;
    private final int color;
    RollingCountSpan(String value,int color){sign=value.substring(0,1);digits=value.substring(1);this.color=color;}
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
      return (int)Math.ceil(paint.measureText(sign+digits));
    }
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
      int old=paint.getColor();paint.setColor(color);
      canvas.drawText(sign,x,baseline,paint);
      float signWidth=paint.measureText(sign),height=bottom-top;
      canvas.save();canvas.clipRect(x+signWidth,top,x+signWidth+paint.measureText(digits),bottom);
      canvas.drawText(digits,x+signWidth,baseline-height*rollProgress,paint);
      canvas.drawText(digits,x+signWidth,baseline+height*(1-rollProgress),paint);
      canvas.restore();paint.setColor(old);
    }
  }

  static CharSequence tintStats(CharSequence label,boolean dark){
    SpannableString s=new SpannableString(label);
    java.util.regex.Matcher plus=java.util.regex.Pattern.compile("\\+\\d+").matcher(label);
    java.util.regex.Matcher minus=java.util.regex.Pattern.compile("[−-]\\d+").matcher(label);
    while(plus.find())s.setSpan(new ForegroundColorSpan(dark?0xff64df83:0xff168c45),
        plus.start(),plus.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    while(minus.find())s.setSpan(new ForegroundColorSpan(dark?0xffff7777:0xffba3434),
        minus.start(),minus.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return s;
  }
}
