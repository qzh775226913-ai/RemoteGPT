package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageFormatTest {
  @Test public void preservesPortableOriginalsAndProducesThumbnail() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream out = new ByteArrayOutputStream(); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    bitmap.recycle(); byte[] original = out.toByteArray();
    MediaFiles.Image prepared = MediaFiles.prepareImage(original);
    assertSame(original, prepared.bytes); assertEquals(".png", prepared.extension);
    assertTrue(prepared.preview.getWidth() <= 256); assertTrue(prepared.preview.getHeight() <= 256);
  }

  @Test public void convertsOtherPlatformDecodedFormatsToPortableJpeg() throws Exception {
    ByteBuffer bmp = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN);
    bmp.put((byte) 'B').put((byte) 'M').putInt(58).putInt(0).putInt(54);
    bmp.putInt(40).putInt(1).putInt(1).putShort((short) 1).putShort((short) 24);
    bmp.putInt(0).putInt(4).putInt(0).putInt(0).putInt(0).putInt(0);
    bmp.put((byte) 0).put((byte) 0).put((byte) 255).put((byte) 0);
    MediaFiles.Image prepared = MediaFiles.prepareImage(bmp.array());
    assertEquals(".jpg", prepared.extension); assertEquals(0xff, prepared.bytes[0] & 255); assertEquals(0xd8, prepared.bytes[1] & 255);
    assertNotNull(prepared.preview); assertNotNull(MediaFiles.decode(prepared.bytes));
  }

  @Test public void rejectsUnreadableSelectionsBeforeUpload() throws Exception {
    try { MediaFiles.prepareImage(new byte[] {1, 2, 3}); fail(); }
    catch (IOException expected) { assertTrue(expected.getMessage().contains("无法读取")); }
  }
}
