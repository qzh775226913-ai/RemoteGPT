package app.remotessh;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import ru.noties.jlatexmath.JLatexMathAndroid;

/** Owns one prose block's math layout and releases asynchronous callbacks with its view lifecycle. */
// Constructed by MessageBodyView with explicit theme colors and callbacks, never inflated from XML.
@SuppressLint("ViewConstructor")
final class MathContentView extends LinearLayout {
  private final int fg,muted,panel;
  private final int textSize,lineSpacing;
  private final Consumer<String> open,copy;
  private String source;
  private List<MathMarkup.Part> parts=Collections.emptyList();
  private List<MathRenderer.Formula> formulas;
  private Future<?> pending;
  private long generation;
  private boolean detached,complete;
  private int layoutWidth;

  MathContentView(Context context,int fg,int muted,int panel,Consumer<String> open,Consumer<String> copy) {
    this(context,fg,muted,panel,open,copy,16,5);
  }
  MathContentView(Context context,int fg,int muted,int panel,Consumer<String> open,Consumer<String> copy,
      int textSize,int lineSpacing) {
    super(context);setOrientation(VERTICAL);
    this.fg=fg;this.muted=muted;this.panel=panel;this.open=open;this.copy=copy;
    this.textSize=textSize;this.lineSpacing=lineSpacing;
    JLatexMathAndroid.init(context);MathGlyphs.init(context.getApplicationContext().getAssets());
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  void setSource(String value) {
    if(value.equals(source))return;
    MathRenderer.cancel(pending);pending=null;generation++;
    List<MathMarkup.Part> previous=parts;List<MathRenderer.Formula> previousFormulas=formulas;
    boolean wasComplete=complete,allKnown=true;
    source=value;parts=MathMarkup.parts(value);
    formulas=new ArrayList<>(Collections.nCopies(parts.size(),null));
    for(int i=0;i<parts.size();i++) {
      MathMarkup.Part part=parts.get(i);if(part.kind==MathMarkup.Kind.TEXT)continue;
      boolean known=false;
      if(previousFormulas!=null)for(int j=0;j<previous.size();j++) {
        MathMarkup.Part old=previous.get(j);
        if(old.kind==part.kind && old.tex.equals(part.tex) && (wasComplete || previousFormulas.get(j)!=null)) {
          formulas.set(i,previousFormulas.get(j));known=true;break;
        }
      }
      allKnown &= known;
    }
    complete=allKnown;show();if(!complete)schedule();
  }
  private void schedule() {
    MathRenderer.cancel(pending);long ticket=++generation;
    if(detached)return;
    float formulaSize=textSize*1.2f*getResources().getDisplayMetrics().scaledDensity;
    pending=MathRenderer.submit(parts,formulaSize,fg,result->{
      if(ticket!=generation || detached)return;
      formulas=result;complete=true;pending=null;show();
    });
  }
  @Override protected void onDetachedFromWindow() {
    detached=true;generation++;MathRenderer.cancel(pending);pending=null;super.onDetachedFromWindow();
  }
  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if(detached){detached=false;if(source!=null && !complete)schedule();}
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec) {
    int width=MeasureSpec.getSize(widthSpec);
    if(width>0 && width!=layoutWidth) {
      layoutWidth=width;
      if(formulas!=null)show();
    }
    super.onMeasure(widthSpec,heightSpec);
  }
  private void show() {
    Map<Object,Integer> offsets=new HashMap<>();
    for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof HorizontalScrollView) {
      View old=getChildAt(i);offsets.put(old.getTag(),old.getScrollX());
    }
    removeAllViews();StringBuilder prose=new StringBuilder();List<InlineToken> inline=new ArrayList<>();
    String prefix="\uE000math";
    while(source.contains(prefix))prefix+="\uE000";
    int available=layoutWidth>0?layoutWidth:Math.max(dp(120),getResources().getDisplayMetrics().widthPixels-dp(64));
    for(int i=0;i<parts.size();i++) {
      MathMarkup.Part part=parts.get(i);
      MathRenderer.Formula formula=formulas==null?null:formulas.get(i);
      boolean display=part.kind==MathMarkup.Kind.DISPLAY || (part.kind==MathMarkup.Kind.INLINE && part.tex.indexOf('\n')>=0) || (formula!=null && formula.width>available);
      if(display) {
        addProse(prose,inline);prose.setLength(0);inline.clear();
        if(formula==null)addText(new SpannableStringBuilder(part.raw));
        else {
          String key=i+"|"+part.raw;
          HorizontalScrollView scroll=new FormulaScroll(offsets.getOrDefault(key,0));scroll.setTag(key);scroll.setFillViewport(true);
          FormulaView view=new FormulaView(formula,part.raw,part.tex);
          scroll.addView(view,new HorizontalScrollView.LayoutParams(-2,-2));
          addView(scroll,new LayoutParams(-1,-2));
        }
      } else if(part.kind==MathMarkup.Kind.TEXT)prose.append(part.raw);
      else {
        String token=prefix+i+"\uE001";prose.append(token);inline.add(new InlineToken(token,part.raw,formula));
      }
    }
    addProse(prose,inline);
  }
  private static final class InlineToken {
    final String token,raw;final MathRenderer.Formula formula;
    InlineToken(String token,String raw,MathRenderer.Formula formula){this.token=token;this.raw=raw;this.formula=formula;}
  }
  private void addProse(StringBuilder raw,List<InlineToken> tokens) {
    SpannableStringBuilder prose=new SpannableStringBuilder(Markdown.render(MessageBodyView.proseDisplay(raw.toString()),muted,panel,open,copy));
    for(InlineToken token:tokens) {
      int start=prose.toString().indexOf(token.token);
      if(start<0)continue;
      prose.replace(start,start+token.token.length(),token.raw);
      if(token.formula!=null)prose.setSpan(new MathSpan(token.formula),start,start+token.raw.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    addText(prose);
  }
  private void addText(SpannableStringBuilder prose) {
    int start=0,end=prose.length();
    while(start<end && prose.charAt(start)=='\n')start++;
    while(end>start && prose.charAt(end-1)=='\n')end--;
    if(start==end)return;
    TextView text=new TextView(getContext());text.setTextSize(textSize);text.setTextColor(fg);
    text.setLineSpacing(dp(lineSpacing),1);text.setIncludeFontPadding(false);
    text.setText(MessageBodyView.paragraphText(prose.subSequence(start,end),dp(4)));
    text.setTextIsSelectable(true);text.setMovementMethod(LinkMovementMethod.getInstance());
    addView(text,new LayoutParams(-1,-2));
  }
  private final class FormulaScroll extends HorizontalScrollView {
    private int restoreX;
    FormulaScroll(int restoreX){super(MathContentView.this.getContext());this.restoreX=restoreX;}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
      super.onLayout(changed,left,top,right,bottom);
      if(restoreX>=0){scrollTo(restoreX,0);restoreX=-1;}
    }
  }
  private final class FormulaView extends View {
    private final MathRenderer.Formula formula;
    FormulaView(MathRenderer.Formula formula,String raw,String tex) {
      super(MathContentView.this.getContext());this.formula=formula;
      setContentDescription(UiText.get(R.string.ui_formula)+tex);
      setOnLongClickListener(v->{if(copy!=null)copy.accept(raw);return true;});
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
      setMeasuredDimension(resolveSize(formula.width+dp(16),widthSpec),resolveSize(formula.height+dp(16),heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);canvas.save();
      canvas.translate(Math.max(dp(8),(getWidth()-formula.width)/2f),dp(8));
      formula.drawable.setBounds(0,0,formula.width,formula.height);formula.drawable.draw(canvas);canvas.restore();
    }
  }
}
