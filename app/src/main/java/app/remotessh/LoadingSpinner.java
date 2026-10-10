package app.remotessh;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.SystemClock;

/** Small monochrome indeterminate indicator used inside existing text buttons. */
final class LoadingSpinner extends Drawable implements Animatable {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean running;
  private long started;
  private final Runnable frame=()->{if(running){invalidateSelf();scheduleSelf(this.frame,SystemClock.uptimeMillis()+32);}};
  LoadingSpinner(int color,int size) {
    paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*AppIcons.STROKE_WIDTH/24f);paint.setStrokeCap(Paint.Cap.ROUND);
    setBounds(0,0,size,size);
  }
  public void draw(Canvas canvas) {
    Rect b=getBounds();float inset=paint.getStrokeWidth();
    float angle=running?((SystemClock.uptimeMillis()-started)%900)*360f/900:0;
    canvas.drawArc(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset,angle,260,false,paint);
  }
  public void start(){if(!running){running=true;started=SystemClock.uptimeMillis();frame.run();}}
  public void stop(){running=false;unscheduleSelf(frame);invalidateSelf();}
  public boolean isRunning(){return running;}
  public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
  public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
