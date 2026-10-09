package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class ImagePickerTest {
  private final Uri first = Uri.parse("content://album/first"), second = Uri.parse("content://album/second");

  @Test public void acceptsMultiSelectionSingleSelectionAndDuplicateResultsInOrder() {
    ClipData clip = ClipData.newUri(RuntimeEnvironment.getApplication().getContentResolver(), "images", first);
    clip.addItem(new ClipData.Item(second)); clip.addItem(new ClipData.Item(first));
    clip.addItem(new ClipData.Item("not an image URI"));
    Intent multiple = new Intent().setData(first); multiple.setClipData(clip);
    assertEquals(Arrays.asList(first, second), ImagePicker.selected(multiple));
    assertEquals(Collections.singletonList(second), ImagePicker.selected(new Intent().setData(second)));
    assertTrue(ImagePicker.selected(new Intent()).isEmpty()); assertTrue(ImagePicker.selected(null).isEmpty());
  }

  private void available(Activity activity, Intent intent) {
    ResolveInfo info = new ResolveInfo(); info.activityInfo = new ActivityInfo();
    info.activityInfo.packageName = "test.album"; info.activityInfo.name = "test.album.Picker";
    info.activityInfo.exported = true;
    shadowOf(activity.getPackageManager()).addResolveInfoForIntent(intent, info);
  }

  @Test public void opensNativeGalleryThroughAppChooserAndRequestsMultipleImages() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Intent expected = ImagePicker.candidates(ImagePicker.Source.GALLERY, 4).get(0); available(activity, expected);
    assertTrue(ImagePicker.launch(activity, ImagePicker.Source.GALLERY, 4));
    Intent chooser = shadowOf(activity).getNextStartedActivityForResult().intent;
    assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
    Intent target = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
    assertEquals(Intent.ACTION_PICK, target.getAction());
    assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, target.getData());
    assertEquals("image/*", target.getType()); assertTrue(target.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
  }

  @Test public void canChooseImageAppsWithoutFilteringOutGalleryOnlyProviders() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Intent expected = ImagePicker.candidates(ImagePicker.Source.APPS, 4).get(0); available(activity, expected);
    assertTrue(ImagePicker.launch(activity, ImagePicker.Source.APPS, 4));
    Intent target = shadowOf(activity).getNextStartedActivityForResult().intent.getParcelableExtra(Intent.EXTRA_INTENT);
    assertEquals(Intent.ACTION_GET_CONTENT, target.getAction());
    assertFalse(target.hasCategory(Intent.CATEGORY_OPENABLE)); assertTrue(target.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
  }

  @Test public void fallsBackToDocumentMultiPickerWhenAlbumsAndPhotoPickerAreMissing() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    for (ImagePicker.Source source : ImagePicker.Source.values()) {
      Intent files = ImagePicker.candidates(source, 4).stream().filter(i -> Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction())).findFirst().get();
      available(activity, files);
      assertTrue(ImagePicker.launch(activity, source, 4));
      Intent launched = shadowOf(activity).getNextStartedActivityForResult().intent;
      assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.getAction());
      assertTrue(launched.hasCategory(Intent.CATEGORY_OPENABLE));
      assertTrue(launched.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
    }
  }

  @Test public void systemPickerUsesValidLimitsAndSingleRemainingSlot() {
    List<Intent> multiple = ImagePicker.candidates(ImagePicker.Source.SYSTEM, 3);
    assertEquals(MediaStore.ACTION_PICK_IMAGES, multiple.get(0).getAction());
    assertEquals(3, multiple.get(0).getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1));
    for (Intent intent : ImagePicker.candidates(ImagePicker.Source.SYSTEM, 1)) {
      assertFalse(intent.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX));
      assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
    }
  }

  @Test public void unavailablePickersReturnWithoutCrashingOrLaunching() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    assertFalse(ImagePicker.launch(activity, ImagePicker.Source.GALLERY, 4));
    assertNull(shadowOf(activity).getNextStartedActivityForResult());
  }
}
