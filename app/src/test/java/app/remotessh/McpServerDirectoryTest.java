package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class McpServerDirectoryTest {
  private Activity activity;
  private RemoteApp app;
  private RpcClient rpc;
  private PipedOutputStream server;
  private ByteArrayOutputStream sent;

  @Before public void setup() throws Exception {
    activity=Robolectric.buildActivity(Activity.class).setup().get();UiText.initialize(activity);
    app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="A";
    server=new PipedOutputStream();sent=new ByteArrayOutputStream();
    rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events() {
      public void onEvent(JSONObject event) {}
      public void onClosed(Exception error) {}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);
    rpc.start();
  }

  @After public void close() throws Exception {rpc.close();server.close();}

  @Test public void serverNameOpensFullDetailsOnlyAfterSelectingName() throws Exception {
    new ConversationHub(activity,app).show();
    AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    menu.getListView().performItemClick(null,2,2);
    JSONObject first=outgoing("toolsAndAuthOnly");
    assertEquals("A",Json.object(first,"params").optString("threadId"));
    respond(first,Json.obj("data",new JSONArray().put(Json.obj("name","server-one",
        "runtimeStatus","connected","authStatus","oAuth","tools",new JSONObject(),
        "resources",new JSONArray(),"resourceTemplates",new JSONArray())),"nextCursor",JSONObject.NULL));
    AlertDialog list=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    await(() -> findText(list.getWindow().getDecorView(),"server-one") != null);
    findText(list.getWindow().getDecorView(),"server-one").performClick();
    JSONObject second=outgoing("full");
    assertEquals("server-one",Json.object(second,"params").optString("serverName"));
    assertEquals("A",Json.object(second,"params").optString("threadId"));
    respond(second,Json.obj("data",new JSONArray().put(Json.obj("name","server-one",
        "runtimeStatus","connected","authStatus","oAuth","serverInfo",
        Json.obj("name","server-one","version","1.0","description","Detailed server"),
        "tools",new JSONObject(),"resources",new JSONArray().put(Json.obj("name","Doc","uri","mcp://doc")),
        "resourceTemplates",new JSONArray())),"nextCursor",JSONObject.NULL));
    AlertDialog detail=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    await(() -> org.robolectric.Shadows.shadowOf(detail).getMessage().toString().contains("mcp://doc"));
    assertTrue(org.robolectric.Shadows.shadowOf(detail).getMessage().toString().contains("Detailed server"));
  }

  private void respond(JSONObject request,JSONObject response) throws Exception {
    server.write((Json.obj("id",request.opt("id"),"result",response)+"\n").getBytes("UTF-8"));server.flush();
  }

  private JSONObject outgoing(String detail) throws Exception {
    JSONObject[] found={null};
    await(() -> {try {for(String line:sent.toString("UTF-8").split("\n")) {
      if(!line.endsWith("}"))continue;JSONObject request=new JSONObject(line);
      if("mcpServerStatus/list".equals(request.optString("method"))
          && detail.equals(Json.object(request,"params").optString("detail")))found[0]=request;
    }}catch(Exception ignored){}return found[0]!=null;});
    return found[0];
  }

  private TextView findText(View view,String value) {
    if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return (TextView)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
      TextView found=findText(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
    }
    return null;
  }

  private void await(BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+3_000_000_000L;
    while(!condition.getAsBoolean() && System.nanoTime()<end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);
    }
    assertTrue(condition.getAsBoolean());
  }
}
