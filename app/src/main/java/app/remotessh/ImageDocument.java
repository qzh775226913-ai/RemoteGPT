package app.remotessh;

import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;

/** A viewer-owned temporary original, bounded overview and optional region decoder. */
final class ImageDocument implements AutoCloseable {
  static final long OVERVIEW_PIXELS=2L*1024*1024, TILE_PIXELS=4L*1024*1024;
  static final int MAX_BITMAP_SIDE=4096;
  final File file;
  final long fileBytes;
  final Bitmap overview;
  final int width,height,displayWidth,displayHeight,overviewSample;
  final Matrix orientation=new Matrix(),inverseOrientation=new Matrix();
  private final BitmapRegionDecoder decoder;
  private boolean closed;

  ImageDocument(File file) throws IOException {
    this.file=file;fileBytes=file.length();
    BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;
    BitmapFactory.decodeFile(file.getPath(),info);
    if(info.outWidth<=0 || info.outHeight<=0)throw new IOException("无法解码图片");
    width=info.outWidth;height=info.outHeight;
    int exif=1;
    try{exif=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
    if(exif<1 || exif>8)exif=1;
    displayWidth=exif>=5?height:width;displayHeight=exif>=5?width:height;
    float[][] transforms={{1,0,0,0,1,0},{-1,0,width,0,1,0},{-1,0,width,0,-1,height},{1,0,0,0,-1,height},
        {0,1,0,1,0,0},{0,-1,height,1,0,0},{0,-1,height,-1,0,width},{0,1,0,-1,0,width}};
    float[] t=transforms[exif-1];orientation.setValues(new float[]{t[0],t[1],t[2],t[3],t[4],t[5],0,0,1});orientation.invert(inverseOrientation);
    overviewSample=sample(width,height,OVERVIEW_PIXELS);
    info.inJustDecodeBounds=false;info.inSampleSize=overviewSample;
    overview=BitmapFactory.decodeFile(file.getPath(),info);
    if(overview==null)throw new IOException("无法解码图片");
    BitmapRegionDecoder region=null;
    try{region=BitmapRegionDecoder.newInstance(file.getPath(),false);}catch(IOException | RuntimeException ignored){}
    decoder=region;
  }

  static int sample(int width,int height,long pixels) {
    int result=1;
    while(((width+(long)result-1)/result)*((height+(long)result-1)/result)>pixels
        || (width+(long)result-1)/result>MAX_BITMAP_SIDE || (height+(long)result-1)/result>MAX_BITMAP_SIDE)result*=2;
    return result;
  }

  static final class Region {
    final Rect bounds;final int sample;
    Region(Rect bounds,int sample){this.bounds=bounds;this.sample=sample;}
  }
  Region region(float scale,float offsetX,float offsetY,int viewportWidth,int viewportHeight) {
    float padding=96/scale;
    RectF visible=new RectF(-offsetX/scale-padding,-offsetY/scale-padding,
        (viewportWidth-offsetX)/scale+padding,(viewportHeight-offsetY)/scale+padding);
    inverseOrientation.mapRect(visible);
    Rect bounds=new Rect(Math.max(0,(int)Math.floor(visible.left)),Math.max(0,(int)Math.floor(visible.top)),
        Math.min(width,(int)Math.ceil(visible.right)),Math.min(height,(int)Math.ceil(visible.bottom)));
    if(bounds.isEmpty())return null;
    int sampling=1;
    while(1f/(sampling*2)>=scale && sampling<1073741824)sampling*=2;
    sampling=Math.max(sampling,sample(bounds.width(),bounds.height(),TILE_PIXELS));
    return new Region(bounds,sampling);
  }
  synchronized Bitmap decode(Region region) {
    if(closed || decoder==null || region==null)return null;
    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=region.sample;
    return decoder.decodeRegion(region.bounds,options);
  }
  boolean hasRegions(){return decoder!=null;}
  @Override public synchronized void close() {
    if(closed)return;closed=true;
    if(decoder!=null)decoder.recycle();overview.recycle();file.delete();
  }
}
