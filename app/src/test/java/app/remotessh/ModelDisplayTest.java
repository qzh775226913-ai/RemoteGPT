package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w411dp-h891dp-xhdpi")
public class ModelDisplayTest {
  @Test public void effortNamesStayEnglishAcrossAllInterfaceLanguages() {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    String[] ids={"none","minimal","low","medium","high","xhigh","max","ultra","future-effort"};
    String[] names={"None","Minimal","Low","Medium","High","Extra High","Max","Ultra","future-effort"};
    JSONObject model=Json.obj("supportedReasoningEfforts", new JSONArray().put("low")
        .put(Json.obj("reasoningEffort","high","description","Server text")));
    String original=model.toString();
    for(int i=1;i<AppLanguage.TAGS.length;i++) {
      app.language.select(AppLanguage.TAGS[i]);
      for(int j=0;j<ids.length;j++)assertEquals(AppLanguage.TAGS[i],names[j],ModelOptions.label(ids[j]));
      assertEquals(java.util.Arrays.asList("low","high"),ModelOptions.efforts(model));
      assertEquals(original,model.toString());
    }
  }

  @Test public void openPickerLocalizesStatusOnlyUpdatesWithoutReplacingCatalog() {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;
    app.models=new JSONArray().put(Json.obj("model","fixture","displayName","Server model"));
    for(int i=1;i<AppLanguage.TAGS.length;i++) {
      app.language.select(AppLanguage.TAGS[i]);app.modelsStatus="";
      var owner=Robolectric.buildActivity(Activity.class).setup();
      ModelPicker picker=new ModelPicker(owner.get(),app,new DialogUi(owner.get(),0xffffffff,0xff000000,0xff777777,0xffeeeeee,0xffdddddd),false);
      try {
        picker.show();
        View shell=org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
        assertNotNull(find(shell,UiText.get(R.string.ui_models_are_provided_by_the_server_if_a_new_model_is_missing_check)));
        JSONArray snapshot=app.models;
        app.modelsStatus="已同步远端模型";app.changed();
        String synced=UiText.get(R.string.ui_remote_models_synchronized);
        if(AppLanguage.TAGS[i].equals("en"))assertEquals("Remote models synchronized",synced);
        assertNotNull(find(shell,synced));
        app.changed();assertSame(snapshot,app.models);assertNotNull(find(shell,synced));
        app.modelsStatus="正在刷新模型…";app.changed();
        assertNotNull(find(shell,UiText.get(R.string.ui_refreshing_models)));
        String raw="原始服务器错误 %1$s";
        app.modelsStatus="刷新失败，保留上次列表："+raw;app.changed();
        assertNotNull(find(shell,UiText.get(R.string.ui_refresh_failed_keeping_the_previous_list)+raw));
        app.modelsStatus="已同步远端模型";
        app.models=new JSONArray().put(Json.obj("model","new","displayName","Remote name"));app.changed();
        assertNotNull(find(shell,synced));
        app.modelsStatus="";app.changed();
        assertNotNull(find(shell,UiText.get(R.string.ui_models_are_provided_by_the_server_if_a_new_model_is_missing_check)));
      } finally {picker.dismiss();owner.pause().stop().destroy();}
    }
  }
  private TextView find(View view,String text) {
    if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
      TextView found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
    }
    return null;
  }
}
