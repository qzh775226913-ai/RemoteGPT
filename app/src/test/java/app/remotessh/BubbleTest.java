package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BubbleTest {
  @Test public void servicePublishesConversationBubbleAndUpdatesApprovalSummary() {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true; app.busy = true; app.threadId = "thread"; app.threadTitle = "测试对话";
    var service = Robolectric.buildService(ConnectionService.class).create();
    NotificationManager manager = app.getSystemService(NotificationManager.class);
    var notifications = Shadows.shadowOf(manager);
    Notification bubble = notifications.getNotification(2);
    assertNotNull(bubble.getBubbleMetadata());
    assertEquals("conversation--thread", bubble.getShortcutId());
    assertEquals(Notification.MessagingStyle.class.getName(), bubble.extras.getString(Notification.EXTRA_TEMPLATE));
    app.approvals.add(Json.obj("id", "approval", "method", "item/commandExecution/requestApproval",
        "params", Json.obj("command", "ls /project", "threadId", "thread")));
    app.changed(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    assertTrue(notifications.getNotification(1).extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("ls /project"));
    service.destroy();
    assertNull(notifications.getNotification(2));
    assertTrue(app.stateListeners.isEmpty());
  }

  @Test @Config(sdk = 28) public void olderAndroidUsesOrdinaryNotification() {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true; app.threadId = "thread";
    var service = Robolectric.buildService(ConnectionService.class).create();
    assertNotNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(2));
    service.destroy();
  }

  @Test public void expandedBubbleRequiresTapAndDropsResolvedApproval() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.connected = true; app.threadId = "thread";
    JSONObject approval = Json.obj("id", "approval", "method", "item/commandExecution/requestApproval",
        "params", Json.obj("command", "ls /project", "threadId", "thread"));
    app.approvals.add(approval);
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    RpcClient rpc = new RpcClient(new ByteArrayInputStream(new byte[0]), sent, new RpcClient.Events() {
      public void onEvent(JSONObject e) {} public void onClosed(Exception e) {}
    });
    var field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, rpc);
    var controller = Robolectric.buildActivity(BubbleActivity.class).setup();
    try {
      View root = controller.get().findViewById(android.R.id.content);
      Button allow = find(root, "允许一次");
      assertNotNull(allow); assertEquals(0, sent.size());
      root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1040, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 720, 1040);
      android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(720, 1040, android.graphics.Bitmap.Config.ARGB_8888);
      root.draw(new android.graphics.Canvas(bitmap));
      new File("build/screenshots").mkdirs();
      try (FileOutputStream output = new FileOutputStream("build/screenshots/bubble-approval.png")) {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
      }
      bitmap.recycle();
      // A stale button may still receive an accessibility action after resolution.
      app.approvals.clear(); app.changed(); allow.performClick();
      assertEquals(0, sent.size());
      app.approvals.add(approval); app.changed();
      find(root, "拒绝").performClick();
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
      while (sent.size() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
      JSONObject answer = new JSONObject(sent.toString("UTF-8"));
      assertEquals("approval", answer.getString("id"));
      assertEquals("decline", answer.getJSONObject("result").getString("decision"));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
    } finally { controller.pause().stop().destroy(); rpc.close(); field.set(app, null); }
  }
  private Button find(View view, String label) {
    if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
      Button found = find(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
    }
    return null;
  }
}
