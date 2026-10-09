package app.remotessh;

import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;

public class ConversationJournalTest {
  static JSONObject fixture(String call) {
    String args=Json.obj("questions",new JSONArray().put(Json.obj("title","选择测试方案","options",new JSONArray().put("A").put("B")))).toString();
    return Json.obj("offset",100,"records",new JSONArray().put(Json.obj("type","task_started","turn_id","test"))
        .put(Json.obj("type","function_call","call_id",call,"arguments",args)).put(Json.obj("type","function_call_output","call_id",call,"accepted",true)));
  }
  @Test public void acceptedAsyncToolBecomesSelectableQuestionButNotNativeRpc() throws Exception {
    ConversationJournal journal=new ConversationJournal();journal.apply(fixture("call"),"thread");
    JSONObject req=journal.pending.get("call");assertTrue(ConversationJournal.savedQuestion(req));
    assertEquals("thread",req.getJSONObject("params").getString("threadId"));
    assertEquals("test",req.getJSONObject("params").getString("turnId"));
    JSONObject question=req.getJSONObject("params").getJSONArray("questions").getJSONObject(0);
    assertEquals("选择测试方案",question.getString("question"));assertEquals("A",question.getJSONArray("options").getJSONObject(0).getString("label"));
  }
  @Test public void acceptanceCanArriveInLaterChunkAndNewUserMessageExpiresQuestion() {
    JSONObject fixture=fixture("call");JSONArray records=fixture.optJSONArray("records");
    ConversationJournal journal=new ConversationJournal();journal.apply(Json.obj("records",new JSONArray().put(records.opt(0)).put(records.opt(1))),"thread");
    assertTrue(journal.pending.isEmpty());journal.apply(Json.obj("records",new JSONArray().put(records.opt(2))),"thread");assertEquals(1,journal.pending.size());
    journal.apply(Json.obj("records",new JSONArray().put(Json.obj("type","user_message"))),"thread");assertTrue(journal.pending.isEmpty());
  }
  @Test public void dismissedQuestionDoesNotReappearOnReplay() {
    ConversationJournal journal=new ConversationJournal();journal.apply(fixture("call"),"thread");journal.dismiss(journal.pending.get("call"));
    journal.apply(fixture("call"),"thread");assertTrue(journal.pending.isEmpty());
  }
  @Test public void permissionParsingDoesNotInventOrEscalateUnsupportedPolicy() {
    assertEquals(PermissionMode.FULL,PermissionMode.fromRemote("never",Json.obj("type","danger-full-access")));
    assertEquals(PermissionMode.READ_ONLY,PermissionMode.fromRemote("never",Json.obj("type","readOnly")));
    assertEquals(PermissionMode.DEFAULT,PermissionMode.fromRemote("on-request",Json.obj("type","workspaceWrite")));
    assertNull(PermissionMode.fromRemote(Json.obj("granular",true),Json.obj("type","dangerFullAccess")));
    assertNull(PermissionMode.fromRemote("never",Json.obj("type","externalSandbox")));
  }
}
