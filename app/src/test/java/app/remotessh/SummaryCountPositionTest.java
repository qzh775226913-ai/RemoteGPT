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
  private static final int[] COUNT_IDS={R.string.ui_edited_files_count,R.string.ui_viewed_files_count,
      R.string.ui_ran_actions_count,R.string.ui_changed_files_count,R.string.ui_more_files_count};
  private static final String[] HANS_PREFIX={"已编辑","已浏览","已执行","已更改","查看另外"};
  private static final String[] HANS_SUFFIX={"个文件","个文件","项操作","个文件","个文件"};
  private static final String[] HANT_PREFIX={"已編輯","已瀏覽","已執行","已更改","查看另外"};
  private static final String[] HANT_SUFFIX={"個檔案","個檔案","項操作","個檔案","個檔案"};

  @Test public void allSummaryCountsFollowTheirLabelsInLightTheme() throws Exception {checkCountPosition(false);}

  @Test @Config(qualifiers="zh-rCN-w411dp-h891dp-night-xhdpi")
  public void allSummaryCountsFollowTheirLabelsInDarkTheme() throws Exception {checkCountPosition(true);}

  private void checkCountPosition(boolean dark) throws Exception {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=dark?new DialogUi(activity,Color.BLACK,Color.WHITE,0xffb0b0b0,0xff303030,0xff444444):
          new DialogUi(activity,Color.WHITE,Color.BLACK,0xff626262,0xffeceef1,0xffd9dde2);
      LinearLayout list=ui.column();list.setBackgroundColor(ui.bg);
      int[] values={1,2,9,10,37,100};
      for(int kind=0;kind<COUNT_IDS.length;kind++){
        for(int i=0;i<values.length;i++){
          int value=values[i];String label=UiText.get(COUNT_IDS[kind],value);
          assertEquals(HANS_PREFIX[kind]+value+HANS_SUFFIX[kind],label);
          if((i&1)!=0)label=UiText.get(R.string.ui_edited_files_count,1)+"  +2 −1 · "+label;
          ChatItem group=new ChatItem("group-"+kind+"-"+value,kind==3?"fileSummary":"activityGroup",label);
          if(kind==3)group.detail="+2 −1";
          group.children.add(new ChatItem("cmd-"+value,"commandExecution","pwd"));
          ToolActivityGroupView row=new ToolActivityGroupView(activity,ui,group,new HashSet<>(),item->{});
          list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
      }
      int width=ui.dp(390);list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(ui.dp(1200),View.MeasureSpec.AT_MOST));
      list.layout(0,0,width,list.getMeasuredHeight());
      for(int kind=0;kind<COUNT_IDS.length;kind++){
        for(int i=0;i<values.length;i++){
          TextView title=(TextView)((ToolActivityGroupView)list.getChildAt(kind*values.length+i)).getChildAt(0);
          String text=title.getText().toString();int start=text.lastIndexOf(HANS_PREFIX[kind]);
          assertTrue(text,start>=0);int end=start+HANS_PREFIX[kind].length();
          int digit=end;while(digit<text.length() && !Character.isDigit(text.charAt(digit)))digit++;
          assertTrue(text,digit<text.length());
          float gap=title.getLayout().getPrimaryHorizontal(digit)-title.getLayout().getPrimaryHorizontal(end);
          assertEquals("the count follows its label without extra characters",end,digit);
          assertEquals("the count must not be laid out farther to the right",0f,gap,.01f);
          Rect previousInk=new Rect(),digitInk=new Rect();
          title.getPaint().getTextBounds(text,end-1,end,previousInk);
          title.getPaint().getTextBounds(text,digit,digit+1,digitInk);
          float previousRight=title.getLayout().getPrimaryHorizontal(end-1)+previousInk.right;
          float digitLeft=title.getLayout().getPrimaryHorizontal(digit)+digitInk.left;
          String lastCharacter=HANS_PREFIX[kind].substring(HANS_PREFIX[kind].length()-1);
          assertTrue("visible gap is too wide: "+(digitLeft-previousRight),
              digitLeft-previousRight<title.getPaint().measureText(lastCharacter)*.5f);
        }
      }
      File directory=new File("build/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());
      Bitmap bitmap=Bitmap.createBitmap(width,list.getHeight(),Bitmap.Config.ARGB_8888);list.draw(new Canvas(bitmap));
      try(FileOutputStream out=new FileOutputStream(new File(directory,"summary-counts-"+(dark?"dark":"light")+".png"))){
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
      }
    } finally {owner.pause().stop().destroy();}
  }

  @Test public void allSummaryCountsRemainLocalizedForAllSupportedLanguages() {
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();
      for(int i=1;i<AppLanguage.TAGS.length;i++){
        String tag=AppLanguage.TAGS[i];
        android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.setLocales(new android.os.LocaleList(Locale.forLanguageTag(tag)));
        android.content.Context localized=activity.createConfigurationContext(config);
        for(int kind=0;kind<COUNT_IDS.length;kind++)for(int count:new int[]{1,10}){
          String label=localized.getString(COUNT_IDS[kind],count);
          assertTrue(tag,label.contains(Integer.toString(count)));assertFalse(tag,label.contains("%"));
          if(tag.equals("zh-Hans"))assertEquals(HANS_PREFIX[kind]+count+HANS_SUFFIX[kind],label);
          if(tag.equals("zh-Hant"))assertEquals(HANT_PREFIX[kind]+count+HANT_SUFFIX[kind],label);
        }
      }
    } finally {owner.pause().stop().destroy();}
  }
}
