package app.remotessh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Animates a temporary in-memory picture of a question between its window and bubble. */
final class QuestionBubbleMotion {
  private final Activity activity;
  private ValueAnimator animator;
  private View overlay;
  private Bitmap image;

  QuestionBubbleMotion(Activity activity) { this.activity=activity; }

  void run(Dialog dialog,View bubble,boolean collapse,Runnable done) {
    cancel();
    FrameLayout root=activity.findViewById(android.R.id.content);
    View decor=dialog.getWindow()==null?null:dialog.getWindow().getDecorView();
    if(root==null || decor==null || decor.getWidth()==0 || decor.getHeight()==0 || bubble.getWidth()==0) {
      if(collapse)dialog.hide();
      done.run();return;
    }
    int[] origin=new int[2],window=new int[2],target=new int[2];
    root.getLocationOnScreen(origin);decor.getLocationOnScreen(window);bubble.getLocationOnScreen(target);
    RectF full=new RectF(window[0]-origin[0],window[1]-origin[1],
        window[0]-origin[0]+decor.getWidth(),window[1]-origin[1]+decor.getHeight());
    RectF small=new RectF(target[0]-origin[0],target[1]-origin[1],
        target[0]-origin[0]+bubble.getWidth(),target[1]-origin[1]+bubble.getHeight());
    try {
      float scale=Math.min(1f,(float)Math.sqrt(1600000.0/(decor.getWidth()*(double)decor.getHeight())));
      image=Bitmap.createBitmap(Math.max(1,Math.round(decor.getWidth()*scale)),
          Math.max(1,Math.round(decor.getHeight()*scale)),Bitmap.Config.ARGB_8888);
      Canvas canvas=new Canvas(image);canvas.scale(scale,scale);decor.draw(canvas);
    } catch(RuntimeException | OutOfMemoryError ignored) { recycle(); }
    if(image==null) {
      if(collapse)dialog.hide();
      done.run();return;
    }
    final Bitmap snapshot=image;
    final RectF bounds=new RectF();
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final Path clip=new Path();
    final float[] progress={collapse?0:1};
    overlay=new View(activity){
      @Override protected void onDraw(Canvas canvas){
        float p=progress[0];canvas.drawColor(Color.argb(Math.round((1-p)*88),0,0,0));
        root.getLocationOnScreen(origin);bubble.getLocationOnScreen(target);
        small.offsetTo(target[0]-origin[0],target[1]-origin[1]);
        bounds.set(full.left+(small.left-full.left)*p,full.top+(small.top-full.top)*p,
            full.right+(small.right-full.right)*p,full.bottom+(small.bottom-full.bottom)*p);
        float radius=activity.getResources().getDisplayMetrics().density*24*(1-p)+small.width()/2*p;
        clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        canvas.save();canvas.clipPath(clip);paint.setAlpha(Math.round(255*(1-p*.35f)));
        canvas.drawBitmap(snapshot,null,bounds,paint);canvas.restore();
      }
    };
    overlay.setClickable(true);
    root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
    bubble.setAlpha(0f);bubble.setVisibility(View.VISIBLE);
    if(collapse)dialog.hide();
    animator=ValueAnimator.ofFloat(collapse?0:1,collapse?1:0);
    animator.setDuration(260);animator.setInterpolator(new PathInterpolator(.22f,0f,.2f,1f));
    animator.addUpdateListener(a->{
      progress[0]=(Float)a.getAnimatedValue();bubble.setAlpha(Math.max(0,(progress[0]-.65f)/.35f));
      if(overlay!=null)overlay.invalidate();
    });
    animator.addListener(new AnimatorListenerAdapter(){
      boolean cancelled;
      @Override public void onAnimationCancel(Animator animation){cancelled=true;}
      @Override public void onAnimationEnd(Animator animation){
        clear();if(!cancelled)done.run();
      }
    });
    animator.start();
  }

  void cancel(){if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();}clear();}
  private void clear(){
    animator=null;
    if(overlay!=null && overlay.getParent() instanceof ViewGroup)((ViewGroup)overlay.getParent()).removeView(overlay);
    overlay=null;recycle();
  }
  private void recycle(){if(image!=null && !image.isRecycled())image.recycle();image=null;}
}
