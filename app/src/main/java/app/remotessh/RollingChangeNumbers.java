package app.remotessh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.animation.PathInterpolator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Animate real changes once, retaining the latest value across rapid updates. */
final class RollingChangeNumbers {
  private static final Pattern COUNT=Pattern.compile("(?<!\\S)[+−]\\d+");
  private static final Pattern FILE_COUNT=Pattern.compile("(?<!\\S)[+−]\\d+(?=\\s*(?:[+−]\\d+)?$)");
  private static final int MAX_PENDING=2;
  private static final long NORMAL_DURATION=320, BUSY_DURATION=200;
  private final Map<String,Counter> counters=new HashMap<>();
  private final Runnable redraw;
  private final boolean dark;
  private boolean attached;

  RollingChangeNumbers(boolean dark,Runnable redraw){
    this.dark=dark;this.redraw=redraw;
  }

  CharSequence style(String scope,CharSequence label,boolean animateChanges,boolean animateFirst){
    SpannableString result=new SpannableString(label);
    Matcher matcher=(scope.startsWith("file:")?FILE_COUNT:COUNT).matcher(label);int index=0;
    while(matcher.find()){
      String value=matcher.group(),sign=value.substring(0,1);
      String key=scope+"/"+(index++)+sign;
      Counter counter=counters.computeIfAbsent(key,k->new Counter());
      counter.observe(value.substring(1),animateChanges,animateFirst);
      int color=sign.equals("+")?(dark?0xff64df83:0xff168c45):(dark?0xffff7777:0xffba3434);
      Object span=counter.moving()?new CountSpan(sign,counter,color):new ForegroundColorSpan(color);
      result.setSpan(span,matcher.start(),matcher.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return result;
  }

  /** Replace finished number spans so TextView discards any cached animation frame. */
  CharSequence settledText(CharSequence text){
    if(!(text instanceof Spanned))return text;
    Spanned source=(Spanned)text;SpannableString result=null;
    for(CountSpan span:source.getSpans(0,text.length(),CountSpan.class)){
      if(span.counter.moving())continue;
      if(result==null)result=new SpannableString(text);
      result.removeSpan(span);
      result.setSpan(new ForegroundColorSpan(span.color),source.getSpanStart(span),
          source.getSpanEnd(span),source.getSpanFlags(span));
    }
    return result==null?text:result;
  }

  void attach(){attached=true;for(Counter counter:counters.values())counter.startNext();}
  void detach(){attached=false;settle();redraw.run();}
  void settle(){for(Counter counter:counters.values())counter.finish();}
  boolean animating(){for(Counter counter:counters.values())if(counter.moving())return true;return false;}
  boolean animating(String scope,int index,String sign){
    Counter counter=counters.get(scope+"/"+index+sign);return counter!=null && counter.moving();
  }
  boolean transitioned(String scope,int index,String sign,String from,String to){
    Counter counter=counters.get(scope+"/"+index+sign);
    return counter!=null && from.equals(counter.lastFrom) && to.equals(counter.lastTo);
  }

  private final class Counter {
    String current,latest,from,to,lastFrom,lastTo;
    float progress;
    ValueAnimator animator;
    final ArrayDeque<String> pending=new ArrayDeque<>();

    void observe(String value,boolean animate,boolean initial){
      if(latest==null){
        latest=value;current=value;
        if((initial || animate && attached) && !value.equals("0")){current="0";pending.add(value);}
      } else if(!value.equals(latest)){
        latest=value;
        if(animate && attached){
          if(pending.size()>=MAX_PENDING)pending.removeLast();
          pending.add(value);
          accelerate();
        } else finish();
      } else if(!animate && !initial && moving())finish();
      startNext();
    }

    boolean moving(){return animator!=null || !pending.isEmpty();}
    void accelerate(){
      if(animator==null || animator.getDuration()<=BUSY_DURATION)return;
      float elapsed=Math.min(1f,animator.getCurrentPlayTime()/(float)animator.getDuration());
      animator.setDuration(BUSY_DURATION);animator.setCurrentPlayTime(Math.round(elapsed*BUSY_DURATION));
    }
    void finish(){
      if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();}
      pending.clear();current=latest;from=to=null;progress=0;
    }
    void startNext(){
      if(!attached || animator!=null || pending.isEmpty())return;
      from=current;to=pending.removeFirst();
      if(from.equals(to)){current=to;startNext();return;}
      lastFrom=from;lastTo=to;
      progress=0;animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(pending.isEmpty()?NORMAL_DURATION:BUSY_DURATION);
      animator.setInterpolator(new PathInterpolator(.42f,0f,.25f,1f));
      animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();redraw.run();});
      animator.addListener(new AnimatorListenerAdapter(){
        @Override public void onAnimationEnd(Animator animation){
          if(animator!=animation)return;
          current=to;animator=null;from=to=null;progress=0;startNext();redraw.run();
        }
      });
      animator.start();
    }
  }

  private static final class CountSpan extends ReplacementSpan {
    private final String sign;
    private final Counter counter;
    private final int color;
    private float reserved;
    CountSpan(String sign,Counter counter,int color){this.sign=sign;this.counter=counter;this.color=color;}
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
      String from=counter.from==null?counter.current:counter.from;
      String to=counter.to==null?from:counter.to;
      reserved=Math.max(Math.max(paint.measureText(from),paint.measureText(to)),paint.measureText(counter.latest));
      return (int)Math.ceil(paint.measureText(sign)+reserved);
    }
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
      String from=counter.from==null?counter.current:counter.from;
      String to=counter.to==null?from:counter.to;
      float fromWidth=paint.measureText(from),toWidth=paint.measureText(to),slot=Math.max(reserved,Math.max(fromWidth,toWidth));
      int old=paint.getColor();paint.setColor(color);canvas.drawText(sign,x,baseline,paint);
      float startX=x+paint.measureText(sign),height=bottom-top;
      float direction=compare(to,from)>=0?1f:-1f;
      canvas.save();canvas.clipRect(startX,top,startX+slot,bottom);
      float p=counter.animator==null?0:counter.progress;int alpha=paint.getAlpha();
      drawValue(canvas,paint,from,startX+slot/2,baseline-direction*height*p,fromWidth,1f-.1f*p,alpha*(1f-.3f*p));
      if(counter.animator!=null)drawValue(canvas,paint,to,startX+slot/2,
          baseline+direction*height*(1-p),toWidth,.9f+.1f*p,alpha*(.7f+.3f*p));
      canvas.restore();paint.setColor(old);
    }
    private static void drawValue(Canvas canvas,Paint paint,String text,float center,float baseline,float width,float scale,float alpha){
      int oldAlpha=paint.getAlpha();paint.setAlpha(Math.max(0,Math.min(255,Math.round(alpha))));
      canvas.save();canvas.translate(center,baseline);canvas.scale(1f,scale);canvas.drawText(text,-width/2,0,paint);canvas.restore();
      paint.setAlpha(oldAlpha);
    }
    private static int compare(String a,String b){
      if(a.length()!=b.length())return Integer.compare(a.length(),b.length());
      return a.compareTo(b);
    }
  }
}
