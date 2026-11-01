package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import android.view.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w411dp-h891dp-xhdpi",shadows=RegionDecoderShadow.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageSourceUiTest {
  @Test public void readmeCommandHasNoFailedImageTilesAndActualPhotoStillLoads() throws Exception {
    ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity=controller.get();RemoteApp app=(RemoteApp)activity.getApplication();
    try {
      app.profile=new Profile();app.threadId="synthetic-media";app.connected=true;
      ChatItem command=new ChatItem("cmd","commandExecution",MediaSourceTest.README_COMMAND);command.status="completed";
      app.items.add(command);app.changed();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      List<MessageImageTile> tiles=ReflectionHelpers.getField(activity,"mediaTiles");
      assertTrue("README badge text must not schedule image downloads",tiles.isEmpty());
      Bitmap photo=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);photo.eraseColor(Color.GREEN);
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();photo.compress(Bitmap.CompressFormat.PNG,100,bytes);photo.recycle();
      ChatItem real=new ChatItem("photo","agentMessage","实际图片仍可查看。");
      real.images.add("data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
      app.items.add(real);app.changed();
      View root=activity.findViewById(android.R.id.content);int width=822,height=1782;
      root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      tiles=ReflectionHelpers.getField(activity,"mediaTiles");assertEquals(1,tiles.size());
      MessageImageTile tile=tiles.get(0);assertEquals("photo",tile.itemId);tile.visible=true;tile.load();
      for(int i=0;i<150 && !tile.loaded;i++){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(30));}
      assertTrue("Real structured image must decode and display",tile.loaded);assertNotNull(tile.preview.getDrawable());
      Bitmap screenshot=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(screenshot));
      File folder=new File("build/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());
      try(FileOutputStream out=new FileOutputStream(new File(folder,"readme-command-media-fixed.png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));}
      screenshot.recycle();
    } finally {controller.pause().stop().destroy();}
  }

  @Test public void imageArrivingInsideLiveToolGroupShowsThumbnailAndOriginal() throws Exception {
    ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity=controller.get();RemoteApp app=(RemoteApp)activity.getApplication();
    try {
      app.profile=new Profile();app.threadId="grouped-media";app.connected=true;app.busy=true;app.turnId="turn";
      ChatItem command=ChatItem.from(Json.obj("id","cmd","type","commandExecution","command","inspect"));command.turnId="turn";
      ChatItem tool=ChatItem.from(Json.obj("id","tool","type","mcpToolCall","server","test","tool","image"));tool.turnId="turn";
      app.items.add(command);app.items.add(tool);app.changed();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      List<MessageImageTile> tiles=ReflectionHelpers.getField(activity,"mediaTiles");assertTrue(tiles.isEmpty());
      Bitmap photo=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);photo.eraseColor(Color.GREEN);
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();assertTrue(photo.compress(Bitmap.CompressFormat.PNG,100,bytes));photo.recycle();
      String source="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
      tool.images.add(source);tool.status="completed";app.changed();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      View root=activity.findViewById(android.R.id.content);
      root.measure(View.MeasureSpec.makeMeasureSpec(822,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1782,View.MeasureSpec.EXACTLY));
      root.layout(0,0,822,1782);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      tiles=ReflectionHelpers.getField(activity,"mediaTiles");assertEquals("the folded activity must have an image tile",1,tiles.size());
      MessageImageTile tile=tiles.get(0);tile.visible=true;tile.load();
      for(int i=0;i<150 && !tile.loaded;i++){Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(30));}
      assertTrue("grouped image thumbnail must decode",tile.loaded);assertNotNull(tile.preview.getDrawable());
      assertTrue(tile.preview.performClick());ImageDialog dialog=ReflectionHelpers.getField(activity,"imageDialog");
      assertNotNull(dialog);assertTrue(dialog.dialog.isShowing());assertEquals(source,dialog.currentSource());
      for(int i=0;i<150 && ReflectionHelpers.getField(dialog.image,"document")==null;i++){
        Thread.sleep(10);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
      }
      assertNotNull("the original image must open from the grouped thumbnail",ReflectionHelpers.getField(dialog.image,"document"));
      dialog.dismiss();app.busy=false;app.turnId="";app.changed();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      tiles=ReflectionHelpers.getField(activity,"mediaTiles");assertEquals("finished groups must retain the image",1,tiles.size());
    } finally {controller.pause().stop().destroy();}
  }
}
