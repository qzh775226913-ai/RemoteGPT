package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ContextMenuPanelTest {
  @Test public void readOnlyHistoryOffersResumeAndCompactConfirmation() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="A";app.readOnly=true;
    new ContextMenuPanel(activity,app,()->{},()->{}).show();
    AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    menu.getListView().performItemClick(null,1,1);
    AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals(UiText.get(R.string.ui_compact_resume_confirmation),
        org.robolectric.Shadows.shadowOf(confirm).getMessage().toString());
    confirm.dismiss();
  }
}
