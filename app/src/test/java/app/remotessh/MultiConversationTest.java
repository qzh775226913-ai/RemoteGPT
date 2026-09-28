package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.os.Looper;
import java.lang.reflect.*;
import java.io.*;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MultiConversationTest {
  RemoteApp app;
  Method event;
  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication(); app.profile=new Profile(); app.connected=true;
    app.threadId="A"; app.threadTitle="任务 A"; app.busy=true; app.turnId="turn-A";
    app.items.add(new ChatItem("same", "agentMessage", "A:")); app.changed();
    event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class); event.setAccessible(true);
  }
  void emit(String method, String thread, Object... values) throws Exception {
    JSONObject params=Json.obj(values); if (thread!=null) params.put("threadId",thread);
    event.invoke(app,Json.obj("method",method,"params",params));
  }
  void second() {
    app.newChat(); assertEquals("", app.threadId); assertFalse(app.busy);
    // Simulate the thread/start result without a real model invocation.
    app.threadId="B"; app.threadTitle="任务 B"; app.busy=true; app.turnId="turn-B";
    app.items.add(new ChatItem("same","agentMessage","B:")); app.changed();
  }
  @Test public void simultaneousDeltasAndCompletionStayInTheirThread() throws Exception {
    second(); emit("item/agentMessage/delta","A","itemId","same","delta","后台输出");
    assertEquals("B:", app.items.get(0).text); assertEquals("B",app.threadId);
    app.inConversation("A",()->assertEquals("A:后台输出",app.items.get(0).text));
    emit("turn/completed","A","turn",Json.obj("status","completed"));
    assertTrue(app.busy); assertEquals("turn-B",app.turnId);
    app.inConversation("A",()->assertFalse(app.busy));
  }
  @Test public void twoNotificationsUseSameStyleAndDifferentDestinations() {
    second(); var service=Robolectric.buildService(ConnectionService.class).create();
    try {
      NotificationManager nm=app.getSystemService(NotificationManager.class);
      var shadow=Shadows.shadowOf(nm);
      Notification a=shadow.getNotification(app.liveStatus.idFor("A"));
      Notification b=shadow.getNotification(app.liveStatus.idFor("B"));
      assertNotNull(a); assertNotNull(b); assertNotEquals(a.contentIntent,b.contentIntent);
      assertEquals(a.extras.getString(Notification.EXTRA_TEMPLATE),b.extras.getString(Notification.EXTRA_TEMPLATE));
      assertTrue(a.actions==null || a.actions.length==0); assertTrue(b.actions==null || b.actions.length==0);
      int bubbles=0;
      for (Notification n:shadow.getAllNotifications()) if(n.getBubbleMetadata()!=null) bubbles++;
      assertEquals(2,bubbles); assertEquals("B",app.threadId);
    } finally { service.destroy(); }
  }
  @Test public void backgroundApprovalAnswersCorrectRequestWithoutSwitching() throws Exception {
    second(); ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new ByteArrayInputStream(new byte[0]),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){} public void onClosed(Exception e){}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);
    try {
      JSONObject req=Json.obj("id",19,"method","item/commandExecution/requestApproval","params",Json.obj("threadId","A","command","pwd"));
      event.invoke(app,req); assertTrue(app.approvals.isEmpty()); app.liveStatus.update();
      Notification n=Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(app.liveStatus.idFor("A"));
      n.actions[0].actionIntent.send();
      long deadline=System.nanoTime()+3_000_000_000L;
      while(sent.size()==0 && System.nanoTime()<deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5); }
      assertEquals(19,new JSONObject(sent.toString("UTF-8").trim()).getInt("id"));
      assertEquals("B",app.threadId); assertTrue(app.busy);
    } finally { rpc.close(); }
  }
  @Test public void backgroundEventsNeverExposeTemporaryContextToUi() throws Exception {
    second(); app.observer=()->assertEquals("B",app.threadId);
    emit("turn/started","A","turn",Json.obj("id","next-A"));
    emit("item/agentMessage/delta","A","itemId","same","delta","更多");
    assertEquals("turn-B",app.turnId);
    app.disconnect(); assertTrue(app.allApprovals().isEmpty());
  }
  @Test public void cachedConversationSwitchPreservesDraftAndRunningTurn() throws Exception {
    Field loaded=RemoteApp.class.getDeclaredField("loadedThreads"); loaded.setAccessible(true);
    ((java.util.Set<String>)loaded.get(app)).add("A");
    app.draftText="A 的未发草稿";
    second(); ((java.util.Set<String>)loaded.get(app)).add("B");
    app.draftText="B 的未发草稿";
    app.resume("A"); assertEquals("A",app.threadId); assertTrue(app.busy);
    assertEquals("turn-A",app.turnId); assertEquals("A 的未发草稿",app.draftText);
    app.resume("B"); assertEquals("B",app.threadId); assertTrue(app.busy);
    assertEquals("B 的未发草稿",app.draftText);
  }

  @Test public void delayedTurnResponseCannotOverwriteSelectedThread() throws Exception {
    app.busy=false; app.turnId="";
    PipedOutputStream server=new PipedOutputStream(); ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){} public void onClosed(Exception e){}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app,rpc);rpc.start();
    try {
      app.send("A 的任务");
      long deadline=System.nanoTime()+3_000_000_000L;
      while(sent.size()==0 && System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
      JSONObject request=new JSONObject(sent.toString("UTF-8").trim());
      second();
      server.write((Json.obj("id",request.opt("id"),"result",Json.obj("turn",Json.obj("id","server-A")))+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); server.flush();
      final boolean[] received={false};
      deadline=System.nanoTime()+3_000_000_000L;
      while(!received[0] && System.nanoTime()<deadline){
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        app.inConversation("A",()->received[0]=app.turnId.equals("server-A")); Thread.sleep(5);
      }
      assertTrue(received[0]); assertEquals("B",app.threadId); assertEquals("turn-B",app.turnId);
    } finally {rpc.close();server.close();}
  }

  @Test public void reconnectRestoresEveryHostedThreadAndKeepsSelectedConversation() throws Exception {
    app.busy=false;
    PipedOutputStream server=new PipedOutputStream(); ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){} public void onClosed(Exception e){}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);rpc.start();
    try {
      app.restoreSession(Json.obj("lastThread","B","multiSession",true,"threads",new JSONArray().put("A").put("B")),"A");
      for (int i=0;i<2;i++) {
        final int count=i+1;
        long deadline=System.nanoTime()+3_000_000_000L;
        while(sent.toString().split("\n").length<count && System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
        // A notification acknowledgement adds another line after each response.
        JSONObject request=null;
        boolean readAnswered=false;
        deadline=System.nanoTime()+3_000_000_000L;
        while(request==null && System.nanoTime()<deadline){
          Shadows.shadowOf(Looper.getMainLooper()).idle();
          for(String line:sent.toString("UTF-8").split("\n")) if(!line.isEmpty()) {
            JSONObject candidate=new JSONObject(line);
            String target = i==0?"A":"B";
            if (!readAnswered && candidate.optString("method").equals("thread/read") && Json.object(candidate,"params").optString("threadId").equals(target)) {
              server.write((Json.obj("id",candidate.opt("id"),"result",Json.obj("thread",Json.obj("id",target,"status",Json.obj("type","active"))))+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
              server.flush(); readAnswered=true;
            }
            if(candidate.optString("method").equals("thread/resume") && Json.object(candidate,"params").optString("threadId").equals(i==0?"A":"B")) request=candidate;
          }
          Thread.sleep(5);
        }
        assertNotNull(request); String id=i==0?"A":"B";
        JSONObject result=Json.obj("remoteReplay",true,"thread",Json.obj("id",id,"status",Json.obj("type","active"),"turns",new JSONArray()));
        server.write((Json.obj("id",request.opt("id"),"result",result)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));server.flush();
        deadline=System.nanoTime()+3_000_000_000L;
        final boolean[] applied={false};
        while(!applied[0] && System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();app.inConversation(id,()->applied[0]=app.replaying);Thread.sleep(5);}
        assertTrue(applied[0]);
        emit("turn/started",id,"turn",Json.obj("id","restored-"+id));
        emit("remote/replayCompleted",id);
      }
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals("A",app.threadId);assertEquals("restored-A",app.turnId);
      app.inConversation("B",()->assertEquals("restored-B",app.turnId));
      assertTrue(app.canSwitchConversation());
    } finally {rpc.close();server.close();}
  }

}
