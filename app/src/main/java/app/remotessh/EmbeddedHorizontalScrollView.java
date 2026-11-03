package app.remotessh;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

/** Keep card focus in place and choose an axis before either scroll view drags. */
class EmbeddedHorizontalScrollView extends HorizontalScrollView {
  private float downX,downY;
  private int direction; // 0: undecided, 1: horizontal, 2: vertical
  private final int touchSlop;
  private ConversationScrollView gestureParent;

  EmbeddedHorizontalScrollView(Context context){
    super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event){
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      releaseGesture();direction=0;downX=event.getRawX();downY=event.getRawY();
      for(ViewParent parent=getParent();parent!=null;parent=parent.getParent()){
        if(parent instanceof ConversationScrollView){
          gestureParent=(ConversationScrollView)parent;gestureParent.holdContentGesture(this);break;
        }
      }
      if(getParent()!=null && (canScrollHorizontally(-1)||canScrollHorizontally(1)))
        getParent().requestDisallowInterceptTouchEvent(true);
    }else if(action==MotionEvent.ACTION_MOVE)prepareGesture(event);
    // Selectable text can disallow the ancestor again while handling MOVE.
    // Cancel its touch once the vertical axis wins so it cannot scroll its own
    // selection or retain the old horizontal fling before the next parent MOVE.
    if(direction==2 && action==MotionEvent.ACTION_MOVE){
      MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
      try{super.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
      return true;
    }
    try{return super.dispatchTouchEvent(event);}
    finally{
      if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
        direction=0;releaseGesture();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
      }
    }
  }

  // Called by the conversation before interception, as well as locally when
  // embedded elsewhere. Raw coordinates survive header animation and offsets.
  void prepareGesture(MotionEvent event){
    if(selectingText(event)){releaseGesture();return;}
    if(direction!=0)return;
    float dx=Math.abs(event.getRawX()-downX),dy=Math.abs(event.getRawY()-downY);
    if(Math.max(dx,dy)>touchSlop){
      direction=dx>dy && (canScrollHorizontally(-1)||canScrollHorizontally(1))?1:2;
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(direction==1);
    }
  }

  private boolean selectingText(MotionEvent event){
    return getChildCount()>0 && getChildAt(0) instanceof TextView && ((TextView)getChildAt(0)).hasSelection()
        && event.getEventTime()-event.getDownTime()>=ViewConfiguration.getLongPressTimeout();
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent event){
    return !selectingText(event) && direction!=2 && super.onInterceptTouchEvent(event);
  }

  private void releaseGesture(){
    if(gestureParent!=null){gestureParent.releaseContentGesture(this);gestureParent=null;}
  }
  @Override protected void onDetachedFromWindow(){
    releaseGesture();direction=0;
    if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    super.onDetachedFromWindow();
  }
}
