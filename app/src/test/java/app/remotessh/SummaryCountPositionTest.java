package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SummaryCountPositionTest {
  @Test public void executionCountFollowsItsLabelInLightTheme() throws Exception {checkCountPosition(false);}

  @Test @Config(qualifiers="zh-rCN-w411dp-h891dp-night-xhdpi")
  public void executionCountFollowsItsLabelInDarkTheme() throws Exception {checkCountPosition(true);}

  private void checkCountPosition(boolean dark) throws Exception {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=dark?new DialogUi(activity,Color.BLACK,Color.WHITE,0xffb0b0b0,0xff303030,0xff444444):
          new DialogUi(activity,Color.WHITE,Color.BLACK,0xff626262,0xffeceef1,0xffd9dde2);
      LinearLayout list=ui.column();list.setBackgroundColor(ui.bg);
      int[] values={1,2,9,10,37,100};
      for(int i=0;i<values.length;i++){
        int value=values[i];String label=UiText.get(R.string.ui_ran_actions_count,value);
        assertEquals("已执行"+value+"项操作",label);
        if((i&1)!=0)label="已编辑 1 个文件  +2 −1 · "+label;
        ChatItem group=new ChatItem("group-"+value,"activityGroup",label);
        group.children.add(new ChatItem("cmd-"+value,"commandExecution","pwd"));
        ToolActivityGroupView row=new ToolActivityGroupView(activity,ui,group,new HashSet<>(),item->{});
        list.addView(row,new LinearLayout.LayoutParams(-1,-2));
      }
      int width=ui.dp(390);list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(ui.dp(400),View.MeasureSpec.AT_MOST));
      list.layout(0,0,width,list.getMeasuredHeight());
      for(int i=0;i<values.length;i++){
        TextView title=(TextView)((ToolActivityGroupView)list.getChildAt(i)).getChildAt(0);
        String text=title.getText().toString();int end=text.indexOf("已执行")+3;
        int digit=end;while(digit<text.length() && !Character.isDigit(text.charAt(digit)))digit++;
        assertTrue(text,end>=3 && digit<text.length());
        float gap=title.getLayout().getPrimaryHorizontal(digit)-title.getLayout().getPrimaryHorizontal(end);
        assertEquals("the count follows the execution label without extra characters",end,digit);
        assertEquals("the count must not be laid out farther to the right",0f,gap,.01f);
        Rect previousInk=new Rect(),digitInk=new Rect();
        title.getPaint().getTextBounds(text,end-1,end,previousInk);
        title.getPaint().getTextBounds(text,digit,digit+1,digitInk);
        float previousRight=title.getLayout().getPrimaryHorizontal(end-1)+previousInk.right;
        float digitLeft=title.getLayout().getPrimaryHorizontal(digit)+digitInk.left;
        assertTrue("visible gap is too wide: "+(digitLeft-previousRight),
            digitLeft-previousRight<title.getPaint().measureText("行")*.5f);
      }
      File directory=new File("build/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());
      Bitmap bitmap=Bitmap.createBitmap(width,list.getHeight(),Bitmap.Config.ARGB_8888);list.draw(new Canvas(bitmap));
      try(FileOutputStream out=new FileOutputStream(new File(directory,"execution-count-"+(dark?"dark":"light")+".png"))){
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
      }
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void executionCountRemainsLocalizedForAllSupportedLanguages() {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();
      for(int i=1;i<AppLanguage.TAGS.length;i++){
        String tag=AppLanguage.TAGS[i];
        android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.setLocales(new android.os.LocaleList(Locale.forLanguageTag(tag)));
        android.content.Context localized=activity.createConfigurationContext(config);
        for(int count:new int[]{1,10}){
          String label=localized.getString(R.string.ui_ran_actions_count,count);
          assertTrue(tag,label.contains(Integer.toString(count)));assertFalse(tag,label.contains("%"));
          if(tag.equals("zh-Hans"))assertEquals("已执行"+count+"项操作",label);
          if(tag.equals("zh-Hant"))assertEquals("已執行"+count+"項操作",label);
        }
      }
    } finally {owner.pause().stop().destroy();}
  }
}
