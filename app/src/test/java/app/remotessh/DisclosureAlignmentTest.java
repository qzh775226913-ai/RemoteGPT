package app.remotessh;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import java.util.HashSet;
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
public class DisclosureAlignmentTest {
  @Test @Config(sdk={26,35}) public void disclosureIconsAreVisuallyCenteredInFoldedAndExpandedSummaries(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();UiText.initialize(activity);
      DialogUi ui=new DialogUi(activity,Color.WHITE,Color.BLACK,Color.BLACK,0xffeeeeee,Color.GRAY);
      for(boolean summary:new boolean[]{false,true})for(boolean open:new boolean[]{false,true}){
        ChatItem group=new ChatItem("group",summary?"fileSummary":"activityGroup",summary?"已更改5个文件":"已编辑1个文件 · 已浏览1个文件 · 已执行2次");
        group.detail=summary?"+20 −4":"";
        group.children.add(new ChatItem("cmd","commandExecution","pwd"));
        if(summary)for(int i=0;i<4;i++)group.children.add(new ChatItem("file"+i,"fileChange","File"+i));
        HashSet<String> expanded=new HashSet<>();if(open)expanded.add(group.id);
        ToolActivityGroupView view=new ToolActivityGroupView(activity,ui,group,expanded,item->{});
        measure(view,ui.dp(380));TextView title=(TextView)view.getChildAt(0);
        assertIconCentered(title,title.getText().length()-1,(summary?"file summary":"activity summary")+(open?" expanded":" folded"));
        if(!summary)assertIconCentered(title,0,"activity leading icon");
        if(summary && open){
          android.widget.LinearLayout children=(android.widget.LinearLayout)view.getChildAt(1);
          TextView more=(TextView)children.getChildAt(children.getChildCount()-1);
          assertIconCentered(more,more.getText().length()-1,"more files icon");more.performClick();measure(view,ui.dp(380));
          more=(TextView)children.getChildAt(children.getChildCount()-1);
          assertIconCentered(more,more.getText().length()-1,"collapse files icon");
        }
      }
      TextView standalone=ui.compactSummary();standalone.setText(AppIcons.compactLabel(activity,"›  已运行 ls  ›","terminal","chevron",Color.BLACK,ui.dp(15)));
      measure(standalone,ui.dp(380));assertIconCentered(standalone,standalone.getText().length()-1,"single tool trailing icon");
      assertIconCentered(standalone,0,"single tool leading icon");
      TextView ordinary=ui.text("History  ›",15,Color.BLACK);ordinary.setGravity(android.view.Gravity.CENTER_VERTICAL);
      ordinary.setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(6));
      ordinary.setText(AppIcons.label(activity,ordinary.getText(),null,"chevron",Color.BLACK,ui.dp(15)));
      measure(ordinary,ui.dp(380));assertIconCentered(ordinary,ordinary.getText().length()-1,"ordinary inline icon");
    }finally{owner.pause().stop().destroy();}
  }
  @Test @Config(sdk={26,35}) @SuppressWarnings("deprecation") public void localizedLabelsAndLargerFontsKeepTheirIconsCentered(){
    ActivityController<Activity> owner=Robolectric.buildActivity(Activity.class).setup();
    try{
      Activity activity=owner.get();
      for(int i=1;i<AppLanguage.TAGS.length;i++)for(float scale:new float[]{.75f,1f,1.3f})for(boolean dark:new boolean[]{false,true}){
        android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.setLocales(new android.os.LocaleList(java.util.Locale.forLanguageTag(AppLanguage.TAGS[i])));config.fontScale=scale;
        activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());UiText.initialize(activity);
        DialogUi ui=new DialogUi(activity,dark?Color.BLACK:Color.WHITE,dark?Color.WHITE:Color.BLACK,
            dark?Color.WHITE:Color.BLACK,dark?0xff303030:0xffeeeeee,Color.GRAY);
        ChatItem group=new ChatItem("group","activityGroup",UiText.get(R.string.ui_ran_actions_count,3));
        group.children.add(new ChatItem("cmd","commandExecution","pwd"));
        for(boolean open:new boolean[]{false,true}){
          HashSet<String> expanded=new HashSet<>();if(open)expanded.add(group.id);
          ToolActivityGroupView view=new ToolActivityGroupView(activity,ui,group,expanded,item->{});measure(view,ui.dp(900));
          TextView title=(TextView)view.getChildAt(0);String label=AppLanguage.TAGS[i]+" "+scale+(dark?" dark":" light")+(open?" open":" closed");
          assertIconCentered(title,0,label+" leading");assertIconCentered(title,title.getText().length()-1,label+" trailing");
        }
      }
    }finally{owner.pause().stop().destroy();}
  }

  private static void measure(View view,int width){
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST));
    view.layout(0,0,width,view.getMeasuredHeight());
  }
  private static void assertIconCentered(TextView view,int index,String label){
    view.setBackgroundColor(Color.TRANSPARENT);
    Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    int left=Math.max(0,(int)Math.floor(view.getCompoundPaddingLeft()+view.getLayout().getPrimaryHorizontal(index))-1);
    int right=Math.min(bitmap.getWidth(),(int)Math.ceil(view.getCompoundPaddingLeft()+view.getLayout().getPrimaryHorizontal(index+1))+1);
    int first=bitmap.getHeight(),last=-1;
    for(int y=0;y<bitmap.getHeight();y++)for(int x=left;x<right;x++)if(Color.alpha(bitmap.getPixel(x,y))>48){first=Math.min(first,y);last=Math.max(last,y);}
    assertTrue(label+" must render in its character slot",last>=first);
    float center=(first+last+1)/2f,expected=view.getHeight()/2f;
    android.graphics.Rect ink=new android.graphics.Rect();AppIcons.textInkBounds(view.getPaint(),view.getText(),ink);
    float shift=view instanceof CompactSummaryTextView?((CompactSummaryTextView)view).opticalShift():0;
    assertEquals(label+" glyph bounds "+first+".."+last+" baseline="+view.getBaseline()+" ink="+ink+" shift="+shift+" line="+view.getLayout().getLineTop(0)+".."+view.getLayout().getLineBottom(0),expected,center,view.getResources().getDisplayMetrics().density*.8f);
    bitmap.recycle();
  }
}
