package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.content.pm.*;
import android.os.Looper;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en")
public class NotificationLanguageTest {
  @Test public void switchingLanguageUpdatesCachedConversationLabelAndVisibleNotifications() {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="task";app.turnId="turn";app.busy=true;app.changed();
    app.language.select("zh-Hant");
    var service=Robolectric.buildService(ConnectionService.class).create();
    try {
      ShortcutManager shortcuts=app.getSystemService(ShortcutManager.class);
      ShortcutInfo original=shortcuts.getDynamicShortcuts().get(0);
      String shortcutId=original.getId();
      assertEquals("運行中",original.getShortLabel().toString());
      app.language.select("en");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
      ShortcutInfo updated=shortcuts.getDynamicShortcuts().get(0);
      assertEquals(shortcutId,updated.getId());
      assertEquals("Running",updated.getShortLabel().toString());
      var notifications=Shadows.shadowOf(app.getSystemService(NotificationManager.class));
      Notification live=notifications.getNotification(app.liveStatus.idFor("task"));
      Notification bubble=notifications.getNotification(2);
      assertEquals("Running",live.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
      assertEquals("Running",bubble.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).toString());
      assertEquals(shortcutId,bubble.getShortcutId());
      Notification foreground=notifications.getNotification(1);
      assertEquals("Remote task is running",foreground.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
      assertEquals("Disconnect",foreground.actions[0].title.toString());
      assertEquals("task",app.threadId);assertEquals("turn",app.turnId);assertTrue(app.busy);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
      assertTrue(shortcuts.getDynamicShortcuts().isEmpty());
    } finally {service.destroy();}
  }
}
