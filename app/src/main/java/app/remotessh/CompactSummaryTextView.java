package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Optically centers a one-line folded summary across Latin, CJK and Cyrillic glyph bounds. */
final class CompactSummaryTextView extends TextView {
  private final Rect inkBounds=new Rect();
  private final int touchSlop;
  private float downX,downY;
  private int startScroll,direction;

  CompactSummaryTextView(Context context) {
    super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
    setSingleLine(true);setHorizontallyScrolling(true);setEllipsize(null);
  }

  private int scrollLimit() {
    if(getLayout()==null || getLayout().getLineCount()==0)return 0;
    return Math.max(0,(int)Math.ceil(getLayout().getLineWidth(0))+getCompoundPaddingLeft()+
        getCompoundPaddingRight()-getWidth());
  }

  @android.annotation.SuppressLint("ClickableViewAccessibility") // Normal taps delegate to TextView, which calls performClick.
  @Override public boolean onTouchEvent(MotionEvent event) {
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      downX=event.getX();downY=event.getY();startScroll=getScrollX();direction=0;
      if(scrollLimit()>0 && getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
    } else if(action==MotionEvent.ACTION_MOVE && direction==0){
      float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);
      if(Math.max(dx,dy)>touchSlop){
        direction=dx>dy && scrollLimit()>0?1:2;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(direction==1);
        if(direction==1){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.onTouchEvent(cancel);cancel.recycle();}
      }
    }
    if(action==MotionEvent.ACTION_MOVE && direction==1){
      scrollTo(Math.max(0,Math.min(scrollLimit(),startScroll+Math.round(downX-event.getX()))),0);
      return true;
    }
    if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
      if(direction==1){direction=0;return true;}
      direction=0;
    }
    return super.onTouchEvent(event);
  }

  @Override public boolean performClick(){return super.performClick();}

  float opticalShift() {
    String value=getText().toString().trim();
    if(value.length()>2)value=value.substring(1,value.length()-1).trim();
    if(value.isEmpty())return 0;
    getPaint().getTextBounds(value,0,value.length(),inkBounds);
    Paint.FontMetricsInt metrics=getPaint().getFontMetricsInt();
    float shift=(metrics.ascent+metrics.descent-inkBounds.top-inkBounds.bottom)/2f;
    float limit=1.5f*getResources().getDisplayMetrics().density;
    return Math.max(-limit,Math.min(limit,shift));
  }

  @Override protected void onDraw(Canvas canvas) {
    int save=canvas.save();
    canvas.translate(0,opticalShift());
    super.onDraw(canvas);
    canvas.restoreToCount(save);
  }
}
