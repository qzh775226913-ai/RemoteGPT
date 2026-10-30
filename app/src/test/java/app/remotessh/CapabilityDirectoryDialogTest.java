package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w320dp-h720dp-mdpi")
public class CapabilityDirectoryDialogTest {
  @Test public void groupsNamesSearchesAndOpensOnlySelectedDetail() {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    CapabilityDirectoryDialog directory=new CapabilityDirectoryDialog(activity,"Skills","Empty");
    directory.showEntries(Arrays.asList(
        new CapabilityDirectoryDialog.Entry("Alpha","Enabled","First detail"),
        new CapabilityDirectoryDialog.Entry("Beta","Disabled","Second detail")));
    AlertDialog list=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(findText(list.getWindow().getDecorView(),"Alpha"));
    assertNotNull(findText(list.getWindow().getDecorView(),"Beta"));
    assertNull(findText(list.getWindow().getDecorView(),"First detail"));
    EditText search=findSearch(list.getWindow().getDecorView());
    assertNotNull(search);
    search.setText("be");
    assertNull(findText(list.getWindow().getDecorView(),"Alpha"));
    TextView beta=findText(list.getWindow().getDecorView(),"Beta");
    assertNotNull(beta);
    beta.performClick();
    AlertDialog detail=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals("Second detail",org.robolectric.Shadows.shadowOf(detail).getMessage().toString());
    detail.dismiss();
    search.setText("missing");
    assertNotNull(findText(list.getWindow().getDecorView(),UiText.get(R.string.ui_no_matching_entries)));
    directory.showError();
    assertNotNull(findText(list.getWindow().getDecorView(),UiText.get(R.string.ui_remote_list_failed)));
    directory.dismiss();
  }

  private EditText findSearch(View view) {
    if (view instanceof EditText) return (EditText)view;
    if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
      EditText found=findSearch(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
    }
    return null;
  }

  private TextView findText(View view,String text) {
    if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return (TextView)view;
    if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
      TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
    }
    return null;
  }
}
