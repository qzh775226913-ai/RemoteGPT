package app.remotessh;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;
import android.widget.TextView;

/** Follow only while the reader is at the latest message, including delayed layout. */
public final class ConversationScrollView extends ScrollView {
  private boolean following = true, layingOut, pinning;
  private View horizontalGesture;
  private boolean handlingUserScroll, flinging;
  private long lastFlingMotion;
  private final int touchSlop;
  private Runnable readingTap;
  private float tapX,tapY;
  private long tapTime;
  private boolean tapCandidate;
  public ConversationScrollView(Context context) {
    super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
  }
  public boolean isFollowing() { return following; }
  public void follow() { following = true; requestLayout(); }
  public void read() { following = false; }
  boolean userScrolling() {return handlingUserScroll;}
  void setOnReadingTap(Runnable action){readingTap=action;}
  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      flinging=false;tapX=event.getX();tapY=event.getY();tapTime=event.getEventTime();
      tapCandidate=readingTap!=null && !interactiveAt(this,tapX,tapY);
    } else if(action==MotionEvent.ACTION_MOVE){
      if(Math.abs(event.getX()-tapX)>touchSlop || Math.abs(event.getY()-tapY)>touchSlop)tapCandidate=false;
    } else if(action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_CANCEL)tapCandidate=false;
    boolean tap=action==MotionEvent.ACTION_UP && tapCandidate &&
        event.getEventTime()-tapTime<ViewConfiguration.getLongPressTimeout() &&
        Math.abs(event.getX()-tapX)<=touchSlop && Math.abs(event.getY()-tapY)<=touchSlop &&
        !interactiveAt(this,event.getX(),event.getY());
    boolean handled=super.dispatchTouchEvent(event);
    if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)tapCandidate=false;
    if(tap && readingTap!=null)readingTap.run();
    return handled;
  }

  private boolean interactiveAt(View view,float x,float y){
    if(view!=this && view.hasOnClickListeners())return true;
    if(view instanceof TextView){
      TextView text=(TextView)view;CharSequence value=text.getText();Layout layout=text.getLayout();
      if(value instanceof Spanned && layout!=null){
        int px=(int)(x-text.getTotalPaddingLeft()+text.getScrollX());
        int py=(int)(y-text.getTotalPaddingTop()+text.getScrollY());
        if(py>=0 && py<layout.getHeight()){
          int line=layout.getLineForVertical(py);
          if(px>=layout.getLineLeft(line) && px<=layout.getLineRight(line)){
            int offset=layout.getOffsetForHorizontal(line,px);
            if(((Spanned)value).getSpans(offset,offset,ClickableSpan.class).length>0)return true;
          }
        }
      }
      return false;
    }
    if(view instanceof ViewGroup){
      ViewGroup group=(ViewGroup)view;
      for(int i=group.getChildCount()-1;i>=0;i--){
        View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;
        float cx=x+group.getScrollX()-child.getX(),cy=y+group.getScrollY()-child.getY();
        if(cx>=0 && cy>=0 && cx<child.getWidth() && cy<child.getHeight() && interactiveAt(child,cx,cy))return true;
      }
    }
    return view!=this && view.isClickable();
  }
  @android.annotation.SuppressLint("ClickableViewAccessibility") // ScrollView handles clicks; this observes its drag.
  @Override public boolean onTouchEvent(MotionEvent event) {
    boolean previous=handlingUserScroll;
    handlingUserScroll=event.getActionMasked()==MotionEvent.ACTION_MOVE;
    try{return super.onTouchEvent(event);}finally{handlingUserScroll=previous;}
  }
  @Override public void fling(int velocityY) {
    flinging=true;lastFlingMotion=android.os.SystemClock.uptimeMillis();super.fling(velocityY);
  }
  @Override public void computeScroll() {
    int before=getScrollY();boolean previous=handlingUserScroll;
    handlingUserScroll=flinging;
    try{super.computeScroll();}finally{handlingUserScroll=previous;}
    if(getScrollY()!=before)lastFlingMotion=android.os.SystemClock.uptimeMillis();
    else if(android.os.SystemClock.uptimeMillis()-lastFlingMotion>100)flinging=false;
  }
  void holdHorizontalGesture(View owner) { horizontalGesture = owner; }
  void releaseHorizontalGesture(View owner) {
    if (horizontalGesture == owner) horizontalGesture = null;
  }
  @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    // Selectable table text may request focus on UP. Preserve the reading Y for
    // that horizontal gesture while still letting Android update focus normally.
    return horizontalGesture != null ? 0 : super.computeScrollDeltaToGetChildRectOnScreen(rect);
  }
  public boolean nearBottom() {
    return getChildCount() == 0 || getChildAt(0).getHeight() + getPaddingTop()
        + getPaddingBottom() - getHeight() - getScrollY() <= 2 * getResources().getDisplayMetrics().density;
  }
  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int readingY = getScrollY();
    layingOut = true;
    super.onLayout(changed, l, t, r, b);
    if (!following) scrollTo(0, readingY);
    layingOut = false;
    pin();
  }
  public void pin() {
    if (!following || getChildCount() == 0) return;
    pinning = true;
    scrollTo(0, Math.max(0, getChildAt(0).getHeight() - getHeight() + getPaddingTop() + getPaddingBottom()));
    pinning = false;
  }
  @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
    if (!layingOut && !pinning) following = nearBottom();
    super.onScrollChanged(x, y, oldX, oldY);
  }
}
