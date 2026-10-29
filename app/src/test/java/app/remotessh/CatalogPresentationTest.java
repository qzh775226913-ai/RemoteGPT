package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=35,qualifiers="en")
public class CatalogPresentationTest {
  @Test public void explicitNormalTierKeepsRemotePositionNameAndDescription() {
    JSONObject model=Json.obj("serviceTiers",new JSONArray()
        .put(Json.obj("id","priority","name","Priority 3x","description","remote fast text"))
        .put(Json.obj("id","default","name","  Standard server name  ","description","remote normal text"))
        .put(Json.obj("id","economy","name","Economy","description","remote economy text")));
    String original=model.toString();List<JSONObject> options=ServiceTierOptions.choices(model,true,true);
    assertEquals(Arrays.asList("","priority","default","economy"),ids(options));
    assertEquals("  Standard server name  ",ServiceTierOptions.label("default",model));
    assertEquals("remote normal text",ServiceTierOptions.description("default",model));
    assertEquals(Arrays.asList("","default"),ids(ServiceTierOptions.choices(model,true,false)));
    assertEquals(original,model.toString());
  }
  @Test public void legacyTiersRetainIdsWithoutInventingTranslatedNames() {
    JSONObject model=Json.obj("additionalSpeedTiers",new JSONArray().put("fast").put("future"));
    assertEquals(Arrays.asList("","default","fast","future"),ids(ServiceTierOptions.choices(model,true,true)));
    assertEquals("fast",ServiceTierOptions.label("fast",model));
    assertEquals("Standard",ServiceTierOptions.label("default",model));
  }
  @Test public void defaultFlagDoesNotReorderServerModelPages() throws Exception {
    JSONArray catalog=ModelCatalog.load(params->params.has("cursor")
        ?Json.obj("data",new JSONArray().put(Json.obj("model","middle","isDefault",true)))
        :Json.obj("data",new JSONArray().put(Json.obj("model","z-last-alphabetically")).put(Json.obj("model","a-first-alphabetically")),"nextCursor","next"));
    List<String> ids=new ArrayList<>();for(int i=0;i<catalog.length();i++)ids.add(ModelOptions.id(catalog.getJSONObject(i)));
    assertEquals(Arrays.asList("z-last-alphabetically","a-first-alphabetically","middle"),ids);
  }
  @Test public void pickerPreservesRemoteModelAndSpeedRows() {
    RemoteApp app=(RemoteApp)org.robolectric.RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.model="z";
    app.models=new JSONArray().put(Json.obj("model","z","serviceTiers",new JSONArray()
        .put(Json.obj("id","priority","name","Server fast","description","fast description"))
        .put(Json.obj("id","default","name","Server normal","description","normal description"))
        .put(Json.obj("id","economy","name","Server economy"))))
        .put(Json.obj("model","a")).put(Json.obj("model","remote-default","isDefault",true));
    var owner=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup();
    ModelPicker picker=new ModelPicker(owner.get(),app,new DialogUi(owner.get(),-1,0xff000000,0xff777777,0xffdddddd,0xffcccccc),false);
    try {
      picker.show();android.view.View shell=org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
      List<String> models=new ArrayList<>(),speeds=new ArrayList<>();
      collect(shell,"Select model: ",models);collect(shell,"Select speed: ",speeds);
      assertEquals(Arrays.asList("","z","a","remote-default"),models);
      assertEquals(Arrays.asList("Remote default","priority","default","economy"),speeds);
      android.view.View normal=find(shell,"Select speed: default");assertNotNull(normal);normal.performClick();
      assertTrue(allText(shell).contains("normal description"));
      assertTrue(allText(shell).contains("Server normal"));
    } finally {picker.dismiss();owner.pause().stop().destroy();}
  }
  private void collect(android.view.View view,String prefix,List<String> output) {
    CharSequence value=view.getContentDescription();
    if(value!=null && value.toString().startsWith(prefix))output.add(value.toString().substring(prefix.length()));
    if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)collect(((android.view.ViewGroup)view).getChildAt(i),prefix,output);
  }
  private android.view.View find(android.view.View view,String description) {
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
    if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
      android.view.View child=find(((android.view.ViewGroup)view).getChildAt(i),description);if(child!=null)return child;
    }
    return null;
  }
  private String allText(android.view.View view) {
    StringBuilder result=new StringBuilder(view instanceof android.widget.TextView?((android.widget.TextView)view).getText():"");
    if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)result.append('\n').append(allText(((android.view.ViewGroup)view).getChildAt(i)));
    return result.toString();
  }
  private List<String> ids(List<JSONObject> options) {
    List<String> result=new ArrayList<>();for(JSONObject option:options)result.add(option.optString("id"));return result;
  }
}
