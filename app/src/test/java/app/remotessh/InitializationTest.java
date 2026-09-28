package app.remotessh;

import static org.junit.Assert.*;

import android.os.Looper;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class InitializationTest {
  @Test
  public void initializedNotificationNeverWritesOnAndroidMainThread() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    AtomicBoolean wroteOnMain = new AtomicBoolean();
    CountDownLatch flushed = new CountDownLatch(1);
    ByteArrayOutputStream output =
        new ByteArrayOutputStream() {
          @Override
          public void flush() {
            if (Looper.myLooper() == Looper.getMainLooper()) wroteOnMain.set(true);
            flushed.countDown();
          }
        };
    RpcClient rpc =
        new RpcClient(
            new ByteArrayInputStream(new byte[0]),
            output,
            new RpcClient.Events() {
              public void onEvent(JSONObject event) {}

              public void onClosed(Exception error) {}
            });
    Field field = RemoteApp.class.getDeclaredField("rpc");
    field.setAccessible(true);
    field.set(app, rpc);
    app.connecting = true;
    app.completeInitialization(rpc, null, 0);
    assertTrue(flushed.await(3, TimeUnit.SECONDS));
    assertFalse("Handshake writes must stay off the UI thread", wroteOnMain.get());
    assertTrue(output.toString("UTF-8").contains("initialized"));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!app.connected && System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(5);
    }
    assertTrue(app.connected);
    assertFalse(app.connecting);
    app.disconnect();
  }

  @Test
  public void cancelledConnectionCannotBeMarkedConnectedByLateHandshake() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    CountDownLatch sent = new CountDownLatch(1);
    ByteArrayOutputStream out =
        new ByteArrayOutputStream() {
          @Override
          public void flush() {
            sent.countDown();
          }
        };
    RpcClient rpc =
        new RpcClient(
            new ByteArrayInputStream(new byte[0]),
            out,
            new RpcClient.Events() {
              public void onEvent(JSONObject e) {}

              public void onClosed(Exception e) {}
            });
    app.disconnect();
    app.completeInitialization(rpc, null, 0);
    assertTrue(sent.await(3, TimeUnit.SECONDS));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertFalse(app.connected);
    rpc.close();
  }
}
