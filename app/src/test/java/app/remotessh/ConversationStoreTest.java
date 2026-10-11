package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConversationStoreTest {
  private RemoteApp app;

  @Before public void setUp() {
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile();
    app.profile.cwd = "/selected";
    app.profile.lastThread = "selected";
    app.threadId = "selected";
    app.draftText = "selected draft";
    app.permissionMode = PermissionMode.READ_ONLY;
    app.changed();
  }

  @Test public void nestedBackgroundFailureRestoresSelectionBeforeNotifying() {
    int[] notifications = {0};
    app.observer = () -> {
      notifications[0]++;
      assertEquals("selected", app.threadId);
      assertEquals("selected draft", app.draftText);
      assertEquals("/selected", app.profile.cwd);
    };
    RuntimeException expected = new RuntimeException("synthetic failure");
    try {
      app.inConversation("background", () -> {
        app.draftText = "background draft";
        app.profile.cwd = "/temporary";
        app.changed();
        app.inConversation("third", () -> {
          app.draftText = "third draft";
          app.changed();
          throw expected;
        });
      });
      fail("The original failure must propagate");
    } catch (RuntimeException actual) { assertSame(expected, actual); }
    assertEquals(1, notifications[0]);
    assertEquals("selected", app.profile.lastThread);
    assertEquals("background draft", app.conversationStore.get("background").draft);
    assertEquals("third draft", app.conversationStore.get("third").draft);
    // Later updates still publish; no scope may be left open after an exception.
    app.changed();
    assertEquals(2, notifications[0]);
  }

  @Test public void backgroundSettingsAndAttachmentsNeverReplaceSelectedDraft() {
    Bitmap preview = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    FileAttachment file = new FileAttachment("draft.pdf", "/selected/draft.pdf", 10);
    app.draftImages.add("/selected/image.png");
    app.draftImagePreviews.put("/selected/image.png", preview);
    app.draftFiles.add(file);
    app.inConversation("background", () -> {
      app.model = "background-model";
      app.reasoningEffort = "high";
      app.permissionMode = PermissionMode.FULL;
      app.serviceTier = "fast";
      app.draftFiles.add(new FileAttachment("other.pdf", "/background/other.pdf", 20));
      app.changed();
    });
    assertEquals("", app.model);
    assertEquals("", app.serviceTier);
    assertEquals(PermissionMode.READ_ONLY, app.permissionMode);
    assertSame(file, app.draftFiles.get(0));
    assertSame(preview, app.draftImagePreviews.get("/selected/image.png"));
    app.inConversation("background", () -> {
      assertEquals("background-model", app.model);
      assertEquals("high", app.reasoningEffort);
      assertEquals("fast", app.serviceTier);
      assertEquals(1, app.draftFiles.size());
      assertTrue(app.draftImages.isEmpty());
    });
  }

  @Test public void workerThreadCannotSwitchConversationState() throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      try { app.inConversation("background", () -> app.draftText = "invalid"); }
      catch (Throwable error) { failure.set(error); }
    });
    worker.start(); worker.join(2000);
    assertFalse(worker.isAlive());
    assertTrue(failure.get() instanceof IllegalStateException);
    assertEquals("selected", app.threadId);
    assertEquals("selected draft", app.draftText);
  }
}
