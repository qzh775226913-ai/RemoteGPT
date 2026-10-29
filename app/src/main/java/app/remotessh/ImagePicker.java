package app.remotessh;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Uses installed image apps without depending on any manufacturer's package names. */
final class ImagePicker {
  enum Source { GALLERY, APPS, SYSTEM }
  static final int REQUEST = 43;

  static List<Intent> candidates(Source source, int remaining) {
    Intent gallery = new Intent(Intent.ACTION_PICK)
        .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, remaining > 1);
    Intent apps = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, remaining > 1);
    Intent photos = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
    if (remaining > 1) photos.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
        Build.VERSION.SDK_INT >= 33 ? Math.min(remaining, MediaStore.getPickImagesMaxLimit()) : remaining);
    Intent files = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, remaining > 1);
    List<Intent> result = new ArrayList<>();
    if (source == Source.GALLERY) { result.add(gallery); result.add(apps); }
    if (source == Source.APPS) { result.add(apps); result.add(gallery); }
    result.add(photos); result.add(files);
    if (source == Source.SYSTEM) { result.add(apps); result.add(gallery); }
    for (Intent intent : result) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return result;
  }

  static boolean launch(Activity activity, Source source, int remaining) {
    for (Intent intent : candidates(source, remaining)) {
      if (intent.resolveActivity(activity.getPackageManager()) == null) continue;
      boolean chooseApp = Intent.ACTION_PICK.equals(intent.getAction()) || Intent.ACTION_GET_CONTENT.equals(intent.getAction());
      try {
        activity.startActivityForResult(chooseApp ? Intent.createChooser(intent, UiText.get(R.string.ui_choose_gallery_or_image_app)) : intent, REQUEST);
        return true;
      } catch (ActivityNotFoundException | SecurityException unavailable) {
        // Installed apps can be disabled or removed between resolving and opening.
      }
    }
    return false;
  }

  static List<Uri> selected(Intent result) {
    LinkedHashSet<Uri> uris = new LinkedHashSet<>();
    if (result != null) {
      ClipData clip = result.getClipData();
      if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
        Uri uri = clip.getItemAt(i).getUri();
        if (uri != null) uris.add(uri);
      }
      if (result.getData() != null) uris.add(result.getData());
    }
    return new ArrayList<>(uris);
  }
}
