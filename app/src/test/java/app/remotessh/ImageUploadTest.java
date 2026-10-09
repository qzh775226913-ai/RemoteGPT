package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Uses only synthetic image providers and the explicitly enabled loopback SSH fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageUploadTest {
  private RemoteApp app;
  private SshTransport transport;
  private RpcClient rpc;

  @Before public void connect() throws Exception {
    Assume.assumeTrue(Boolean.getBoolean("remote.ssh.fixture"));
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    Profile profile = new Profile(); profile.persistent = false; profile.host = "127.0.0.1";
    profile.port = 22228; profile.user = "fixture"; profile.password = "test-only"; profile.command = "fixture-server";
    transport = new SshTransport();
    Map<String, String> pins = new HashMap<>();
    rpc = transport.connect(profile, new HostPins() {
      public String getPin(String target) { return pins.get(target); }
      public void rememberPin(String target, String pin) { pins.put(target, pin); }
    }, (host, fingerprint) -> true, new RpcClient.Events() {
      public void onEvent(JSONObject event) {} public void onClosed(Exception error) {}
    });
    Field field = RemoteApp.class.getDeclaredField("transport"); field.setAccessible(true); field.set(app, transport);
    app.profile = profile; app.threadId = "pictures"; app.connected = true;
  }

  @After public void close() {
    if (rpc != null) rpc.close();
    if (app != null) app.disconnect();
    if (transport != null) transport.close();
  }

  private byte[] png(int color) {
    Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color);
    ByteArrayOutputStream out = new ByteArrayOutputStream(); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    bitmap.recycle(); return out.toByteArray();
  }

  private Uri image(String name, byte[] bytes) {
    Uri uri = Uri.parse("content://fixture.album/" + name);
    shadowOf(app.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(bytes));
    return uri;
  }

  private void waitUntil(BooleanSupplier condition) throws Exception {
    for (int i = 0; i < 1000 && !condition.getAsBoolean(); i++) {
      shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(10);
    }
    assertTrue("Image operation did not finish: " + app.error, condition.getAsBoolean());
  }

  private void verifyUploaded(int index, byte[] bytes) throws Exception {
    ByteArrayOutputStream downloaded = new ByteArrayOutputStream(); transport.download(app.draftImages.get(index), downloaded, MediaFiles.IMAGE_LIMIT);
    assertArrayEquals(bytes, downloaded.toByteArray()); assertNotNull(app.draftImagePreviews.get(app.draftImages.get(index)));
  }

  @Test public void uploadsMultipleImagesInOrderWithoutDuplicates() throws Exception {
    byte[] red = png(Color.RED), green = png(Color.GREEN), blue = png(Color.BLUE);
    Uri first = image("red", red), second = image("green", green), third = image("blue", blue);
    app.attachImages(Arrays.asList(first, second, first, third));
    assertTrue(app.attaching); assertEquals(3, app.imageUploadCount);
    waitUntil(() -> !app.attaching);
    assertEquals(3, app.draftImages.size()); assertEquals(3, app.draftImagePreviews.size());
    verifyUploaded(0, red); verifyUploaded(1, green); verifyUploaded(2, blue);
    assertEquals(0, app.imageUploadCount); assertNull(app.pendingImagePreview);
  }

  @Test public void oneUnreadableImageDoesNotDiscardOrStopTheRemainingImages() throws Exception {
    byte[] red = png(Color.RED), blue = png(Color.BLUE);
    app.attachImages(Arrays.asList(image("red", red), image("broken", new byte[] {1, 2, 3}), image("blue", blue)));
    waitUntil(() -> !app.attaching);
    assertEquals(2, app.draftImages.size()); verifyUploaded(0, red); verifyUploaded(1, blue);
    assertTrue(app.error.contains("第 2 张")); assertTrue(app.error.contains("成功的图片已保留"));
  }

  @Test public void keepsBatchLockedUntilLastImageAndStopsLateResultsAfterDisconnect() throws Exception {
    byte[] red = png(Color.RED);
    Uri first = image("first", red), slow = Uri.parse("content://fixture.album/slow");
    CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1), closed = new CountDownLatch(1);
    shadowOf(app.getContentResolver()).registerInputStream(slow, new ByteArrayInputStream(red) {
      @Override public synchronized int read(byte[] buffer, int offset, int length) {
        opened.countDown();
        try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return -1; }
        return super.read(buffer, offset, length);
      }
      @Override public void close() { closed.countDown(); }
    });
    try {
      app.attachImages(Arrays.asList(first, slow));
      waitUntil(() -> opened.getCount() == 0 && app.draftImages.size() == 1 && app.imageUploadIndex == 2);
      assertTrue(app.attaching); assertEquals(2, app.imageUploadIndex);
      app.newChat(); assertEquals("pictures", app.threadId);
      app.send("wait for both images"); assertTrue(app.items.isEmpty()); assertFalse(app.busy);
      app.disconnect(); release.countDown(); waitUntil(() -> closed.getCount() == 0);
      Thread.sleep(100); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      assertEquals(1, app.draftImages.size()); assertFalse(app.attaching); assertNull(app.pendingImagePreview);
      assertEquals(0, app.imageUploadCount); assertFalse(app.error.contains("图片上传失败"));
    } finally { release.countDown(); }
  }

  @Test public void oversizedSelectionDoesNotUploadOrDropExistingDraftImages() {
    app.draftImages.add("/existing.png");
    app.attachImages(Arrays.asList(Uri.parse("content://album/1"), Uri.parse("content://album/2"), Uri.parse("content://album/3"), Uri.parse("content://album/4")));
    assertFalse(app.attaching); assertEquals(Collections.singletonList("/existing.png"), app.draftImages);
    assertTrue(app.error.contains("还能添加 3 张"));
  }
}
