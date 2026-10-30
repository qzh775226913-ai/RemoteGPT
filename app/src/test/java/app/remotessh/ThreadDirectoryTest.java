package app.remotessh;

import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ThreadDirectoryTest {
  @Test public void pinPreferenceIsScopedToConnectionAndSurvivesControllerRecreation() {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    Profile first = new Profile(); first.id = "host-one";
    Profile second = new Profile(); second.id = "host-two";
    app.profile = first;
    app.threadDirectory.pin("thread", true);
    assertTrue(new ThreadDirectory(app).pinned("thread"));
    app.profile = second;
    assertFalse(app.threadDirectory.pinned("thread"));
    app.threadDirectory.pin("thread", true);
    app.profile = first;
    app.threadDirectory.pin("thread", false);
    assertFalse(app.threadDirectory.pinned("thread"));
    app.profile = second;
    assertTrue(app.threadDirectory.pinned("thread"));
  }
}
