package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
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
public class QuestionRecoveryTest {
  RemoteApp app; RpcClient rpc; PipedOutputStream server; ByteArrayOutputStream sent;
  Method event, apply;
  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.connected=true;app.threadId="thread";app.setForeground(true);
    server=new PipedOutputStream();sent=new ByteArrayOutputStream();rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){public void onEvent(JSONObject e){} public void onClosed(Exception e){}});
    Field f=RemoteApp.class.getDeclaredField("rpc");f.setAccessible(true);f.set(app,rpc);rpc.start();
    event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
    apply=RemoteApp.class.getDeclaredMethod("applyThread",JSONObject.class,String.class,boolean.class);apply.setAccessible(true);
  }
  @After public void close() throws Exception {rpc.close();server.close();}
  void await(java.util.function.BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+3_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}assertTrue(condition.getAsBoolean());
  }
  JSONObject outgoing(String method) throws Exception {
    JSONObject[] found={null};await(()->{try{for(String line:sent.toString("UTF-8").split("\n")){if(!line.endsWith("}"))continue;JSONObject j=new JSONObject(line);if(j.optString("method").equals(method))found[0]=j;}}catch(Exception ignored){}return found[0]!=null;});return found[0];
  }
  void respond(JSONObject request,JSONObject result,boolean error) throws Exception {server.write((Json.obj("id",request.opt("id"),error?"error":"result",result)+"\n").getBytes("UTF-8"));server.flush();}
  JSONObject question() {app.journal.apply(ConversationJournalTest.fixture("call"),"thread");app.syncJournalQuestions();app.changed();return app.approvals.get(0);}
  JSONObject answer() {return Json.obj("answers",Json.obj("q0",Json.obj("answers",new JSONArray().put("B"))));}
  JSONObject snapshot() {return Json.obj("approvalPolicy","never","sandbox",Json.obj("type","dangerFullAccess"),"thread",Json.obj("id","thread","status",Json.obj("type","idle"),"turns",new JSONArray()));}

  @Test public void resumingReadOnlyHistoryRetainsPreparedTextPicturesAndFilesWithoutAutoSend() throws Exception {
    app.readOnly=true;app.draftText="预先输入";app.draftImages.add("/uploads/draft.png");
    FileAttachment file=new FileAttachment("draft.pdf","/uploads/draft.pdf",10);app.draftFiles.add(file);
    app.resume("thread");JSONObject request=outgoing("thread/resume");respond(request,snapshot(),false);
    await(()->!app.recoveryInProgress() && !app.readOnly);
    assertEquals("预先输入",app.draftText);assertEquals(Collections.singletonList("/uploads/draft.png"),app.draftImages);assertSame(file,app.draftFiles.get(0));
    assertFalse(sent.toString("UTF-8").contains("turn/start"));assertFalse(app.busy);
  }

  @Test public void completedAsyncQuestionShowsReminderAndSurvivesIdleSnapshot() throws Exception {
    app.busy=true;app.turnId="test";app.liveStatus.observe();
    event.invoke(app,Json.obj("method","turn/completed","params",Json.obj("threadId","thread","turn",Json.obj("status","completed"))));
    JSONObject req=question();assertEquals("待回答",LiveStatusNotifications.stage(app));assertTrue(app.currentThreadRunning());
    apply.invoke(app,snapshot(),"thread",true);assertSame(req,app.visibleApproval());
    app.liveStatus.update();Notification notification=Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(app.liveStatus.idFor("thread"));
    assertNotNull(notification);assertEquals(Notification.CATEGORY_REMINDER,notification.category);
    assertTrue(notification.extras.getString(Notification.EXTRA_TITLE).startsWith("待回答"));
    assertEquals("填写信息",notification.actions[0].title.toString());
    assertTrue(notification.extras.getBoolean(LiveStatusNotifications.PROMOTED));
    assertTrue(ConnectionService.summary(app).startsWith("等待回答"));
    event.invoke(app,Json.obj("method","thread/status/changed","params",Json.obj("threadId","thread","status",Json.obj("type","idle"))));
    assertSame(req,app.visibleApproval());assertFalse(app.busy);assertTrue(app.currentThreadRunning());
  }
  @Test public void backgroundAndCompletionKeepSelectedWriter() throws Exception {
    Field loaded=RemoteApp.class.getDeclaredField("loadedThreads");loaded.setAccessible(true);((Set<String>)loaded.get(app)).add("thread");
    app.setForeground(false);assertFalse(app.readOnly);
    app.busy=true;app.turnId="test";
    event.invoke(app,Json.obj("method","turn/completed","params",Json.obj("threadId","thread","turn",Json.obj("status","completed"))));
    Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(app.readOnly);
    assertFalse(sent.toString().contains("thread/unsubscribe"));
  }
  @Test public void savedQuestionAnswerUsesNewTurnWithoutImagesOrFakeRpcResponse() throws Exception {
    JSONObject req=question();app.draftImages.add("/tmp/unrelated.png");app.answer(req,answer());app.answer(req,answer());
    JSONObject request=outgoing("turn/start");assertEquals(1,request.getJSONObject("params").getJSONArray("input").length());
    assertTrue(request.getJSONObject("params").getJSONArray("input").getJSONObject(0).getString("text").contains("\nB\n"));
    assertFalse(sent.toString().contains("saved-call"));
    respond(request,Json.obj("turn",Json.obj("id","next","status","inProgress")),false);await(()->app.approvals.isEmpty());
    assertFalse(app.isAnswering(req));assertEquals(1,app.draftImages.size());
    app.journal=new ConversationJournal();app.journal.apply(ConversationJournalTest.fixture("call"),"thread");app.syncJournalQuestions();assertTrue(app.approvals.isEmpty());
  }
  @Test public void failedAnswerKeepsQuestionForRetry() throws Exception {
    JSONObject req=question();app.answer(req,answer());JSONObject request=outgoing("turn/start");
    respond(request,Json.obj("code",-32600,"message","not ready"),true);await(()->!app.isAnswering(req));
    assertSame(req,app.visibleApproval());assertFalse(app.journal.dismissed.contains("call"));assertFalse(app.busy);
  }
  @Test public void readOnlyQuestionResumesBeforeSubmittingAndRunningQuestionSteers() throws Exception {
    JSONObject req=question();app.readOnly=true;app.answer(req,answer());JSONObject resume=outgoing("thread/resume");
    assertFalse(sent.toString().contains("turn/start"));respond(resume,snapshot(),false);JSONObject start=outgoing("turn/start");
    respond(start,Json.obj("turn",Json.obj("id","run","status","inProgress")),false);await(()->app.approvals.isEmpty());
    app.journal.apply(ConversationJournalTest.fixture("second"),"thread");app.syncJournalQuestions();app.busy=true;app.turnId="run";
    app.answer(app.approvals.get(0),answer());JSONObject steer=outgoing("turn/steer");assertEquals("run",steer.getJSONObject("params").getString("expectedTurnId"));
    respond(steer,Json.obj("turnId","run"),false);await(()->app.approvals.isEmpty());assertTrue(app.busy);
  }
  @Test public void resumeShowsRemotePermissionAndRestoresThreadChoiceWithoutCrossThreadLeak() throws Exception {
    apply.invoke(app,snapshot(),"thread",false);assertEquals(PermissionMode.INHERIT,app.permissionMode);assertEquals(PermissionMode.FULL,app.displayedPermission());
    JSONObject params=new JSONObject();app.permissionMode.applyTurn(params);assertEquals(0,params.length());
    app.choosePermission(PermissionMode.READ_ONLY);
    Field cache=RemoteApp.class.getDeclaredField("conversations");cache.setAccessible(true);((Map<?,?>)cache.get(app)).clear();app.threadId="";app.permissionMode=PermissionMode.INHERIT;
    apply.invoke(app,snapshot(),"thread",false);assertEquals(PermissionMode.READ_ONLY,app.permissionMode);
    app.inConversation("other",()->assertEquals(PermissionMode.INHERIT,app.permissionMode));assertEquals(PermissionMode.READ_ONLY,app.permissionMode);
  }

  @Test public void nativeQuestionHistorySurvivesClearedConversationCache() throws Exception {
    JSONObject req=Json.obj("id",18,"method","item/tool/requestUserInput","params",Json.obj("threadId","thread","turnId","test","questions",new JSONArray().put(Json.obj("id","q0","question","选择方案"))));
    event.invoke(app,req);app.answer(req,answer());await(()->app.approvals.isEmpty());
    await(()->!app.getSharedPreferences("interaction-history",0).getAll().isEmpty());
    assertEquals("answered",app.interactions.entries.get(InteractionHistory.key(req)).optString("status"));
    Field cache=RemoteApp.class.getDeclaredField("conversations");cache.setAccessible(true);((Map<?,?>)cache.get(app)).clear();app.threadId="";app.interactions=new InteractionHistory();
    JSONObject restored=snapshot();Json.object(restored,"thread").put("turns",new JSONArray().put(Json.obj("id","test","items",new JSONArray().put(Json.obj("id","m","type","agentMessage","phase","final_answer","text","结果")))));
    apply.invoke(app,restored,"thread",true);
    assertEquals(2,app.displayItems().size());assertEquals("question",app.displayItems().get(0).type);assertEquals("answered",app.displayItems().get(0).status);
    app.inConversation("other",()->assertTrue(app.interactions.entries.isEmpty()));
  }
}
