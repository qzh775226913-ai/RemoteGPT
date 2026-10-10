package app.remotessh;

import android.graphics.*;
import java.io.*;
import org.robolectric.annotation.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBitmapRegionDecoder;

/** Robolectric 4.14's region decoder returns blank, unsampled bitmaps. Use an independent
 * ImageIO region/subsampling decoder to exercise the viewer's coordinates and pixel budgets. */
@Implements(BitmapRegionDecoder.class)
public final class RegionDecoderShadow extends ShadowBitmapRegionDecoder {
  private String path;
  @Implementation protected static BitmapRegionDecoder newInstance(String path,boolean shareable) throws IOException {
    BitmapRegionDecoder decoder=ShadowBitmapRegionDecoder.newInstance(path,shareable);
    ((RegionDecoderShadow)Shadow.extract(decoder)).path=path;return decoder;
  }
  @Implementation protected Bitmap decodeRegion(Rect bounds,BitmapFactory.Options options) {
    // The Android test compiler excludes java.desktop; reflection reaches the host JDK decoder.
    try {
      Class<?> io=Class.forName("javax.imageio.ImageIO"),readerType=Class.forName("javax.imageio.ImageReader"),paramType=Class.forName("javax.imageio.ImageReadParam");
      Object stream=io.getMethod("createImageInputStream",Object.class).invoke(null,new File(path));
      try(AutoCloseable close=(AutoCloseable)stream) {
        java.util.Iterator<?> readers=(java.util.Iterator<?>)io.getMethod("getImageReaders",Object.class).invoke(null,stream);
        if(!readers.hasNext())return null;Object reader=readers.next();
        try {
          readerType.getMethod("setInput",Object.class).invoke(reader,stream);
          Object params=readerType.getMethod("getDefaultReadParam").invoke(reader);
          Class<?> rectangle=Class.forName("java.awt.Rectangle");
          Object region=rectangle.getConstructor(int.class,int.class,int.class,int.class).newInstance(bounds.left,bounds.top,bounds.width(),bounds.height());
          paramType.getMethod("setSourceRegion",rectangle).invoke(params,region);
          int sample=Math.max(1,options.inSampleSize);paramType.getMethod("setSourceSubsampling",int.class,int.class,int.class,int.class).invoke(params,sample,sample,0,0);
          Object decoded=readerType.getMethod("read",int.class,paramType).invoke(reader,0,params);Class<?> bitmap=Class.forName("java.awt.image.BufferedImage");
          int w=(int)bitmap.getMethod("getWidth").invoke(decoded),h=(int)bitmap.getMethod("getHeight").invoke(decoded);
          int[] pixels=(int[])bitmap.getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class).invoke(decoded,0,0,w,h,null,0,w);
          return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        }finally{readerType.getMethod("dispose").invoke(reader);}
      }
    }catch(Exception error){throw new IllegalStateException(error);}
  }
}
