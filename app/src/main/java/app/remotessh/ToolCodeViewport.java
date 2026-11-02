package app.remotessh;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.OverScroller;
import android.widget.TextView;

/** A selectable code surface with finger-anchored pinch and two-axis dragging. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with its selectable text child.
final class ToolCodeViewport extends FrameLayout {
  private static final float MIN_SCALE=.65f,MAX_SCALE=4f;
  private final TextView code;
  private final ScaleGestureDetector scaling;
  private final OverScroller inertia;
  private final int slop,minFling,maxFling;
  private VelocityTracker velocity;
  private float scale=1,downX,downY,lastX,lastY,focusX,focusY;
  private boolean dragged,multiple,ownsGesture;

  ToolCodeViewport(Context context,TextView code){
    super(context);this.code=code;
    ViewConfiguration config=ViewConfiguration.get(context);
    slop=config.getScaledTouchSlop();minFling=config.getScaledMinimumFlingVelocity();
    maxFling=config.getScaledMaximumFlingVelocity();inertia=new OverScroller(context);
    code.setPivotX(0);code.setPivotY(0);addView(code,new LayoutParams(-2,-2));
    scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScaleBegin(ScaleGestureDetector detector){
        focusX=detector.getFocusX();focusY=detector.getFocusY();return true;
      }
      @Override public boolean onScale(ScaleGestureDetector detector){
        float x=detector.getFocusX(),y=detector.getFocusY();
        zoom(scale*detector.getScaleFactor(),focusX,focusY,x,y);focusX=x;focusY=y;return true;
      }
    });
    scaling.setQuickScaleEnabled(false);
    setClipChildren(true);setClipToPadding(true);
  }

  @Override protected void onMeasure(int widthSpec,int heightSpec){
    int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
    code.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
    setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
    code.layout(0,0,code.getMeasuredWidth(),code.getMeasuredHeight());scrollTo(getScrollX(),getScrollY());
  }
  private int maxX(){return Math.max(0,Math.round(code.getWidth()*scale)-getWidth());}
  private int maxY(){return Math.max(0,Math.round(code.getHeight()*scale)-getHeight());}
  @Override public void scrollTo(int x,int y){super.scrollTo(Math.max(0,Math.min(maxX(),x)),Math.max(0,Math.min(maxY(),y)));}

  float scaleFactor(){return scale;}
  void zoom(float target,float oldX,float oldY,float newX,float newY){
    float next=Math.max(MIN_SCALE,Math.min(MAX_SCALE,target));
    float x=(getScrollX()+oldX)*next/scale-newX;
    float y=(getScrollY()+oldY)*next/scale-newY;
    scale=next;code.setScaleX(scale);code.setScaleY(scale);scrollTo(Math.round(x),Math.round(y));
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event){
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      inertia.abortAnimation();downX=lastX=event.getX();downY=lastY=event.getY();dragged=multiple=ownsGesture=false;
      if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();
    }
    if(velocity!=null)velocity.addMovement(event);
    if(event.getPointerCount()>1)multiple=true;
    scaling.onTouchEvent(event);
    // Selectable TextView may disallow parent interception on DOWN. Take over
    // actual drags ourselves, but leave a long-pressed text selection with it.
    if(!ownsGesture && action!=MotionEvent.ACTION_DOWN && onInterceptTouchEvent(event)){
      MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
      super.dispatchTouchEvent(cancel);cancel.recycle();ownsGesture=true;
    }
    boolean handled=ownsGesture?onTouchEvent(event):super.dispatchTouchEvent(event);
    if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
      if(velocity!=null){velocity.recycle();velocity=null;}
    }
    return handled;
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent event){
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_POINTER_DOWN)return true;
    if(action==MotionEvent.ACTION_MOVE){
      boolean selecting=event.getEventTime()-event.getDownTime()>=ViewConfiguration.getLongPressTimeout() && code.hasSelection();
      return !selecting && Math.hypot(event.getX()-downX,event.getY()-downY)>slop;
    }
    return false;
  }

  @android.annotation.SuppressLint("ClickableViewAccessibility") // The selectable TextView owns clicks and long presses.
  @Override public boolean onTouchEvent(MotionEvent event){
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_MOVE && event.getPointerCount()==1 && !scaling.isInProgress()){
      float x=event.getX(),y=event.getY();
      if(Math.hypot(x-downX,y-downY)>slop)dragged=true;
      if(dragged)scrollBy(Math.round(lastX-x),Math.round(lastY-y));
      lastX=x;lastY=y;
    }else if(action==MotionEvent.ACTION_POINTER_UP){
      int index=event.getActionIndex()==0?1:0;
      if(index<event.getPointerCount()){lastX=event.getX(index);lastY=event.getY(index);}
    }else if(action==MotionEvent.ACTION_UP && dragged && !multiple && velocity!=null){
      velocity.computeCurrentVelocity(1000,maxFling);
      int vx=Math.round(-velocity.getXVelocity()),vy=Math.round(-velocity.getYVelocity());
      if(Math.abs(vx)>=minFling || Math.abs(vy)>=minFling){
        inertia.fling(getScrollX(),getScrollY(),vx,vy,0,maxX(),0,maxY());postInvalidateOnAnimation();
      }
    }
    return true;
  }
  @Override public void computeScroll(){
    if(inertia.computeScrollOffset()){scrollTo(inertia.getCurrX(),inertia.getCurrY());postInvalidateOnAnimation();}
  }
  @Override protected void onDetachedFromWindow(){inertia.abortAnimation();super.onDetachedFromWindow();}
}
