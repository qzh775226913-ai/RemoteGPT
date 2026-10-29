package app.remotessh;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import java.util.concurrent.ExecutorService;

/** Native pinch/pan viewer. A zoomed image changes pages only on a new outward edge swipe. */
final class ZoomImageView extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private final Rect originalBounds=new Rect();
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ScaleGestureDetector scaling;
  private final GestureDetector gestures;
  private final ExecutorService worker;
  private final java.util.function.IntConsumer page;
  private ImageDocument document;
  private Bitmap placeholder,detail;
  private Rect detailBounds;
  private volatile long revision;
  private float scale,fit,offsetX,offsetY,downX,downY;
  private boolean multiple,doubleTap,disposed;
  private boolean previousEdge,nextEdge;
  private final Runnable requestDetail=this::decodeDetail;

  ZoomImageView(Context context,ExecutorService worker,java.util.function.IntConsumer page) {
    super(context);this.worker=worker;this.page=page;setContentDescription(UiText.get(R.string.ui_full_image_preview));
    scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScaleBegin(ScaleGestureDetector detector){multiple=true;return true;}
      @Override public boolean onScale(ScaleGestureDetector detector){zoom(scale*detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());return true;}
    });
    gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent event){return true;}
      @Override public boolean onDoubleTap(MotionEvent event){doubleTap=true;zoom(isZoomed()?fit:fit*3,event.getX(),event.getY());return true;}
      @Override public boolean onScroll(MotionEvent first,MotionEvent event,float dx,float dy){
        if(!scaling.isInProgress() && event.getPointerCount()==1 && isZoomed()){offsetX-=dx;offsetY-=dy;constrain();changed();}return true;
      }
    });
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }
  void setPlaceholder(Bitmap bitmap){clear();placeholder=bitmap;reset();}
  void setDocument(ImageDocument value){
    float factor=fit>0?scale/fit:1;
    float[] center={imageWidth()>0?(getWidth()/2f-offsetX)/scale/imageWidth():.5f,imageHeight()>0?(getHeight()/2f-offsetY)/scale/imageHeight():.5f};
    boolean fromThumbnail=placeholder!=null;
    clear();document=value;originalBounds.set(0,0,value.width,value.height);reset();
    if(fromThumbnail && factor>1.01f){
      center[0]*=value.width;center[1]*=value.height;value.orientation.mapPoints(center);
      zoom(fit*factor,getWidth()/2f,getHeight()/2f);offsetX=getWidth()/2f-center[0]*scale;offsetY=getHeight()/2f-center[1]*scale;constrain();changed();
    }
  }
  void clear(){revision++;removeCallbacks(requestDetail);document=null;placeholder=null;scale=fit=offsetX=offsetY=0;releaseDetail();invalidate();}
  void dispose(){disposed=true;clear();}
  private void releaseDetail(){if(detail!=null){detail.recycle();detail=null;}detailBounds=null;}
  private int imageWidth(){return document!=null?document.displayWidth:placeholder!=null?placeholder.getWidth():0;}
  private int imageHeight(){return document!=null?document.displayHeight:placeholder!=null?placeholder.getHeight():0;}
  void reset(){if(imageWidth()==0 || getWidth()==0 || getHeight()==0)return;fit=Math.min((float)getWidth()/imageWidth(),(float)getHeight()/imageHeight());scale=fit;constrain();changed();}
  boolean isZoomed(){return fit>0 && scale>fit*1.01f;}
  float zoomScale(){return scale;}
  float fitScale(){return fit;}
  RectF displayedBounds(){return new RectF(offsetX,offsetY,offsetX+imageWidth()*scale,offsetY+imageHeight()*scale);}
  void zoom(float target,float focusX,float focusY) {
    if(fit<=0)return;float next=Math.max(fit,Math.min(Math.max(fit*8,1),target));
    offsetX=focusX-(focusX-offsetX)*next/scale;offsetY=focusY-(focusY-offsetY)*next/scale;scale=next;constrain();changed();
  }
  private void constrain(){offsetX=clamp(offsetX,imageWidth()*scale,getWidth());offsetY=clamp(offsetY,imageHeight()*scale,getHeight());}
  private static float clamp(float offset,float image,float viewport){return image<=viewport?(viewport-image)/2:Math.max(viewport-image,Math.min(0,offset));}
  private void changed(){revision++;invalidate();removeCallbacks(requestDetail);if(document!=null && document.hasRegions() && !disposed)postDelayed(requestDetail,80);}
  @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
    super.onSizeChanged(w,h,oldW,oldH);
    float factor=fit>0?scale/fit:1;
    float cx=imageWidth()>0 && scale>0?(oldW/2f-offsetX)/scale/imageWidth():.5f;
    float cy=imageHeight()>0 && scale>0?(oldH/2f-offsetY)/scale/imageHeight():.5f;
    reset();
    if(factor>1.01f){zoom(fit*factor,w/2f,h/2f);offsetX=w/2f-cx*imageWidth()*scale;offsetY=h/2f-cy*imageHeight()*scale;constrain();changed();}
  }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);if(imageWidth()==0 || scale<=0)return;
    canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
    if(document!=null){canvas.concat(document.orientation);canvas.drawBitmap(document.overview,null,originalBounds,paint);
      if(detail!=null)canvas.drawBitmap(detail,null,detailBounds,paint);
    }else if(placeholder!=null && !placeholder.isRecycled())canvas.drawBitmap(placeholder,0,0,paint);
    canvas.restore();
  }
  private void decodeDetail() {
    ImageDocument source=document;long token=revision;
    if(source==null || disposed)return;
    ImageDocument.Region region=source.region(scale,offsetX,offsetY,getWidth(),getHeight());
    if(region==null)return;
    if(region.sample>=source.overviewSample){releaseDetail();invalidate();return;}
    worker.execute(()->{
      if(token!=revision)return;
      Bitmap bitmap=null;
      try{bitmap=source.decode(region);}catch(RuntimeException | OutOfMemoryError ignored){}
      if(bitmap==null)return;
      if(token!=revision){bitmap.recycle();return;}
      Bitmap ready=bitmap;
      main.post(()->{if(token!=revision || source!=document || disposed){ready.recycle();return;}releaseDetail();detail=ready;detailBounds=region.bounds;invalidate();});
    });
  }
  @Override public boolean onTouchEvent(MotionEvent event) {
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
      downX=event.getX();downY=event.getY();multiple=false;doubleTap=false;
      RectF bounds=displayedBounds();previousEdge=!isZoomed() || bounds.left>=-1;nextEdge=!isZoomed() || bounds.right<=getWidth()+1;
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
    }
    if(event.getPointerCount()>1)multiple=true;
    scaling.onTouchEvent(event);gestures.onTouchEvent(event);
    if(event.getActionMasked()==MotionEvent.ACTION_UP) {
      float dx=event.getX()-downX,dy=event.getY()-downY;
      if(!multiple && !doubleTap && (dx<0?nextEdge:previousEdge) && Math.abs(dx)>48*getResources().getDisplayMetrics().density && Math.abs(dx)>Math.abs(dy)*1.3f)page.accept(dx<0?1:-1);
      else if(!multiple && !doubleTap && Math.hypot(dx,dy)<8*getResources().getDisplayMetrics().density)performClick();
    }
    if((event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL) && getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
}
