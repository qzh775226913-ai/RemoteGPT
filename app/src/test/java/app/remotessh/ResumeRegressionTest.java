package app.remotessh;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ResumeRegressionTest {
  private RemoteApp app;
  private Method event, apply;
  private RpcClient rpc;
  private static final String LIMIT="You’ve hit your usage limit. Upgrade to Pro or try again at Oct 4th, 2026 11:23 AM.";
  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication(); app.profile=new Profile();
    app.connected=true; app.threadId="thread"; app.setForeground(true);
    rpc=new RpcClient(new java.io.ByteArrayInputStream(new byte[0]),new java.io.ByteArrayOutputStream(),new RpcClient.Events(){
      public void onEvent(JSONObject event){} public void onClosed(Exception error){}
    });
    var field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);
    event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
    apply=RemoteApp.class.getDeclaredMethod("applyThread",JSONObject.class,String.class,boolean.class);apply.setAccessible(true);
  }
  @After public void close(){rpc.close();}
  void emit(String method,JSONObject params) throws Exception {event.invoke(app,Json.obj("method",method,"params",params));}
  void snapshot(JSONObject thread) throws Exception {apply.invoke(app,Json.obj("thread",thread),"thread",false);}
  JSONObject errorParams(String id) {return Json.obj("threadId",id,"turnId","old-turn","error",Json.obj("message",LIMIT));}

  @Test public void replayedUsageFailureStaysInHistoryAndDoesNotRaiseCurrentError() throws Exception {
    emit("remote/replayStarted",Json.obj("threadId","thread"));
    emit("error",errorParams("thread"));
    emit("turn/completed",Json.obj("threadId","thread","turn",Json.obj("id","old-turn","status","failed","error",Json.obj("message",LIMIT))));
    emit("remote/replayCompleted",Json.obj("threadId","thread"));
    assertEquals("",app.error); assertEquals("",app.lastError);
    assertEquals(1,app.items.size()); assertTrue(app.items.get(0).detail.contains(LIMIT));
    assertEquals("历史任务错误",app.items.get(0).text);
  }
  @Test public void liveUsageFailureAfterReplayStillRaisesError() throws Exception {
    emit("remote/replayStarted",Json.obj("threadId","thread"));
    emit("remote/replayCompleted",Json.obj("threadId","thread"));
    emit("error",errorParams("thread"));
    assertEquals(LIMIT,app.error); assertEquals(LIMIT,app.lastError);
    app.error="";
    emit("turn/completed",Json.obj("threadId","thread","turn",Json.obj("status","failed","error",Json.obj("message",LIMIT))));
    assertEquals(LIMIT,app.error);
  }
  @Test public void replayBarrierAlsoCoversOtherConversationJournalTails() throws Exception {
    app.inConversation("other",()->{});
    emit("remote/replayStarted",Json.obj("threadId","thread"));
    emit("error",errorParams("other"));
    app.inConversation("other",()-> {assertEquals("",app.error);assertEquals(1,app.items.size());});
    assertEquals("",app.lastError);
  }
  @Test public void emptyResumeMetadataPreservesExistingTitle() throws Exception {
    app.threadTitle="已有会话标题";
    snapshot(Json.obj("id","thread","name",JSONObject.NULL,"preview","","status",Json.obj("type","idle")));
    assertEquals("已有会话标题",app.threadTitle);
    snapshot(Json.obj("id","thread","name","新的正式标题"));
    assertEquals("新的正式标题",app.threadTitle);
  }
  @Test public void coldResumeFallsBackToListTitleThenFirstUserMessage() throws Exception {
    app.threads=new JSONArray().put(Json.obj("id","other","name","不能串标题"))
        .put(Json.obj("id","thread","name","列表标题"));
    snapshot(Json.obj("id","thread","name",JSONObject.NULL,"preview",""));
    assertEquals("列表标题",app.threadTitle);
    app.threadTitle=""; app.threads=new JSONArray();
    snapshot(Json.obj("id","thread","turns",new JSONArray().put(Json.obj("id","old-turn","status","failed","error",Json.obj("message",LIMIT),"items",
        new JSONArray().put(Json.obj("id","user","type","userMessage","content",new JSONArray().put(Json.obj("type","text","text","最初的用户问题"))))))));
    assertEquals("最初的用户问题",app.threadTitle);
    // Robolectric lacks AndroidKeyStore; ignore its save warning, never a quota alert.
    assertFalse(app.error.contains(LIMIT)); assertFalse(app.lastError.contains(LIMIT));
  }
  @Test public void replayedUserMessageRestoresTitleForEmptyHostedBaseline() throws Exception {
    snapshot(Json.obj("id","thread","name","","preview","","turns",new JSONArray()));
    emit("remote/replayStarted",Json.obj("threadId","thread"));
    emit("item/completed",Json.obj("threadId","thread","item",Json.obj("id","user","type","userMessage","content",
        new JSONArray().put(Json.obj("type","text","text","重放恢复的问题标题")))));
    assertEquals("重放恢复的问题标题",app.threadTitle);
  }
}
