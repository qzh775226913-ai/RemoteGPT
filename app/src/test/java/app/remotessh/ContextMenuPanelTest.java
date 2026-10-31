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
  private DialogUi ui(Activity activity){return new DialogUi(activity,0xff000000,0xffeeeeee,0xffaaaaaa,0xff303030,0xff424242);}
  @Test public void readOnlyHistoryOffersResumeAndCompactConfirmation() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="A";app.readOnly=true;
    new ContextMenuPanel(activity,app,ui(activity),()->{},()->{}).show();
    AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    menu.getListView().performItemClick(null,1,1);
    AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals(UiText.get(R.string.ui_compact_resume_confirmation),
        org.robolectric.Shadows.shadowOf(confirm).getMessage().toString());
    confirm.dismiss();
  }

  @Test public void activeCompactionShowsSpecificStatusInsteadOfIdleError() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();UiText.initialize(activity);
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="active";app.turnId="compact-turn";
    app.readOnly=false;app.busy=true;app.remoteRunning=true;
    ChatItem compaction=new ChatItem("compact","contextCompaction","");compaction.turnId="compact-turn";
    app.items.add(compaction);
    new ContextMenuPanel(activity,app,ui(activity),()->{},()->{}).show();
    AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    menu.getListView().performItemClick(null,1,1);
    AlertDialog status=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals(UiText.get(R.string.ui_context_compacting),
        org.robolectric.Shadows.shadowOf(status).getMessage().toString());
    status.dismiss();app.items.clear();
  }
}
