package app.remotessh;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;

/** Storage Access Framework grants access only to the files the user selects. */
final class FilePicker {
  static final int REQUEST=45;
  static Intent documentIntent(int remaining) {
    return new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,remaining>1)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }
  static boolean launch(Activity activity,int remaining) {
    Intent[] candidates={documentIntent(remaining),new Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,remaining>1)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};
    for(Intent intent:candidates) {
      if(intent.resolveActivity(activity.getPackageManager())==null)continue;
      try {
        activity.startActivityForResult(Intent.ACTION_GET_CONTENT.equals(intent.getAction())
            ? Intent.createChooser(intent,UiText.get(R.string.ui_choose_file_manager)):intent,REQUEST);
        return true;
      } catch(ActivityNotFoundException | SecurityException unavailable) { }
    }
    return false;
  }
}
