package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w411dp-h891dp-xhdpi")
public class RemoteBrowserTest {
  private View find(View root, String label) {
    if (root instanceof TextView && label.contentEquals(((TextView)root).getText())) return root;
    if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) {
      View result=find(((ViewGroup)root).getChildAt(i),label);if(result!=null)return result;
    }
    return null;
  }

  @Test public void longPressOffersRenameAndPinAndSearchTracksQuery() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.profile.cwd="/project";app.connected=true;
    app.threads=new JSONArray().put(Json.obj("id","thread-a","name","Alpha","cwd","/project"));
    RemoteBrowser browser=new RemoteBrowser(activity,app,false,new RemoteBrowser.Actions() {
      public void connect(Profile p) {} public void add() {} public void settings() {}
      public void directories() {} public void diagnostics() {} public void chat() {}
    });
    browser.show();
    Dialog browserWindow=ShadowDialog.getLatestDialog();
    View row=find(browserWindow.getWindow().getDecorView(),"Alpha");
    assertNotNull(row);
    assertTrue(((View)row.getParent()).performLongClick());
    AlertDialog actions=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals(UiText.get(R.string.ui_rename_conversation),actions.getListView().getAdapter().getItem(0));
    assertEquals(UiText.get(R.string.ui_pin_conversation),actions.getListView().getAdapter().getItem(1));
    actions.getListView().performItemClick(null,1,1);
    assertTrue(app.threadDirectory.pinned("thread-a"));
    EditText search=findInput(browserWindow.getWindow().getDecorView());
    assertNotNull(search);app.nextCursor="stale-page";search.setText("Alpha");
    assertEquals("Alpha",app.historySearch);
    assertEquals("",app.nextCursor);
    app.historySearch="";
    browser.refresh();
    assertEquals("",search.getText().toString());
    browser.dismiss();
  }

  private EditText findInput(View root) {
    if (root instanceof EditText) return (EditText)root;
    if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) {
      EditText result=findInput(((ViewGroup)root).getChildAt(i));if(result!=null)return result;
    }
    return null;
  }
}
