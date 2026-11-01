package app.remotessh;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=RemoteApp.class)
public class ComposerRenderCacheTest {
  @Test public void failedRenderRetriesAndChangedPreviewRebinds(){
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();UiText.initialize(app);
    app.profile=new Profile();app.threadId="thread";app.draftImages.add("/image.png");
    ComposerRenderCache cache=new ComposerRenderCache();AtomicInteger calls=new AtomicInteger();
    try{cache.attachments(app,()->{throw new IllegalStateException("synthetic render failure");});fail();}
    catch(IllegalStateException expected){assertEquals("synthetic render failure",expected.getMessage());}
    cache.attachments(app,calls::incrementAndGet);cache.attachments(app,calls::incrementAndGet);
    assertEquals(1,calls.get());
    Bitmap preview=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
    app.draftImagePreviews.put("/image.png",preview);cache.attachments(app,calls::incrementAndGet);
    assertEquals("the decoded preview must replace its placeholder",2,calls.get());
    app.threadId="another";cache.attachments(app,calls::incrementAndGet);
    assertEquals("another conversation must receive its own callbacks",3,calls.get());
    preview.recycle();
  }
}
