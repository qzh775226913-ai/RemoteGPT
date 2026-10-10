package app.remotessh;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IconStyleTest {
  private final Context context=RuntimeEnvironment.getApplication();
  private static Bitmap raster(Drawable drawable,int size) {
    Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(bitmap));return bitmap;
  }
  private static void save(Bitmap bitmap,String name) throws Exception {
    File folder=new File("build/screenshots");assertTrue(folder.isDirectory() || folder.mkdirs());
    try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
  }
  @Test public void everyGlyphHasTransparentPaddingAndReadableSmallSize() throws Exception {
    for(String name:AppIconCatalog.NAMES) {
      Bitmap image=raster(AppIcons.drawable(context,name,Color.WHITE),24);int count=0;
      for(int y=0;y<24;y++)for(int x=0;x<24;x++)if(Color.alpha(image.getPixel(x,y))>0){count++;assertTrue("Glyph must stay inside its viewport: "+name,x>0 && x<23 && y>0 && y<23);}
      assertTrue("Icon must be legible at notification size: "+name,count>12);assertTrue("Icons must have negative space: "+name,count<350);image.recycle();
    }
    gallery(false);gallery(true);
  }
  private void gallery(boolean dark) throws Exception {
    Bitmap image=Bitmap.createBitmap(960,((AppIconCatalog.NAMES.length+5)/6)*160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(dark?0xff171717:Color.WHITE);
    Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(dark?0xffeeeeee:0xff171717);text.setTextSize(18);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
    for(int i=0;i<AppIconCatalog.NAMES.length;i++) {
      int x=(i%6)*160,y=(i/6)*160;String name=AppIconCatalog.NAMES[i];Drawable glyph=AppIcons.drawable(context,name,text.getColor());
      glyph.setBounds(x+48,y+30,x+112,y+94);glyph.draw(canvas);canvas.drawText(name,x+24,y+130,text);
    }
    save(image,"icon-system-"+(dark?"dark":"light"));image.recycle();
  }
  @Test public void notificationIsSameBrandAlphaMaskWithoutSolidBackground() throws Exception {
    Bitmap notification=raster(context.getDrawable(R.drawable.ic_notification),24),brand=raster(AppIcons.drawable(context,"brand",Color.WHITE),24);
    assertTrue(notification.sameAs(brand));assertEquals(Color.TRANSPARENT,notification.getPixel(0,0));
    for(int y=0;y<24;y++)for(int x=0;x<24;x++)if(Color.alpha(notification.getPixel(x,y))>0)assertEquals(0xffffff,notification.getPixel(x,y)&0xffffff);
    Bitmap preview=raster(context.getDrawable(R.drawable.ic_notification),192);save(preview,"notification-brand-mask");preview.recycle();notification.recycle();brand.recycle();
  }
  @Test public void remoteChevronIsCenteredWithBalancedClearanceFromBothLoops() {
    Bitmap image=raster(AppIcons.drawable(context,"brand",Color.WHITE),240);
    int minX=240,maxX=-1,minY=240,maxY=-1;
    // The central opening is separate from the two rounded loops.
    for(int y=95;y<145;y++)for(int x=95;x<145;x++)if(Color.alpha(image.getPixel(x,y))>0) {
      minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
    }
    assertTrue(maxX>minX && maxY>minY);
    assertEquals("Chevron is horizontally centered",240,minX+maxX+1,1);
    assertEquals("Chevron is vertically centered",240,minY+maxY+1,1);
    int leftEdge=-1,rightEdge=240;
    for(int y=119;y<=120;y++) {
      for(int x=0;x<95;x++)if(Color.alpha(image.getPixel(x,y))>0)leftEdge=Math.max(leftEdge,x);
      for(int x=145;x<240;x++)if(Color.alpha(image.getPixel(x,y))>0)rightEdge=Math.min(rightEdge,x);
    }
    int leftGap=minX-leftEdge-1,rightGap=rightEdge-maxX-1;
    assertTrue("Loops leave a readable central opening",leftGap>=10 && rightGap>=10);
    assertEquals("Clearance is balanced on both sides",leftGap,rightGap,1);
    // Allow one high-resolution pixel for curved-edge rasterization (0.1 viewport unit).
    for(int y=0;y<240;y++)for(int x=0;x<240;x++) {
      if(x>=95 && x<145 && y>=95 && y<145)continue;
      if(Color.alpha(image.getPixel(x,y))<128)continue;
      boolean mirrored=false;
      for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
        int mx=239-x+dx,my=239-y+dy;
        if(mx>=0 && mx<240 && my>=0 && my<240 && Color.alpha(image.getPixel(mx,my))>=128)mirrored=true;
      }
      assertTrue("Loop geometry stays center symmetric",mirrored);
    }
    image.recycle();
  }
  @Test @Config(sdk=26) public void launcherAndActivityUseSameAdaptiveIconOnAndroid8() throws Exception {
    assertEquals(R.mipmap.ic_launcher,context.getApplicationInfo().icon);
    android.content.pm.ActivityInfo activity=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,MainActivity.class),0);
    assertEquals(R.mipmap.ic_launcher,activity.icon);
    assertTrue(context.getDrawable(R.mipmap.ic_launcher) instanceof AdaptiveIconDrawable);
    assertSafeForeground(((AdaptiveIconDrawable)context.getDrawable(R.mipmap.ic_launcher)).getForeground());
  }
  private void assertSafeForeground(Drawable foreground) {
    Bitmap image=raster(foreground,216);int pixels=0;
    for(int y=0;y<216;y++)for(int x=0;x<216;x++)if(Color.alpha(image.getPixel(x,y))>0){pixels++;assertTrue("Brand stays inside the 66 dp circular safe zone",Math.hypot(x+.5-108,y+.5-108)<=66);}
    assertTrue(pixels>1200);image.recycle();
  }
  @Test @Config(sdk=33) public void themedAndOrdinaryLauncherShareForegroundInsideSafeZone() throws Exception {
    AdaptiveIconDrawable launcher=(AdaptiveIconDrawable)context.getDrawable(R.mipmap.ic_launcher);assertNotNull(launcher.getMonochrome());
    Bitmap foreground=raster(launcher.getForeground(),216),monochrome=raster(launcher.getMonochrome(),216);assertTrue(foreground.sameAs(monochrome));foreground.recycle();monochrome.recycle();
    assertSafeForeground(launcher.getForeground());
    Bitmap preview=Bitmap.createBitmap(768,256,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(preview);canvas.drawColor(0xfff3f3f3);
    for(int i=0;i<3;i++) {
      canvas.save();canvas.translate(i*256,0);Path mask=new Path();
      if(i==0)mask.addCircle(128,128,112,Path.Direction.CW);else mask.addRoundRect(16,16,240,240,i==1?48:70,i==1?48:70,Path.Direction.CW);
      canvas.clipPath(mask);
      if(i<2){Drawable flat=context.getDrawable(R.drawable.ic_launcher);flat.setBounds(0,0,256,256);flat.draw(canvas);}
      else{canvas.drawColor(0xffdddddd);Drawable themed=launcher.getMonochrome().mutate();themed.setTint(0xff171717);themed.setBounds(0,0,256,256);themed.draw(canvas);}
      canvas.restore();
    }
    save(preview,"launcher-icon-masks");preview.recycle();
    Bitmap export=raster(context.getDrawable(R.drawable.ic_launcher),512);save(export,"RemoteGPT-icon-512");export.recycle();
  }
  @Test public void inlineSymbolsKeepLabelsParagraphSpansAndCopyText() {
    CharSequence original=MessageBodyView.paragraphText("✓  已选择\n\n说明 ›",12);
    CharSequence rendered=AppIcons.label(context,original,"check","chevron",Color.BLACK,18);
    assertEquals(original.toString(),rendered.toString());Spanned spans=(Spanned)rendered;
    assertEquals(2,spans.getSpans(0,rendered.length(),ReplacementSpan.class).length);
    assertEquals(1,spans.getSpans(0,rendered.length(),LineHeightSpan.class).length);
  }
  @Test public void controlIconAndPermissionTintUpdateWithoutChangingHitTarget() {
    IconView view=new IconView(context,"shield",Color.BLACK);view.layout(0,0,96,96);Bitmap first=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);view.draw(new Canvas(first));
    view.icon="bolt";view.color=0xffd99032;Bitmap updated=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);view.draw(new Canvas(updated));assertFalse(first.sameAs(updated));assertEquals(96,view.getWidth());assertEquals(96,view.getHeight());first.recycle();updated.recycle();
  }
  @Test public void nativeSelectionIconsFollowCheckedAndDisabledStates() {
    for(boolean radio:new boolean[]{true,false}) {
      android.widget.CompoundButton button=radio?new android.widget.RadioButton(context):new android.widget.CheckBox(context);
      AppIcons.selection(button,Color.BLACK,0xff707070,radio);assertNull(button.getButtonTintList());
      Bitmap unchecked=raster(button.getButtonDrawable(),48);button.setChecked(true);Bitmap checked=raster(button.getButtonDrawable(),48);
      assertTrue(button.isChecked());assertFalse(unchecked.sameAs(checked));button.setEnabled(false);Bitmap disabled=raster(button.getButtonDrawable(),48);assertFalse(checked.sameAs(disabled));
      unchecked.recycle();checked.recycle();disabled.recycle();
    }
  }
}
