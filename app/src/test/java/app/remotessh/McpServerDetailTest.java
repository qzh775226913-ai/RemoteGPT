package app.remotessh;

import static org.junit.Assert.*;
import android.app.Activity;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class McpServerDetailTest {
  @Test public void fullInventoryIncludesMetadataToolsResourcesAndTemplates() throws Exception {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    UiText.initialize(activity);
    JSONObject server=new JSONObject()
        .put("name","sample")
        .put("runtimeStatus","connected")
        .put("authStatus","oAuth")
        .put("pluginId","plugin-1")
        .put("httpOrigin","https://example.test")
        .put("serverInfo",new JSONObject().put("title","Example MCP")
            .put("version","2.1").put("description","Server description")
            .put("websiteUrl","https://example.test/about"))
        .put("tools",new JSONObject().put("search",new JSONObject()
            .put("name","search").put("description","Find documents")))
        .put("resources",new JSONArray().put(new JSONObject().put("name","Guide")
            .put("uri","docs://guide").put("description","Reference material")))
        .put("resourceTemplates",new JSONArray().put(new JSONObject().put("name","Item")
            .put("uriTemplate","docs://item/{id}")))
        .put("serverCapabilities",new JSONObject().put("resources",new JSONObject().put("subscribe",true)));
    String detail=McpServerDetail.describe(server);
    for(String expected:new String[]{"connected","oAuth","plugin-1","https://example.test",
        "Example MCP","2.1","Server description","search","Find documents","Guide",
        "docs://guide","Reference material","Item","docs://item/{id}","subscribe"})
      assertTrue(expected,detail.contains(expected));
    assertTrue(detail.contains(UiText.get(R.string.ui_mcp_tools_count,1)));
    assertTrue(detail.contains(UiText.get(R.string.ui_mcp_resources_count,1)));
  }
}
