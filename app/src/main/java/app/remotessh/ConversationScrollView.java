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
  private boolean handlingUserScroll, flinging, userScrollCallback, flingReachedLatest;
  private int touchDirection,flingDirection,flingLatestLimit;
  private float lastRawY;
  private long lastFlingMotion;
  private final int touchSlop;
  private Runnable readingTap;
  private float tapX,tapY;
  private long tapTime;
  private boolean tapCandidate,touchActive,screenTopKnown;
  private int touchStartScreenTop,lastScreenTop;
  private final int[] screenPosition=new int[2];
  public ConversationScrollView(Context context) {
    super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
  }
  public boolean isFollowing() { return following; }
  public void follow() { stopFling(); following = true; requestLayout(); }
  public void read() { following = false; flingReachedLatest=false; }
  boolean userScrolling() {return userScrollCallback;}
  void setOnReadingTap(Runnable action){readingTap=action;}
  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      getLocationOnScreen(screenPosition);touchStartScreenTop=lastScreenTop=screenPosition[1];screenTopKnown=true;touchActive=true;
      flinging=false;flingReachedLatest=false;touchDirection=0;lastRawY=event.getRawY();tapX=event.getRawX();tapY=event.getRawY();tapTime=event.getEventTime();
      tapCandidate=readingTap!=null && !interactiveAt(this,event.getX(),event.getY());
    } else if(action==MotionEvent.ACTION_MOVE){
      touchDirection=Float.compare(lastRawY,event.getRawY());lastRawY=event.getRawY();
      if(Math.abs(event.getRawX()-tapX)>touchSlop || Math.abs(event.getRawY()-tapY)>touchSlop)tapCandidate=false;
    } else if(action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_CANCEL)tapCandidate=false;
    boolean tap=action==MotionEvent.ACTION_UP && tapCandidate &&
        event.getEventTime()-tapTime<ViewConfiguration.getLongPressTimeout() &&
        Math.abs(event.getRawX()-tapX)<=touchSlop && Math.abs(event.getRawY()-tapY)<=touchSlop &&
        !interactiveAt(this,event.getX(),event.getY());
    boolean handled=super.dispatchTouchEvent(event);
    if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_DOWN && !handled){tapCandidate=false;touchActive=false;}
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
  // Keep ScrollView's internal gesture coordinates stable while its header moves.
  private MotionEvent stableTouch(MotionEvent event){
    if(!touchActive)return null;
    getLocationOnScreen(screenPosition);int delta=screenPosition[1]-touchStartScreenTop;
    if(delta==0)return null;
    MotionEvent copy=MotionEvent.obtain(event);copy.offsetLocation(0,delta);return copy;
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent event){
    MotionEvent stable=stableTouch(event);
    try{return super.onInterceptTouchEvent(stable==null?event:stable);}finally{if(stable!=null)stable.recycle();}
  }
  @android.annotation.SuppressLint("ClickableViewAccessibility") // ScrollView handles clicks; this observes its drag.
  @Override public boolean onTouchEvent(MotionEvent event) {
    boolean previous=handlingUserScroll;
    handlingUserScroll=event.getActionMasked()==MotionEvent.ACTION_MOVE;
    MotionEvent stable=stableTouch(event);
    try{return super.onTouchEvent(stable==null?event:stable);}finally{if(stable!=null)stable.recycle();handlingUserScroll=previous;}
  }
  @Override public void fling(int velocityY) {
    flinging=true;flingDirection=Integer.signum(velocityY);
    flingReachedLatest=flingDirection>0 && following && nearBottom();
    flingLatestLimit=latestLimit();
    lastFlingMotion=android.os.SystemClock.uptimeMillis();super.fling(velocityY);
  }

  private void stopFling(){
    if(!flinging || getChildCount()==0){flinging=false;flingReachedLatest=false;return;}
    // ScrollView has no public abort API. A down/cancel pair uses its normal touch path
    // to abort the platform scroller without sending a click to message children.
    long now=android.os.SystemClock.uptimeMillis();float x=getWidth()/2f,y=getHeight()/2f;
    MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
    MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,x,y,0);
    boolean previous=pinning;pinning=true;
    try{
      super.onTouchEvent(down);scrollTo(getScrollX(),getScrollY());super.onTouchEvent(cancel);
      super.onInterceptTouchEvent(cancel);stopNestedScroll();
    }finally{
      pinning=previous;down.recycle();cancel.recycle();flinging=false;flingReachedLatest=false;flingDirection=0;
    }
  }
  @Override public void computeScroll() {
    int before=getScrollY();boolean previous=handlingUserScroll;
    handlingUserScroll=flinging;
    try{super.computeScroll();}finally{handlingUserScroll=previous;}
    // The platform fling keeps the range it had when it started. Stop it as soon
    // as it reaches that range; later footer and stream layouts may have enlarged it.
    if(flinging && flingDirection>0 && flingReachedLatest){
      stopFling();if(following)pin();return;
    }
    if(getScrollY()!=before)lastFlingMotion=android.os.SystemClock.uptimeMillis();
    else if(android.os.SystemClock.uptimeMillis()-lastFlingMotion>100){
      boolean reached=flingReachedLatest;flinging=false;flingReachedLatest=false;
      if(reached && following)pin();
    }
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
  @Override protected void onDetachedFromWindow(){touchActive=false;screenTopKnown=false;super.onDetachedFromWindow();}
  private int latestLimit(){
    return getChildCount()==0?0:Math.max(0,getChildAt(0).getHeight()+getPaddingTop()+getPaddingBottom()-getHeight());
  }
  public boolean nearBottom() {
    return getChildCount()==0 || latestLimit()-getScrollY()<=2*getResources().getDisplayMetrics().density;
  }
  void preserveVisualOffset(int amount){
    boolean previous=pinning;pinning=true;
    try{scrollTo(getScrollX(),getScrollY()+amount);}finally{pinning=previous;}
  }
  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int readingY = getScrollY();
    getLocationOnScreen(screenPosition);int top=screenPosition[1];
    int anchorAdjustment=touchActive && screenTopKnown && !following?top-lastScreenTop:0;
    layingOut = true;
    super.onLayout(changed, l, t, r, b);
    if (!following) scrollTo(0, readingY+anchorAdjustment);
    layingOut = false;
    lastScreenTop=top;screenTopKnown=true;
    pin();
  }
  public void pin() {
    if (!following || getChildCount() == 0) return;
    pinning = true;
    scrollTo(0, Math.max(0, getChildAt(0).getHeight() - getHeight() + getPaddingTop() + getPaddingBottom()));
    pinning = false;
  }
  @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
    boolean atLatest=nearBottom();
    if(flinging && flingDirection>0 && (atLatest ||
        y>=flingLatestLimit-2*getResources().getDisplayMetrics().density))flingReachedLatest=true;
    if (!layingOut && !pinning) following = atLatest || (flinging && flingDirection>0 && flingReachedLatest);
    boolean previous=userScrollCallback;
    int direction=flinging?flingDirection:touchDirection;
    userScrollCallback=handlingUserScroll && !layingOut && !pinning && direction!=0 &&
        Integer.signum(y-oldY)==direction;
    try{super.onScrollChanged(x, y, oldX, oldY);}finally{userScrollCallback=previous;}
  }
}
