package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.LocaleList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="en-w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ConversationChromeVisualTest {
  @Test public void composerHasSubtleInsetEdgeInBothThemes() throws Exception {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Context context=owner.get();
      for(int panel:new int[]{0xff303030,0xffeceef1}){
        Drawable surface=ComposerSurface.create(context,panel);int width=220,height=80;
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        surface.setBounds(0,0,width,height);surface.draw(new Canvas(bitmap));
        Rect padding=new Rect();surface.getPadding(padding);assertEquals(new Rect(0,0,0,0),padding);
        Outline outline=new Outline();surface.getOutline(outline);assertTrue("rounded clipping must remain available",outline.canClip());
        assertEquals(0,Color.alpha(bitmap.getPixel(0,0)));
        int top=bitmap.getPixel(width/2,0),middle=bitmap.getPixel(width/2,height/2);
        assertEquals(255,Color.alpha(top));assertEquals(panel,middle);
        assertTrue("top edge should suggest a shallow recess",Color.red(top)<Color.red(middle));
        assertTrue("edge contrast should remain subtle",Color.red(middle)-Color.red(top)<=20);
        File directory=new File("build/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,
            panel==0xff303030?"composer-edge-dark.png":"composer-edge-light.png"))){
          assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
        }
      }
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void resumeButtonFitsAllLanguagesAndKeepsOneSize() {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();
      int[] states={R.string.ui_resume,R.string.ui_reconnect_and_resume,R.string.ui_connecting,
          R.string.ui_synchronizing,R.string.ui_resuming};
      for(float fontScale:new float[]{1f,1.15f})for(int i=1;i<AppLanguage.TAGS.length;i++){
        String tag=AppLanguage.TAGS[i];Configuration config=new Configuration(activity.getResources().getConfiguration());
        config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));config.fontScale=fontScale;
        Context localized=activity.createConfigurationContext(config);UiText.initialize(localized);
        LinearLayout holder=new LinearLayout(localized);TextView button=new TextView(localized);
        button.setTextSize(15);ConversationOverlayLayout.addResume(holder,button);
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)button.getLayoutParams();
        int expectedWidth=params.width,expectedHeight=params.height;
        assertTrue(tag,expectedWidth<=localized.getResources().getDisplayMetrics().widthPixels-36);
        for(int state:states){
          String label=UiText.get(state);button.setText(label);
          holder.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST));
          holder.layout(0,0,320,holder.getMeasuredHeight());
          assertEquals(tag,expectedWidth,button.getWidth());assertEquals(tag,expectedHeight,button.getHeight());
          assertTrue(tag+": "+label,button.getPaint().measureText(label)+button.getPaddingLeft()+
              button.getPaddingRight()+20<=expectedWidth+1);
        }
      }
      UiText.initialize(activity);
    } finally {owner.pause().stop().destroy();}
  }
}
