package app.remotessh;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class ThumbnailLoaderTest {
  @Test public void newestVisibleImagePrecedesOldQueuedWorkAndDiscardedWorkNeverRuns() throws Exception {
    ThumbnailLoader loader=new ThumbnailLoader(1);
    CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
    List<String> order=Collections.synchronizedList(new ArrayList<>());
    try {
      ThumbnailLoader.Job first=loader.submit(active->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
      assertTrue(started.await(3,TimeUnit.SECONDS));
      ThumbnailLoader.Job older=loader.submit(active->order.add("older"));
      ThumbnailLoader.Job discarded=loader.submit(active->order.add("offscreen"));discarded.discard();
      ThumbnailLoader.Job visible=loader.submit(active->order.add("visible"));
      release.countDown();first.get(3,TimeUnit.SECONDS);visible.get(3,TimeUnit.SECONDS);older.get(3,TimeUnit.SECONDS);
      assertEquals(Arrays.asList("visible","older"),order);assertTrue(discarded.isCancelled());
    } finally {release.countDown();loader.close();}
  }

  @Test public void cancelledActiveReadStopsBeforeDecodingOrPublishingImage() throws Exception {
    ThumbnailLoader loader=new ThumbnailLoader(1);CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1);
    AtomicBoolean published=new AtomicBoolean();
    try {
      ThumbnailLoader.Job job=loader.submit(active->{
        started.countDown();
        try {while(active.getAsBoolean())Thread.sleep(10);published.set(true);}
        catch(InterruptedException expected) {Thread.currentThread().interrupt();}
        finally {finished.countDown();}
      });
      assertTrue(started.await(3,TimeUnit.SECONDS));job.discard();assertTrue(finished.await(3,TimeUnit.SECONDS));assertFalse(published.get());
    } finally {loader.close();}
  }

  @Test public void cancelledStreamDoesNotWriteLateBytesAndOrdinaryCopyKeepsBounds() throws Exception {
    AtomicBoolean active=new AtomicBoolean(true);ByteArrayOutputStream out=new ByteArrayOutputStream();
    InputStream in=new ByteArrayInputStream(new byte[32768]) {
      @Override public synchronized int read(byte[] b,int off,int len) {int n=super.read(b,off,len);active.set(false);return n;}
    };
    try {MediaFiles.copy(in,out,32768,active::get);fail("cancelled copy must stop");}catch(InterruptedIOException expected) {}
    assertEquals(0,out.size());
    MediaFiles.copy(new ByteArrayInputStream(new byte[3]),out,3);assertEquals(3,out.size());
    try {MediaFiles.copy(new ByteArrayInputStream(new byte[4]),new ByteArrayOutputStream(),3);fail("bound must remain enforced");}catch(IOException expected) {}
  }
}
