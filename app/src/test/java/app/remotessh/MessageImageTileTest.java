package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.graphics.*;
import android.widget.LinearLayout;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MessageImageTileTest {
  private ActivityController<Activity> controller;
  private RemoteApp app;
  private ThumbnailLoader worker;
  private MessageImageTile tile;
  private byte[] png;
  private final AtomicReference<ImageDialog.Reader> reader=new AtomicReference<>();

  @Before public void setup() throws Exception {
    controller=Robolectric.buildActivity(Activity.class).setup();Activity activity=controller.get();
    app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.profile.host="fixture";app.threadId="photos";app.connected=true;
    Bitmap image=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);image.eraseColor(Color.GREEN);
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle();png=bytes.toByteArray();
    worker=new ThumbnailLoader(1);
    LinearLayout row=new LinearLayout(activity);activity.setContentView(row);
    DialogUi ui=new DialogUi(activity,Color.BLACK,Color.WHITE,Color.GRAY,Color.DKGRAY,Color.GRAY);
    tile=new MessageImageTile(activity,app,ui,worker,"image","/image.png",row,false,()->true,s->{},(path,out,active)->reader.get().read(path,out,active));
    row.addView(tile.tile);tile.visible=true;
  }
  @After public void close() {
    tile.release();worker.close();controller.pause().stop().destroy();
  }
  private void await(BooleanSupplier condition) throws Exception {
    for(int i=0;i<200 && !condition.getAsBoolean();i++) {
      Thread.sleep(10);shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    }
    assertTrue("Timed out waiting for image state",condition.getAsBoolean());
  }
  private void read(ImageDialog.Reader value) {reader.set(value);}

  @Test public void transientReadFailuresRecoverAutomaticallyWithoutAnotherScrollOrTap() throws Exception {
    AtomicInteger reads=new AtomicInteger();
    read((source,out,active)->{
      if(reads.incrementAndGet()<3)throw new IOException("temporary read failure");
      MediaFiles.copy(new ByteArrayInputStream(png),out,MediaFiles.IMAGE_LIMIT,active);
    });
    tile.load();await(()->tile.loaded);
    assertEquals(3,reads.get());assertNotNull(tile.preview.getDrawable());assertFalse(tile.label.isShown());
  }

  @Test public void permanentFailureHasBoundedRetriesAndManualTapStartsAnotherAttempt() throws Exception {
    AtomicInteger reads=new AtomicInteger();
    read((source,out,active)->{
      reads.incrementAndGet();throw new IOException("file missing");
    });
    tile.load();await(()->reads.get()==3 && !tile.loading);
    for(int i=0;i<20;i++){tile.load();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));}
    assertEquals(3,reads.get());assertEquals("图片加载失败 · 点按重试",tile.label.getText().toString());
    tile.label.performClick();await(()->reads.get()>=4);assertFalse(tile.loaded);
  }

  @Test public void leavingViewportCancelsScheduledRetries() throws Exception {
    AtomicInteger reads=new AtomicInteger();
    read((source,out,active)->{
      reads.incrementAndGet();throw new IOException("temporary failure");
    });
    tile.load();await(()->reads.get()==1 && !tile.loading);
    tile.release();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
    assertEquals(1,reads.get());assertFalse(tile.loaded);assertFalse(tile.visible);
  }
  @Test public void offscreenPreviewReusesThumbnailWithoutReadingAndRejectsAnotherHost() throws Exception {
    AtomicInteger reads=new AtomicInteger();read((source,out,active)->{reads.incrementAndGet();out.write(png);});
    tile.load();await(()->tile.loaded);Bitmap cached=tile.cachedPreview();assertNotNull(cached);
    tile.release();assertFalse(tile.loaded);assertSame(cached,tile.cachedPreview());assertEquals(1,reads.get());
    app.profile.host="another-host";assertNull(tile.cachedPreview());assertFalse(cached.isRecycled());
  }

  @Test public void reconnectRejectsLateImageAndLoadsUsingTheNewConnection() throws Exception {
    CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger reads=new AtomicInteger();
    read((source,out,active)->{
      reads.incrementAndGet();started.countDown();
      while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}
      MediaFiles.copy(new ByteArrayInputStream(png),out,MediaFiles.IMAGE_LIMIT,active);
    });
    try {
      tile.load();assertTrue(started.await(3,TimeUnit.SECONDS));
      ReflectionHelpers.setField(app,"generation",app.connectionGeneration()+1);tile.refreshConnection();
      read((source,out,active)->{
        reads.incrementAndGet();MediaFiles.copy(new ByteArrayInputStream(png),out,MediaFiles.IMAGE_LIMIT,active);
      });
      tile.visible=true;tile.load();release.countDown();await(()->tile.loaded);
      assertEquals(2,reads.get());assertNotNull(tile.preview.getDrawable());assertTrue(tile.valid());
    } finally {release.countDown();}
    app.profile.host="different-server";ReflectionHelpers.setField(app,"generation",app.connectionGeneration()+1);
    tile.refreshConnection();assertFalse("Editing the same profile must not reuse another host's tile",tile.valid());
  }
}
