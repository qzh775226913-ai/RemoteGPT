package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w411dp-h891dp-xhdpi")
public class ConversationHubTest {
  @Test public void headerGroupsFactsSkillsAndMcpWithoutNetworkAccess() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.profile.cwd="/project";
    ConversationHub hub=new ConversationHub(activity,app);
    hub.show();
    AlertDialog list=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals(UiText.get(R.string.ui_current_project_and_conversation),list.getListView().getAdapter().getItem(0));
    assertEquals(UiText.get(R.string.ui_available_skills),list.getListView().getAdapter().getItem(1));
    assertEquals(UiText.get(R.string.ui_mcp_servers),list.getListView().getAdapter().getItem(2));
    list.getListView().performItemClick(null,0,0);
    AlertDialog details=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    String body=org.robolectric.Shadows.shadowOf(details).getMessage().toString();
    assertTrue(body.contains("/project"));
    assertTrue(body.contains(UiText.get(R.string.ui_detail_model,UiText.get(R.string.ui_default_model))));
    assertTrue(body.contains(UiText.get(R.string.ui_detail_permission,UiPresentation.permission(app.displayedPermission()))));
  }
}
