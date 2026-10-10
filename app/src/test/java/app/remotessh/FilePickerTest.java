package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk={28,35})
public class FilePickerTest {
  void available(Activity activity,Intent intent) {
    ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();
    info.activityInfo.packageName="test.files";info.activityInfo.name="test.files.Picker";info.activityInfo.exported=true;
    shadowOf(activity.getPackageManager()).addResolveInfoForIntent(intent,info);
  }
  @Test public void opensDocumentsOfAnyTypeWithReadGrantAndMultiSelection() {
    var controller=Robolectric.buildActivity(Activity.class).setup();Activity activity=controller.get();
    try {
      available(activity,FilePicker.documentIntent(8));assertTrue(FilePicker.launch(activity,8));
      var started=shadowOf(activity).getNextStartedActivityForResult();assertEquals(FilePicker.REQUEST,started.requestCode);
      assertEquals(Intent.ACTION_OPEN_DOCUMENT,started.intent.getAction());assertEquals("*/*",started.intent.getType());
      assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(started.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
      assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,started.intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }finally{controller.pause().stop().destroy();}
  }
  @Test public void fallsBackToAvailableFileAppsAndHandlesMissingPicker() {
    var controller=Robolectric.buildActivity(Activity.class).setup();Activity activity=controller.get();
    try {
      assertFalse(FilePicker.launch(activity,8));assertNull(shadowOf(activity).getNextStartedActivityForResult());
      available(activity,new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
      assertTrue(FilePicker.launch(activity,1));Intent chooser=shadowOf(activity).getNextStartedActivityForResult().intent;
      assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());Intent target=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
      assertEquals(Intent.ACTION_GET_CONTENT,target.getAction());assertEquals("*/*",target.getType());assertFalse(target.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
    }finally{controller.pause().stop().destroy();}
  }
}
