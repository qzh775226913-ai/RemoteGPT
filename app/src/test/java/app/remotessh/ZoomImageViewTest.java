package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w411dp-h891dp-xhdpi",shadows=RegionDecoderShadow.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZoomImageViewTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private ExecutorService worker;
  private ZoomImageView view;
  private final List<ImageDocument> documents=new ArrayList<>();
  @Before public void open(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();worker=Executors.newSingleThreadExecutor();}
  @After public void close() throws Exception {
    if(view!=null)view.dispose();worker.shutdown();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));
    for(ImageDocument document:documents)document.close();controller.pause().stop().destroy();
  }
  private byte[] image(int width,int height,int color) throws Exception {
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawColor(color);
    ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));bitmap.recycle();return out.toByteArray();
  }
  private ImageDocument document(byte[] bytes) throws Exception {
    File file=File.createTempFile("test-image-",".png",activity.getCacheDir());try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
    ImageDocument document=new ImageDocument(file);documents.add(document);return document;
  }
  private void await(BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+5_000_000_000L;
    while(!condition.getAsBoolean() && System.nanoTime()<end){Thread.sleep(10);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(15));}
    assertTrue(condition.getAsBoolean());
  }
  private void gesture(long down,long time,int action,float... coords) {
    int count=coords.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[count];
    for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;points[i]=new MotionEvent.PointerCoords();points[i].x=coords[i*2];points[i].y=coords[i*2+1];points[i].pressure=1;points[i].size=1;}
    MotionEvent event=MotionEvent.obtain(down,time,action,count,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);view.onTouchEvent(event);event.recycle();
  }
  private void attach(ImageDocument document,AtomicInteger page) {
    view=new ZoomImageView(activity,worker,page::set);FrameLayout root=new FrameLayout(activity);root.addView(view);root.layout(0,0,1000,800);view.layout(0,0,1000,800);view.setDocument(document);
  }
  @Test public void pinchKeepsFocusAndDraggingStaysInBoundsWithoutChangingPages() throws Exception {
    AtomicInteger page=new AtomicInteger();attach(document(image(1000,800,Color.BLUE)),page);long down=SystemClock.uptimeMillis();
    gesture(down,down,MotionEvent.ACTION_DOWN,350,400);
    gesture(down,down+10,MotionEvent.ACTION_POINTER_DOWN|(1<<8),350,400,650,400);
    for(int i=1;i<=8;i++)gesture(down,down+10+i*20,MotionEvent.ACTION_MOVE,350-i*20,400,650+i*20,400);
    assertTrue(view.zoomScale()>view.fitScale()*1.4f);RectF bounds=view.displayedBounds();assertEquals(500,bounds.centerX(),1);assertEquals(400,bounds.centerY(),1);
    gesture(down,down+190,MotionEvent.ACTION_POINTER_UP|(1<<8),190,400,810,400);
    gesture(down,down+220,MotionEvent.ACTION_MOVE,950,790);gesture(down,down+250,MotionEvent.ACTION_UP,950,790);
    bounds=view.displayedBounds();assertTrue(bounds.left<=0 && bounds.top<=0);assertTrue(bounds.right>=1000 && bounds.bottom>=800);assertEquals(0,page.get());
    view.reset();assertFalse(view.isZoomed());assertEquals(new RectF(0,0,1000,800),view.displayedBounds());
  }
  @Test public void doubleTapTogglesZoomAndOnlyFitImageSwipeChangesPage() throws Exception {
    AtomicInteger page=new AtomicInteger();attach(document(image(1000,800,Color.BLUE)),page);long t=SystemClock.uptimeMillis();
    gesture(t,t,MotionEvent.ACTION_DOWN,300,300);gesture(t,t+30,MotionEvent.ACTION_UP,300,300);
    gesture(t+100,t+100,MotionEvent.ACTION_DOWN,300,300);gesture(t+100,t+130,MotionEvent.ACTION_UP,300,300);assertTrue(view.isZoomed());
    gesture(t+800,t+800,MotionEvent.ACTION_DOWN,850,400);gesture(t+800,t+900,MotionEvent.ACTION_MOVE,150,400);gesture(t+800,t+950,MotionEvent.ACTION_UP,150,400);assertEquals(0,page.get());
    gesture(t+1500,t+1500,MotionEvent.ACTION_DOWN,300,300);gesture(t+1500,t+1530,MotionEvent.ACTION_UP,300,300);
    gesture(t+1600,t+1600,MotionEvent.ACTION_DOWN,300,300);gesture(t+1600,t+1630,MotionEvent.ACTION_UP,300,300);assertFalse(view.isZoomed());
    gesture(t+2300,t+2300,MotionEvent.ACTION_DOWN,850,400);gesture(t+2300,t+2400,MotionEvent.ACTION_MOVE,150,400);gesture(t+2300,t+2450,MotionEvent.ACTION_UP,150,400);assertEquals(1,page.get());
  }
  @Test public void largeOriginalDecodesBoundedOverviewAndFullResolutionVisibleRegion() throws Exception {
    ImageDocument source=document(image(4096,2048,Color.BLUE));assertTrue(source.hasRegions());assertTrue((long)source.overview.getWidth()*source.overview.getHeight()<=ImageDocument.OVERVIEW_PIXELS);
    ImageDocument.Region region=source.region(1,-1500,-600,1000,800);assertEquals(1,region.sample);assertTrue(region.bounds.width()<source.width);
    Bitmap tile=source.decode(region);assertNotNull(tile);assertEquals(region.bounds.width(),tile.getWidth());assertEquals(Color.BLUE,tile.getPixel(100,100));assertTrue((long)tile.getWidth()*tile.getHeight()<=ImageDocument.TILE_PIXELS);tile.recycle();
    region=source.region(.1f,0,0,2000,2000);tile=source.decode(region);assertTrue((long)tile.getWidth()*tile.getHeight()<=ImageDocument.TILE_PIXELS);tile.recycle();
  }
  @Test public void longNarrowImageKeepsOverviewWithinTextureSizeAndReadsOriginalDetail() throws Exception {
    ImageDocument source=document(image(8,20000,Color.GREEN));
    assertTrue(source.overview.getHeight()<=ImageDocument.MAX_BITMAP_SIDE);
    assertTrue(source.overview.getWidth()<=ImageDocument.MAX_BITMAP_SIDE);
    ImageDocument.Region region=source.region(1,496,-12000,1000,800);
    assertEquals(1,region.sample);assertTrue(region.bounds.top>0 && region.bounds.bottom<source.height);
    Bitmap detail=source.decode(region);assertEquals(8,detail.getWidth());assertEquals(Color.GREEN,detail.getPixel(0,100));detail.recycle();
  }
  @Test public void overviewReplacementKeepsZoomFocusAndLoadsFineDetailWithoutRecyclingSharedThumbnail() throws Exception {
    ImageDocument source=document(image(4096,2048,Color.BLUE));view=new ZoomImageView(activity,worker,delta->fail("Loading detail must not switch pages"));
    FrameLayout root=new FrameLayout(activity);root.addView(view);activity.setContentView(root);root.layout(0,0,1000,800);view.layout(0,0,1000,800);
    Bitmap thumbnail=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);view.setPlaceholder(thumbnail);view.zoom(view.fitScale()*3,700,400);
    RectF old=view.displayedBounds();float center=(500-old.left)/old.width();view.setDocument(source);
    assertEquals(3,view.zoomScale()/view.fitScale(),.01);RectF next=view.displayedBounds();assertEquals(center,(500-next.left)/next.width(),.01);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));await(()->ReflectionHelpers.getField(view,"detail")!=null);
    Bitmap detail=ReflectionHelpers.getField(view,"detail");Rect bounds=ReflectionHelpers.getField(view,"detailBounds");
    assertEquals(bounds.width(),detail.getWidth());assertEquals(Color.BLUE,detail.getPixel(100,100));
    view.dispose();assertTrue(detail.isRecycled());assertFalse(thumbnail.isRecycled());thumbnail.recycle();
  }
  @Test public void exifOrientationAlsoMapsVisibleRegionBackToOriginal() throws Exception {
    Bitmap bitmap=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawColor(Color.RED);
    File file=File.createTempFile("test-image-",".jpg",activity.getCacheDir());try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}bitmap.recycle();
    for(int orientation=1;orientation<=8;orientation++) {
      ExifInterface exif=new ExifInterface(file.getPath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,Integer.toString(orientation));exif.saveAttributes();
      File copy=File.createTempFile("test-image-",".jpg",activity.getCacheDir());java.nio.file.Files.copy(file.toPath(),copy.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      ImageDocument source=new ImageDocument(copy);documents.add(source);assertEquals(orientation>=5?150:300,source.displayWidth);
      assertEquals(orientation>=5?300:150,source.displayHeight);RectF bounds=new RectF(0,0,300,150);source.orientation.mapRect(bounds);
      assertEquals(new RectF(0,0,source.displayWidth,source.displayHeight),bounds);source.inverseOrientation.mapRect(bounds);assertEquals(new RectF(0,0,300,150),bounds);
      ImageDocument.Region region=source.region(2,-40,-80,100,100);assertNotNull(region);assertTrue(new Rect(0,0,300,150).contains(region.bounds));
    }
    file.delete();
  }
  @Test public void imageDialogRetriesAndKeepsBorrowedThumbnailOnClose() throws Exception {
    byte[] bytes=image(900,450,Color.BLUE);AtomicInteger reads=new AtomicInteger();Bitmap thumbnail=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
    ImageDialog dialog=new ImageDialog(activity,true,"image",thumbnail,(source,out,active)->{if(reads.incrementAndGet()==1)throw new IOException("offline");out.write(bytes);},()->true,source->{});
    try {
      assertSame(thumbnail,ReflectionHelpers.getField(dialog.image,"placeholder"));
      await(()->find(dialog.dialog.getWindow().getDecorView(),"图片加载失败 · 点按重试")!=null);
      find(dialog.dialog.getWindow().getDecorView(),"图片加载失败 · 点按重试").performClick();
      await(()->ReflectionHelpers.getField(dialog.image,"document")!=null);
      ImageDocument source=ReflectionHelpers.getField(dialog.image,"document");assertEquals(900,source.width);assertEquals(2,reads.get());
      dialog.dismiss();await(()->source.overview.isRecycled() && !source.file.exists());assertFalse(thumbnail.isRecycled());
    }finally{dialog.dismiss();thumbnail.recycle();}
  }
  @Test @Config(sdk=26) public void pendingImagePreviewSupportsZoomWithoutSaveOrUpload() throws Exception {
    Bitmap thumbnail=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888);
    ImageDialog dialog=new ImageDialog(activity,true,null,thumbnail,(source,out,active)->fail("Preview must not start a download"),()->true,source->fail("No remote file to save"));
    try {
      capture(dialog.image,"zoom-image-preview");
      dialog.image.zoom(dialog.image.fitScale()*3,100,100);assertTrue(dialog.image.isZoomed());
      assertNull(find(dialog.dialog.getWindow().getDecorView(),"保存图片"));
      dialog.dismiss();assertFalse(thumbnail.isRecycled());
    }finally{dialog.dismiss();thumbnail.recycle();}
  }
  @Test public void changedConnectionBlocksSavingAndDismissCancelsOriginalRead() throws Exception {
    AtomicBoolean valid=new AtomicBoolean(true);AtomicReference<String> saved=new AtomicReference<>();
    CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
    ImageDialog dialog=new ImageDialog(activity,true,"image",null,(source,out,active)->{
      started.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));MediaFiles.check(active);
      fail("Dismissed viewer must cancel before writing the original");
    },valid::get,saved::set);
    try {
      assertTrue(started.await(5,TimeUnit.SECONDS));valid.set(false);
      dialog.dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();assertNull(saved.get());
      finish.countDown();await(()->activity.getCacheDir().listFiles((folder,name)->name.startsWith("viewer-")).length==0);
      assertNull(ReflectionHelpers.getField(dialog.image,"document"));
    }finally{finish.countDown();dialog.dismiss();}
  }
  private View find(View root,String label){if(root instanceof TextView && label.contentEquals(((TextView)root).getText()))return root;if(root.getContentDescription()!=null && label.contentEquals(root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View value=find(((ViewGroup)root).getChildAt(i),label);if(value!=null)return value;}return null;}
  private void capture(View root,String name) throws Exception {
    root.measure(View.MeasureSpec.makeMeasureSpec(822,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1782,View.MeasureSpec.EXACTLY));root.layout(0,0,822,1782);
    Bitmap bitmap=Bitmap.createBitmap(822,1782,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File folder=new File("build/screenshots");folder.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }
}
