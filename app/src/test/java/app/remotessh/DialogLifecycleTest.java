package app.remotessh;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DialogLifecycleTest {
  @Test public void destroyingActivityReleasesModelPickerListener() throws Exception {
    var controller = Robolectric.buildActivity(MainActivity.class).setup();
    try {
      RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
      app.profile = new Profile();
      app.connected = true;
      app.threadId = "selected";
      int before = app.stateListeners.size();
      Method open = MainActivity.class.getDeclaredMethod("chooseModel");
      open.setAccessible(true);
      open.invoke(controller.get());
      assertEquals(before + 1, app.stateListeners.size());
      controller.pause().stop().destroy();
      assertEquals(before, app.stateListeners.size());
    } finally {
      if (!controller.get().isDestroyed()) controller.pause().stop().destroy();
    }
  }
}
