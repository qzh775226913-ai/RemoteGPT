package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w320dp-h720dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SummaryCardSpacingTest {
  @Test public void completedFileCardIsCompactAndReadable() throws Exception {verify(false,1f);}
  @Test @Config(qualifiers="zh-rCN-w320dp-h720dp-night-xhdpi")
  public void completedFileCardFitsLargerTextInDarkTheme() throws Exception {verify(true,1.3f);}

  @SuppressWarnings("deprecation")
  private void verify(boolean dark,float fontScale) throws Exception {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();Configuration config=new Configuration(activity.getResources().getConfiguration());
      config.fontScale=fontScale;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
      UiText.initialize(activity);
      DialogUi ui=dark?new DialogUi(activity,Color.BLACK,Color.WHITE,0xffb0b0b0,0xff303030,0xff444444):
          new DialogUi(activity,Color.WHITE,Color.BLACK,0xff626262,0xffeceef1,0xffd9dde2);
      ChatItem summary=new ChatItem("files","fileSummary","已更改5个文件");summary.detail="+20 −4";
      for(int i=0;i<5;i++){
        ChatItem file=new ChatItem("file-"+i,"fileChange","src/File"+i+".java");
        file.changes=new org.json.JSONArray().put(Json.obj("path",file.text,"diff","@@ -1 +1 @@\n-old\n+new\n"));
        summary.children.add(file);
      }
      HashSet<String> expanded=new HashSet<>();expanded.add(summary.id);
      ToolActivityGroupView card=new ToolActivityGroupView(activity,ui,summary,expanded,item->{});
      int width=ui.dp(288);card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(ui.dp(600),View.MeasureSpec.AT_MOST));
      card.layout(0,0,width,card.getMeasuredHeight());
      LinearLayout content=(LinearLayout)card.getChildAt(1);assertEquals(4,content.getChildCount());
      if(fontScale==1f)assertTrue("summary has too much vertical space: "+card.getHeight(),card.getHeight()<=ui.dp(150));
      for(int i=0;i<3;i++){
        LinearLayout disclosure=(LinearLayout)content.getChildAt(i);LinearLayout row=(LinearLayout)disclosure.getChildAt(0);
        TextView name=(TextView)row.getChildAt(1);
        if(fontScale==1f)assertTrue("file rows should be compact",row.getHeight()<=ui.dp(31));
        assertTrue("file name must remain inside its row",name.getTop()>=0 && name.getBottom()<=row.getHeight());
        assertTrue(name.getText().toString().contains("File"+i+".java"));
      }
      File directory=new File("build/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());
      Bitmap bitmap=Bitmap.createBitmap(width,card.getHeight(),Bitmap.Config.ARGB_8888);card.draw(new Canvas(bitmap));
      try(FileOutputStream out=new FileOutputStream(new File(directory,
          dark?"summary-card-compact-dark.png":"summary-card-compact-light.png"))){
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
      }
    } finally {owner.pause().stop().destroy();}
  }
}
