package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LocalizationLayoutTest {
  @Test public void allLanguagesFitPermissionAndLanguagePanelsOnNarrowScreen() throws Exception { capture(false); }
  @Test @Config(qualifiers="en-w320dp-h720dp-night-mdpi")
  public void allLanguagesFitAtLargerFontInDarkTheme() throws Exception { capture(true); }

  private void capture(boolean dark) throws Exception {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.getSharedPreferences("display",0).edit().putFloat("fontScale",dark?1.15f:1f).apply();
    for (int i=1;i<AppLanguage.TAGS.length;i++) {
      String tag=AppLanguage.TAGS[i];app.language.select(tag);
      var owner=Robolectric.buildActivity(MainActivity.class).setup();
      PermissionPicker permissions=null;
      try {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("ui");field.setAccessible(true);
        DialogUi ui=(DialogUi)field.get(owner.get());
        permissions=new PermissionPicker(owner.get(),app,ui,false);permissions.show();
        Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View root=dialog.getWindow().getDecorView();
        layout(root);
        assertTextFits(root);
        screenshot(root,"permissions-"+tag+(dark?"-dark-large":"-light"));
        permissions.dismiss();
        LanguagePicker.show(owner.get(),app);
        AlertDialog languages=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        layout(languages.getWindow().getDecorView());
        assertEquals(11,languages.getListView().getAdapter().getCount());
        screenshot(languages.getWindow().getDecorView(),"languages-"+tag+(dark?"-dark-large":"-light"));
        languages.dismiss();
      } finally {if(permissions!=null)permissions.dismiss();owner.pause().stop().destroy();}
    }
  }
  private void layout(View view) {
    view.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));
    view.layout(0,0,320,view.getMeasuredHeight());
  }
  private void assertTextFits(View view) {
    if(view.getVisibility()!=View.VISIBLE)return;
    if(view instanceof TextView) {
      TextView text=(TextView)view;
      if(text.getLayout()!=null && text.length()>0) {
        assertTrue(text.getText().toString(),text.getLayout().getHeight()<=text.getHeight()-text.getTotalPaddingTop()-text.getTotalPaddingBottom());
        for(int i=0;i<text.getLayout().getLineCount();i++) assertEquals(text.getText().toString(),0,text.getLayout().getEllipsisCount(i));
      }
    }
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertTextFits(((ViewGroup)view).getChildAt(i));
  }
  private void screenshot(View view,String name)throws Exception {
    File dir=new File("build/screenshots/localization");assertTrue(dir.isDirectory()||dir.mkdirs());
    Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    bitmap.recycle();
  }
}
