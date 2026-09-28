package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class FinalAuditTest {
  RemoteApp app;
  RpcClient rpc;
  PipedOutputStream server;
  ByteArrayOutputStream sent;
  Method event;
  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication(); app.profile=new Profile();
    app.connected=true; app.threadId="selected"; app.setForeground(true);
    sent=new ByteArrayOutputStream(); server=new PipedOutputStream();
    rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){
      public void onEvent(JSONObject value){} public void onClosed(Exception error){}
    });
    Field f=RemoteApp.class.getDeclaredField("rpc");f.setAccessible(true);f.set(app,rpc);rpc.start();
    event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
  }
  @After public void close() throws Exception {rpc.close();server.close();}
  void emit(String method,JSONObject params) throws Exception {event.invoke(app,Json.obj("method",method,"params",params));}
  void await(java.util.function.BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+3_000_000_000L;
    while(!condition.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
    assertTrue(condition.getAsBoolean());
  }
  JSONObject readRequest() throws Exception {
    await(()->sent.toString().contains("\n"));
    return new JSONObject(sent.toString("UTF-8").split("\n")[0]);
  }
  void respond(JSONObject request,JSONObject result) throws Exception {
    server.write((Json.obj("id",request.opt("id"),"result",result)+"\n").getBytes("UTF-8"));server.flush();
  }
  @Test public void readonlyIdleStatusClearsPendingQuestionAndAllRunningIndicators() throws Exception {
    app.readOnly=true;app.remoteRunning=true;app.busy=true;app.turnId="turn";
    app.approvals.add(Json.obj("id",9,"method","item/tool/requestUserInput"));
    emit("thread/status/changed",Json.obj("threadId","selected","status",Json.obj("type","idle")));
    assertFalse(app.busy);assertTrue(app.approvals.isEmpty());assertNull(app.visibleApproval());
    assertFalse(app.currentThreadRunning());assertFalse(app.threadRunning(Json.obj("id","selected")));
  }
  @Test public void backgroundReplayCompletionDoesNotSendQueuedMessageOrExposeQuestion() throws Exception {
    app.inConversation("other",()->{
      app.busy=true;app.turnId="turn";app.remoteRunning=true;
      assertTrue(app.queueMessage("只能在新任务完成后发送"));
      app.approvals.add(Json.obj("id",9,"method","item/tool/requestUserInput"));
    });
    emit("remote/replayStarted",Json.obj("threadId","selected"));
    assertNull(app.visibleApproval());
    assertFalse(app.threadRunning(Json.obj("id","other")));
    app.inConversation("other",()->assertFalse(LiveStatusNotifications.running(app)));
    emit("turn/completed",Json.obj("threadId","other","turn",Json.obj("id","old","status","completed")));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    app.inConversation("other",()->assertEquals("只能在新任务完成后发送",app.queuedMessage()));
    assertEquals(0,sent.size());
  }
  @Test public void initializationPreservesLegacyRecoveryIdsBeforeSessionReply() throws Exception {
    Set<String> remembered=new HashSet<>(Arrays.asList("A","B"));
    app.getSharedPreferences("sessions",0).edit().putStringSet(app.profile.id,remembered).commit();
    app.connected=false; app.connecting=true;
    app.completeInitialization(rpc,null,0);
    await(()->app.connected);
    assertEquals(remembered,app.getSharedPreferences("sessions",0).getStringSet(app.profile.id,Collections.emptySet()));
    app.changed();
    assertEquals(remembered,app.getSharedPreferences("sessions",0).getStringSet(app.profile.id,Collections.emptySet()));
  }
  @Test public void delayedReadonlySnapshotCannotReviveCompletedTask() throws Exception {
    app.readOnly=true;app.remoteRunning=true;
    app.pollReadOnly(); JSONObject request=readRequest();
    emit("thread/status/changed",Json.obj("threadId","selected","status",Json.obj("type","idle")));
    respond(request,Json.obj("thread",Json.obj("id","selected","status",Json.obj("type","active"))));
    Field polling=RemoteApp.class.getDeclaredField("readPolling");polling.setAccessible(true);
    await(()->{try{return !polling.getBoolean(app);}catch(Exception e){throw new RuntimeException(e);}});
    assertFalse(app.remoteRunning);assertFalse(app.currentThreadRunning());
  }
  @Test public void legacyRefreshDoesNotDuplicatePendingApprovals() throws Exception {
    Field legacy=RemoteApp.class.getDeclaredField("multiSessionHost");legacy.setAccessible(true);legacy.setBoolean(app,false);
    Field loaded=RemoteApp.class.getDeclaredField("loadedThreads");loaded.setAccessible(true);
    ((Set<String>)loaded.get(app)).add("other");
    JSONObject question=Json.obj("id",9,"method","item/tool/requestUserInput","params",Json.obj("threadId","other"));
    app.inConversation("other",()->{app.remoteRunning=true;app.approvals.add(question);});
    Method refresh=RemoteApp.class.getDeclaredMethod("refreshOtherLegacyThreads",String.class);refresh.setAccessible(true);refresh.invoke(app,"selected");
    JSONObject request=readRequest();
    respond(request,Json.obj("thread",Json.obj("id","other","name","已同步","status",Json.obj("type","active"))));
    final boolean[] applied={false};
    await(()->{app.inConversation("other",()->applied[0]=app.threadTitle.equals("已同步"));return applied[0];});
    app.inConversation("other",()->{assertEquals(1,app.approvals.size());assertSame(question,app.approvals.get(0));});
  }
}
