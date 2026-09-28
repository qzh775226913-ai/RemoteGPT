package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ActivityStateRegressionTest {
  RemoteApp app;
  @Before public void setup() {
    app=(RemoteApp) RuntimeEnvironment.getApplication();
    app.connected=true; app.profile=new Profile(); app.threadId="history";
  }
  void emit(String method, JSONObject params) throws Exception {
    Method event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);
    event.setAccessible(true); event.invoke(app,Json.obj("method",method,"params",params));
  }
  @Test public void readingAndReplayNeverAppearAsRunning() {
    app.busy=true;
    assertFalse(app.threadRunning(Json.obj("id","history")));
    assertFalse(LiveStatusNotifications.running(app));
    app.remoteRunning=true; app.replaying=true;
    assertFalse(app.threadRunning(Json.obj("id","history")));
    assertFalse(LiveStatusNotifications.running(app));
  }
  @Test public void completedCachedThreadOverridesStaleListStatus() throws Exception {
    app.busy=true; app.remoteRunning=true; app.turnId="turn"; app.changed();
    JSONObject stale=Json.obj("id","history","status",Json.obj("type","active"));
    emit("turn/completed",Json.obj("threadId","history","turn",Json.obj("status","completed")));
    assertFalse(app.threadRunning(stale));
    assertFalse(LiveStatusNotifications.running(app));
    app.newChat(); assertFalse(app.threadRunning(stale));
  }
  @Test public void backgroundStatusFeedsBothListAndLiveCloud() throws Exception {
    JSONObject params=Json.obj("threadId","other","status",Json.obj("type","active"));
    emit("thread/status/changed",params);
    assertTrue(app.threadRunning(Json.obj("id","other")));
    app.inConversation("other",()->assertTrue(LiveStatusNotifications.running(app)));
    emit("thread/status/changed",Json.obj("threadId","other","status",Json.obj("type","idle")));
    assertFalse(app.threadRunning(Json.obj("id","other")));
    app.inConversation("other",()->assertFalse(LiveStatusNotifications.running(app)));
  }
  @Test public void backgroundQuestionsAreVisibleWithoutSwitchingConversation() throws Exception {
    JSONObject question=Json.obj("id",12,"method","item/tool/requestUserInput","params",
        Json.obj("threadId","other","questions",new JSONArray().put(Json.obj("id","q","question","选择一个环境"))));
    Method event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class); event.setAccessible(true); event.invoke(app,question);
    assertSame(question,app.visibleApproval()); assertEquals("history",app.threadId);
    var activity=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      assertNotNull(dialog); assertTrue(dialog.isShowing());
      assertEquals("补充信息",Shadows.shadowOf(dialog).getTitle().toString());
    } finally { activity.pause().stop().destroy(); }
  }
  @Test public void passiveBackgroundCompletionRemovesRunningState() throws Exception {
    PipedOutputStream server=new PipedOutputStream(); ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){} public void onClosed(Exception e){}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);rpc.start();
    try {
      app.inConversation("passive",()-> {app.remoteRunning=true; app.readOnly=true;});
      app.pollPassiveStatus();
      long deadline=System.nanoTime()+3_000_000_000L;
      while(sent.size()==0 && System.nanoTime()<deadline) Thread.sleep(5);
      JSONObject request=new JSONObject(sent.toString("UTF-8").trim());
      assertEquals("thread/read",request.getString("method"));
      assertEquals("passive",request.getJSONObject("params").getString("threadId"));
      server.write((Json.obj("id",request.opt("id"),"result",Json.obj("thread",Json.obj("id","passive","status",Json.obj("type","idle"))))+"\n").getBytes("UTF-8"));
      server.flush();
      deadline=System.nanoTime()+3_000_000_000L;
      while(app.threadRunning(Json.obj("id","passive")) && System.nanoTime()<deadline) {
        Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5);
      }
      assertFalse(app.threadRunning(Json.obj("id","passive")));
      app.inConversation("passive",()->assertFalse(LiveStatusNotifications.running(app)));
      assertEquals("history",app.threadId);
    } finally { rpc.close(); server.close(); }
  }

  @Test public void browsingOnlySendsReadAndIdleExitUnsubscribes() throws Exception {
    ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new ByteArrayInputStream(new byte[0]),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){} public void onClosed(Exception e){}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);
    try {
      app.viewConversation("viewed");
      long deadline=System.nanoTime()+3_000_000_000L;
      while(sent.size()==0 && System.nanoTime()<deadline) Thread.sleep(5);
      JSONObject request=new JSONObject(sent.toString("UTF-8").trim());
      assertEquals("thread/read",request.getString("method"));
      assertFalse(app.currentThreadRunning());
      Field loaded=RemoteApp.class.getDeclaredField("loadedThreads");loaded.setAccessible(true);
      ((java.util.Set<String>)loaded.get(app)).add("viewed");
      app.busy=false; app.leaveIdleConversation();
      deadline=System.nanoTime()+3_000_000_000L;
      while(!sent.toString().contains("unsubscribe") && System.nanoTime()<deadline) Thread.sleep(5);
      assertTrue(sent.toString().contains("unsubscribe")); assertTrue(app.readOnly);
      assertFalse(sent.toString().contains("thread/resume"));
    } finally {rpc.close();}
  }
}
