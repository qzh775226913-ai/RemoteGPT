package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ThumbnailCacheTest {
  @Test public void cacheBoundsMemoryWithoutRecyclingImagesStillHeldByViews() {
    ThumbnailCache cache=new ThumbnailCache();Bitmap first=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888);
    cache.images.put("a",first);cache.images.put("b",Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888));cache.images.put("c",Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888));
    assertTrue(cache.images.size()<=8*1024*1024);assertNull(cache.images.get("a"));assertFalse(first.isRecycled());
  }
  @Test public void cacheIdentityDoesNotMixHostsOrSourcesAndContainsNoImageData() throws Exception {
    assertNotEquals(ThumbnailCache.key("host-a","data:image/png;base64,abc"),ThumbnailCache.key("host-b","data:image/png;base64,abc"));
    assertNotEquals(ThumbnailCache.key("host-a","/tmp/a.png"),ThumbnailCache.key("host-a","/tmp/b.png"));
    assertFalse(ThumbnailCache.key("host-a","data:image/png;base64,abc").contains("data:"));
  }
}
