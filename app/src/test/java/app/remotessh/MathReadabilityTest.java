package app.remotessh;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import java.io.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MathReadabilityTest {
  @Test public void lightConversationScreenshot() throws Exception {capture("light");}
  @Test @Config(qualifiers="zh-rCN-w411dp-h891dp-night-xhdpi") public void darkConversationScreenshot() throws Exception {capture("dark");}
  @Test public void lightSecondaryAndConnectionTextHaveReadableContrast() throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      java.lang.reflect.Field field=MainActivity.class.getDeclaredField("muted");field.setAccessible(true);
      int muted=field.getInt(controller.get());
      for(int background:new int[]{0xffffffff,0xfffafafa,0xfff4f4f4,0xfff2f2f2,0xffececec,0xfff5f6f7,0xffeceef1,0xffeff1f4})assertTrue(contrast(muted,background)>=4.5);
      RemoteApp app=(RemoteApp)controller.get().getApplication();app.profile=new Profile();app.connected=true;app.changed();
      java.lang.reflect.Field status=MainActivity.class.getDeclaredField("status");status.setAccessible(true);
      int color=((android.widget.TextView)status.get(controller.get())).getCurrentTextColor();
      assertTrue(contrast(color,0xfff5f6f7)>=4.5);
    } finally {controller.pause().stop().destroy();}
  }
  private static double contrast(int a,int b) {double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
  private static double luminance(int color) {
    double value=0;double[] weights={.2126,.7152,.0722};
    int[] components={Color.red(color),Color.green(color),Color.blue(color)};
    for(int i=0;i<3;i++){double v=components[i]/255.;value+=weights[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}
    return value;
  }
  private void capture(String theme) throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      MainActivity activity=controller.get();RemoteApp app=(RemoteApp)activity.getApplication();
      app.profile=new Profile();app.profile.name="测试主机";app.profile.cwd="/projects/demo";
      app.connected=true;app.conversationStore.select("math-preview");app.threadTitle="详解叶素理论公式";
      app.items.add(new ChatItem("formula","agentMessage","忽略旋流时，切向速度近似为：\n\n\\[U_t\\approx\\Omega r\\]\n\n相对速度大小为：\n\n\\[W\\approx\\sqrt{(\\Omega r)^2+(V_\\infty+v_i)^2}\\]\n\n入流角为：\n\n\\[\\phi\\approx\\arctan\\frac{V_\\infty+v_i}{\\Omega r}\\]\n\n按螺旋桨角度约定：\n\n\\[\\boxed{\\alpha=\\theta-\\phi}\\]\n\n其中 \\(\\Omega\\) 为角速度，\\(r\\) 为半径。"));
      app.changed();
      View root=activity.findViewById(android.R.id.content);
      int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;
      for(int i=0;i<30;i++) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        Thread.sleep(15);
      }
      Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
      File folder=new File("build/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());
      try(FileOutputStream out=new FileOutputStream(new File(folder,"math-conversation-"+theme+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
      bitmap.recycle();
    } finally {controller.pause().stop().destroy();}
  }
}
