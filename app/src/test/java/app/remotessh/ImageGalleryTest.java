package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w411dp-h891dp-xhdpi",shadows=RegionDecoderShadow.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageGalleryTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private ImageDialog viewer;
  private final List<String> gallery=Arrays.asList("a","b","c");
  @Before public void open(){controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();}
  @After public void close() throws Exception {
    if(viewer!=null){
      viewer.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();
      ThreadPoolExecutor worker=ReflectionHelpers.getField(viewer,"worker");assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    controller.pause().stop().destroy();
  }
  private byte[] png(int width) throws Exception {
    Bitmap bitmap=Bitmap.createBitmap(width,40,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);
    ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();
  }
  private void await(BooleanSupplier ready) throws Exception {
    for(int i=0;i<500;i++){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(15));if(ready.getAsBoolean())return;Thread.sleep(10);}fail("Gallery did not settle");
  }
  private ImageDocument document(){return ReflectionHelpers.getField(viewer.image,"document");}
  private TextView status(){return ReflectionHelpers.getField(viewer,"status");}
  private void loaded(String source,int width) throws Exception {await(()->source.equals(viewer.currentSource()) && document()!=null && document().width==width);}
  private void touch(long down,long time,int action,float x) {
    MotionEvent event=MotionEvent.obtain(down,time,action,x,200,0);
    try{viewer.image.onTouchEvent(event);}finally{event.recycle();}
  }
  private void swipe(boolean next) {
    viewer.image.layout(0,0,600,400);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    long t=SystemClock.uptimeMillis();float start=next?500:100,end=next?100:500;
    touch(t,t,MotionEvent.ACTION_DOWN,start);touch(t,t+100,MotionEvent.ACTION_MOVE,end);touch(t,t+150,MotionEvent.ACTION_UP,end);
  }
  @Test public void swipePagesRespectBoundsAndSaveCurrentSourceWithoutRecyclingThumbnail() throws Exception {
    byte[] a=png(80),b=png(120),c=png(160);AtomicInteger reads=new AtomicInteger();AtomicReference<String> saved=new AtomicReference<>();
    Bitmap thumbnail=Bitmap.createBitmap(12,4,Bitmap.Config.ARGB_8888);List<String> mutable=new ArrayList<>(gallery);
    viewer=new ImageDialog(activity,true,"b",thumbnail,mutable,path->null,(source,out,active)->{reads.incrementAndGet();out.write(source.equals("a")?a:source.equals("b")?b:c);},()->true,saved::set);
    try {
      mutable.clear();loaded("b",120);ImageDocument old=document();
      swipe(true);loaded("c",160);assertFalse(old.overview.isRecycled());assertTrue(old.file.exists());
      assertTrue(status().getText().toString().startsWith("3 / 3"));int before=reads.get();
      swipe(true);assertEquals("c",viewer.currentSource());assertEquals(before,reads.get());
      swipe(false);loaded("b",120);swipe(false);loaded("a",80);
      swipe(false);assertEquals("a",viewer.currentSource());
      swipe(true);loaded("b",120);swipe(true);loaded("c",160);
      assertEquals("Revisiting these three images must not download them again",3,reads.get());
      viewer.dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("c",saved.get());
      await(()->old.overview.isRecycled() && !old.file.exists());
      assertFalse(thumbnail.isRecycled());
    }finally{viewer.dismiss();thumbnail.recycle();}
  }
  @Test public void fastPagingCancelsQueuedPageAndDiscardsLateOriginal() throws Exception {
    byte[] a=png(80),c=png(160);CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1),oldFinished=new CountDownLatch(1);
    List<String> reads=Collections.synchronizedList(new ArrayList<>());
    viewer=new ImageDialog(activity,true,"a",null,gallery,path->null,(source,out,active)->{
      reads.add(source);
      if(source.equals("a")){
        started.countDown();boolean done=false;
        try {
          while(!done){try{done=finish.await(5,TimeUnit.SECONDS);if(!done)throw new IOException("timed out");}catch(InterruptedException ignored){}}
          out.write(a); // Deliberately ignore cancellation, as a late I/O response may do.
        } finally {oldFinished.countDown();}
      }else out.write(c);
    },()->true,source->{});
    try {
      assertTrue(started.await(5,TimeUnit.SECONDS));viewer.page(1);viewer.page(1);loaded("c",160);
      assertEquals("A blocked obsolete read must not hold the current page",1,oldFinished.getCount());
      assertTrue(reads.contains("c"));finish.countDown();
      await(()->activity.getCacheDir().listFiles((dir,name)->name.startsWith("viewer-")).length==1);
      viewer.dismiss();await(()->activity.getCacheDir().listFiles((dir,name)->name.startsWith("viewer-")).length==0);
    }finally{finish.countDown();viewer.dismiss();}
  }
  @Test public void failedPageRetriesThatPageAndInvalidOwnerCannotSaveOrNavigate() throws Exception {
    byte[] bytes=png(120);AtomicInteger attempts=new AtomicInteger();AtomicBoolean valid=new AtomicBoolean(true);AtomicReference<String> saved=new AtomicReference<>();
    viewer=new ImageDialog(activity,true,"a",null,Arrays.asList("a","b"),path->null,(source,out,active)->{
      if(source.equals("b") && attempts.incrementAndGet()==1)throw new IOException("offline");out.write(bytes);
    },valid::get,saved::set);
    loaded("a",120);viewer.page(1);await(()->status().getText().toString().contains("失败"));
    assertTrue(status().getText().toString().startsWith("2 / 2"));status().performClick();loaded("b",120);assertEquals(2,attempts.get());
    valid.set(false);viewer.page(-1);assertEquals("b",viewer.currentSource());
    viewer.dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(saved.get());
  }
  @Test public void recentImagesUseBoundedLruAndReleaseEveryCachedFileOnClose() throws Exception {
    Map<String,AtomicInteger> reads=new HashMap<>();for(String source:Arrays.asList("a","b","c","d"))reads.put(source,new AtomicInteger());
    byte[] bytes=png(80);
    viewer=new ImageDialog(activity,true,"a",null,Arrays.asList("a","b","c","d"),path->null,
        (source,out,active)->{reads.get(source).incrementAndGet();out.write(bytes);},()->true,source->{});
    loaded("a",80);ImageDocument first=document();
    viewer.page(1);loaded("b",80);viewer.page(1);loaded("c",80);
    viewer.page(-1);loaded("b",80);viewer.page(1);loaded("c",80);assertEquals(1,reads.get("b").get());
    viewer.page(1);loaded("d",80);await(()->first.overview.isRecycled() && !first.file.exists());
    viewer.page(-1);loaded("c",80);viewer.page(-1);loaded("b",80);assertEquals(1,reads.get("b").get());
    viewer.page(-1);loaded("a",80);assertEquals(2,reads.get("a").get());
    viewer.dismiss();await(()->activity.getCacheDir().listFiles((dir,name)->name.startsWith("viewer-")).length==0);
  }
  @Test public void decodedImageBudgetEvictsLargeImagesBeforeCountLimit() throws Exception {
    Bitmap bitmap=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);
    ByteArrayOutputStream data=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,data);bitmap.recycle();
    byte[] bytes=data.toByteArray();AtomicInteger reads=new AtomicInteger();
    viewer=new ImageDialog(activity,true,"a",null,gallery,path->null,(source,out,active)->{reads.incrementAndGet();out.write(bytes);},()->true,source->{});
    loaded("a",2048);ImageDocument first=document();viewer.page(1);loaded("b",2048);viewer.page(1);loaded("c",2048);
    await(()->first.overview.isRecycled());viewer.page(-1);loaded("b",2048);viewer.page(-1);loaded("a",2048);assertEquals(4,reads.get());
  }
  @Test public void originalFileBudgetEvictsBeforeCountLimit() throws Exception {
    byte[] bytes=png(80),padding=new byte[64*1024];AtomicInteger reads=new AtomicInteger();
    viewer=new ImageDialog(activity,true,"a",null,gallery,path->null,(source,out,active)->{
      reads.incrementAndGet();out.write(bytes);
      for(int i=0;i<240;i++){MediaFiles.check(active);out.write(padding);}
    },()->true,source->{});
    loaded("a",80);ImageDocument first=document();viewer.page(1);loaded("b",80);viewer.page(1);loaded("c",80);
    await(()->first.overview.isRecycled() && !first.file.exists());
    viewer.page(-1);loaded("b",80);viewer.page(-1);loaded("a",80);assertEquals(4,reads.get());
  }
  @Test public void galleryCanonicalizesAndDeduplicatesInConversationOrder() {
    ChatItem first=new ChatItem("one","userMessage","![A](/photos/a.png) ![B](next.png)");first.images.add("sandbox:/photos/a.png");
    ChatItem second=new ChatItem("two","agentMessage","![B](next.png)");second.images.add("https://example.test/image.png");
    List<String> result=MediaFiles.imageGallery(Arrays.asList(first,second),"/work");
    assertEquals(Arrays.asList("/photos/a.png","/work/next.png","https://example.test/image.png"),result);
    first.images.clear();assertEquals(3,result.size());
  }
  @Test public void conversationPreviewIncludesOtherMessagesAndClosesOnThreadChange() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.conversationStore.select("gallery");
    String a="data:image/png;base64,"+android.util.Base64.encodeToString(png(80),android.util.Base64.NO_WRAP);
    String b="data:image/png;base64,"+android.util.Base64.encodeToString(png(120),android.util.Base64.NO_WRAP);
    ChatItem one=new ChatItem("one","userMessage","");one.images.add(a);ChatItem two=new ChatItem("two","agentMessage","");two.images.add(b);
    app.items.add(one);app.items.add(two);app.changed();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    ReflectionHelpers.callInstanceMethod(activity,"openConversationImage",ReflectionHelpers.ClassParameter.from(String.class,b));
    viewer=ReflectionHelpers.getField(activity,"imageDialog");loaded(b,120);viewer.page(-1);loaded(a,80);
    viewer.dismiss();
    ReflectionHelpers.callInstanceMethod(activity,"openImage",ReflectionHelpers.ClassParameter.from(String.class,b));
    viewer=ReflectionHelpers.getField(activity,"imageDialog");loaded(b,120);viewer.page(-1);assertEquals(b,viewer.currentSource());
    viewer.dismiss();
    ReflectionHelpers.callInstanceMethod(activity,"openConversationImage",ReflectionHelpers.ClassParameter.from(String.class,b));
    viewer=ReflectionHelpers.getField(activity,"imageDialog");loaded(b,120);
    ImageDocument original=document();ReflectionHelpers.setField(app,"generation",app.connectionGeneration()+1);
    app.changed();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(viewer.dialog.isShowing());await(()->original.overview.isRecycled());
    ReflectionHelpers.callInstanceMethod(activity,"openConversationImage",ReflectionHelpers.ClassParameter.from(String.class,b));
    viewer=ReflectionHelpers.getField(activity,"imageDialog");loaded(b,120);
    app.conversationStore.select("other");app.changed();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(viewer.dialog.isShowing());
  }
}
