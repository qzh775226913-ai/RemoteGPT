package app.remotessh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A fine rim and short inner shadows keep the composer visibly recessed. */
final class ComposerSurface extends Drawable {
  private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), shade=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds=new RectF(),strokeBounds=new RectF();
  private final Path clip=new Path();
  private final Shader top,bottom,side;
  private final float corner,topDepth,bottomDepth,sideDepth,stroke;
  private final int rimAlpha;
  private float radius;

  static Drawable create(Context context,int panel){return new ComposerSurface(context,panel);}

  private ComposerSurface(Context context,int panel){
    float density=context.getResources().getDisplayMetrics().density;
    boolean dark=Color.red(panel)+Color.green(panel)+Color.blue(panel)<384;
    corner=26*density;topDepth=14*density;bottomDepth=12*density;sideDepth=7*density;
    stroke=Math.max(1f,.75f*density);
    fill.setColor(panel);
    rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(stroke);
    rimAlpha=dark?32:90;rim.setColor(Color.argb(rimAlpha,255,255,255));
    top=fade(Color.argb(dark?100:22,0,0,0),Color.TRANSPARENT,topDepth,false);
    bottom=fade(Color.argb(0,255,255,255),Color.argb(dark?20:100,255,255,255),bottomDepth,false);
    side=fade(Color.argb(dark?55:12,0,0,0),Color.TRANSPARENT,sideDepth,true);
  }

  private static Shader fade(int start,int end,float distance,boolean horizontal){
    return new LinearGradient(0,0,horizontal?distance:0,horizontal?0:distance,start,end,Shader.TileMode.CLAMP);
  }

  @Override protected void onBoundsChange(Rect rect){
    bounds.set(rect);radius=Math.min(corner,Math.min(bounds.width(),bounds.height())/2f);
    clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
    strokeBounds.set(bounds);strokeBounds.inset(stroke/2f,stroke/2f);
  }

  @Override public void draw(Canvas canvas){
    if(bounds.isEmpty())return;
    canvas.drawRoundRect(bounds,radius,radius,fill);
    int saved=canvas.save();canvas.clipPath(clip);canvas.translate(bounds.left,bounds.top);
    float width=bounds.width(),height=bounds.height();
    shade.setShader(top);canvas.drawRect(0,0,width,Math.min(height,topDepth),shade);
    int bottomSave=canvas.save();canvas.translate(0,Math.max(0,height-bottomDepth));
    shade.setShader(bottom);canvas.drawRect(0,0,width,Math.min(height,bottomDepth),shade);canvas.restoreToCount(bottomSave);
    shade.setShader(side);canvas.drawRect(0,0,Math.min(width,sideDepth),height,shade);
    int rightSave=canvas.save();canvas.translate(width,0);canvas.scale(-1,1);
    canvas.drawRect(0,0,Math.min(width,sideDepth),height,shade);canvas.restoreToCount(rightSave);
    canvas.restoreToCount(saved);
    canvas.drawRoundRect(strokeBounds,Math.max(0,radius-stroke/2f),Math.max(0,radius-stroke/2f),rim);
  }

  @Override public void getOutline(Outline outline){outline.setRoundRect(getBounds(),radius);}
  @Override public boolean getPadding(Rect padding){padding.setEmpty();return false;}
  @Override public void setAlpha(int alpha){fill.setAlpha(alpha);shade.setAlpha(alpha);rim.setAlpha(rimAlpha*alpha/255);invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){fill.setColorFilter(filter);shade.setColorFilter(filter);rim.setColorFilter(filter);invalidateSelf();}
  @SuppressWarnings("deprecation") @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
