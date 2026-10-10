package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class InteractionHistoryTest {
  static JSONObject question(String call,String turn) {
    return Json.obj("method",ConversationJournal.METHOD,"params",Json.obj("callId",call,"turnId",turn,"questions",new JSONArray()
        .put(Json.obj("id","q0","question","选择方案","options",new JSONArray().put(Json.obj("label","A","description","优先兼容")).put(Json.obj("label","B"))))));
  }
  static ChatItem message(String id,String type,String turn,String text) {ChatItem m=new ChatItem(id,type,text);m.turnId=turn;return m;}
  @Test public void answeredQuestionRemainsInCorrectTurnWithoutDuplicateAnswerMessage() throws Exception {
    InteractionHistory h=new InteractionHistory();JSONObject q=question("call","t1");h.question(q);h.answered(q,Json.obj("answers",Json.obj("q0",Json.obj("answers",new JSONArray().put("B")))));
    ChatItem finalReply=message("final","agentMessage","t1","测试题已发出");finalReply.phase="final_answer";
    List<ChatItem> canonical=Arrays.asList(message("u","userMessage","t1","请提问"),finalReply,message("answer","userMessage","t2","回答上一轮的问题：\n选择方案\nB\n\n"));
    List<ChatItem> items=h.present(canonical);
    assertEquals(3,items.size());assertEquals("question",items.get(1).type);assertEquals("answered",items.get(1).status);assertEquals("final",items.get(2).id);
    assertEquals("B",items.get(1).interaction.getJSONObject("answers").getString("q0"));
    assertEquals("userMessage",canonical.get(2).type);
    assertEquals("回答上一轮的问题：\n选择方案\nB\n\n",canonical.get(2).text);
    InteractionHistory restored=new InteractionHistory();restored.restore(h.serialize());assertEquals(3,restored.present(canonical).size());
    assertEquals(0,restored.present(Arrays.asList(message("other","agentMessage","other-turn","另一对话"))).stream().filter(m->m.type.equals("question")).count());
  }
  @Test public void archiveKeepsOldAcceptedQuestionsWithoutMakingThemPendingAgain() {
    InteractionHistory h=new InteractionHistory();String args=Json.obj("questions",new JSONArray().put(Json.obj("title","选择方案","options",new JSONArray().put("A").put("B")))).toString();
    h.archive(Json.obj("history",new JSONArray().put(Json.obj("type","task_started","turn_id","t1")).put(Json.obj("type","function_call","call_id","call","arguments",args))),"thread");
    assertEquals(0,h.present(Arrays.asList(message("m","agentMessage","t1","进展"))).size()-1);
    h.archive(Json.obj("history",new JSONArray().put(Json.obj("type","function_call_output","call_id","call","accepted",true)).put(Json.obj("type","task_started","turn_id","t2")).put(Json.obj("type","user_answer","text","回答上一轮的问题：\n选择方案\nB\n\n"))),"thread");
    JSONObject entry=h.entries.get("call:call");assertEquals("answered",entry.optString("status"));assertEquals("B",Json.object(entry,"answers").optString("q0"));
    assertEquals(2,h.present(Arrays.asList(message("m","agentMessage","t1","进展"))).size());
  }
  @Test public void secretAnswersAreRedactedAndNeverPersistedOrCopied() throws Exception {
    InteractionHistory h=new InteractionHistory();JSONObject req=Json.obj("method","item/tool/requestUserInput","id",1,"params",Json.obj("turnId","t","questions",new JSONArray().put(Json.obj("id","secret","question","输入秘密","isSecret",true))));
    h.question(req);h.answered(req,Json.obj("answers",Json.obj("secret",Json.obj("answers",new JSONArray().put("private-password")))));
    assertFalse(h.serialize().contains("private-password"));assertTrue(h.serialize().contains("不记录内容"));
    h.status(req,"resolved");assertEquals("answered",h.entries.get(InteractionHistory.key(req)).optString("status"));
  }
  @Test public void identicalFutureQuestionDoesNotStealAnEarlierAnswer() {
    InteractionHistory h=new InteractionHistory();h.question(question("first","t1"));h.question(question("later","t3"));
    List<ChatItem> list=h.present(Arrays.asList(message("a","agentMessage","t1","问题"),message("b","userMessage","t2","回答上一轮的问题：\n选择方案\nA\n\n"),message("c","agentMessage","t3","又一个问题")));
    ChatItem first=list.stream().filter(m->m.type.equals("question")&&m.turnId.equals("t1")).findFirst().get();assertEquals("call:first",first.interaction.optString("id"));assertEquals("answered",first.status);assertEquals("A",Json.object(first.interaction,"answers").optString("q0"));
    ChatItem later=list.stream().filter(m->m.type.equals("question")&&m.turnId.equals("t3")).findFirst().get();assertEquals("pending",later.status);
    assertEquals("pending",h.entries.get("call:first").optString("status")); // Presentation does not rewrite the archive.
    assertFalse(list.stream().anyMatch(m->m.id.equals("b")));
  }
  @Test public void nativeAndRolloutCopiesOfSameQuestionProduceOneCard() {
    InteractionHistory h=new InteractionHistory();JSONObject saved=question("call","t1"),nativeQ=Json.obj("id",8,"method","item/tool/requestUserInput","params",Json.object(saved,"params"));
    try{Json.object(nativeQ,"params").remove("callId");}catch(Exception ignored){}
    h.question(nativeQ);h.question(question("call","t1"));
    assertEquals(2,h.present(Arrays.asList(message("a","agentMessage","t1","问题"))).size());
  }
  @Test public void separateAsyncCallsWithSameWordingRemainSeparateHistoryEntries() {
    InteractionHistory h=new InteractionHistory();h.question(question("first","t1"));h.question(question("second","t1"));
    assertEquals(3,h.present(Arrays.asList(message("a","agentMessage","t1","问题"))).size());
  }

  @Test public void missingQuestionOrUnrelatedUserMessageIsNeverHidden() {
    InteractionHistory h=new InteractionHistory();h.question(question("first","t1"));
    ChatItem answer=message("answer","userMessage","t2","回答上一轮的问题：\n选择方案\nA\n\n");
    assertSame(answer,h.present(Arrays.asList(answer)).get(0));
    ChatItem user=message("user","userMessage","t2","回答上一轮的问题：\n其他问题\n继续检查\n\n");
    assertTrue(h.present(Arrays.asList(message("q","agentMessage","t1","问题"),user)).contains(user));
  }

  @Test public void duplicateNativeArchiveEntryStillFoldsAnswerIntoSingleQuestion() {
    InteractionHistory h=new InteractionHistory();JSONObject nativeQ=Json.obj("id",8,"method","item/tool/requestUserInput","params",Json.object(question("call","t1"),"params"));
    Json.object(nativeQ,"params").remove("callId");h.question(nativeQ);h.question(question("call","t1"));
    List<ChatItem> items=h.present(Arrays.asList(message("q","agentMessage","t1","问题"),message("a","userMessage","t2","回答上一轮的问题：\n选择方案\nB\n\n")));
    assertEquals(2,items.size());ChatItem card=items.get(1);assertEquals("question",card.type);assertEquals("answered",card.status);assertEquals("B",Json.object(card.interaction,"answers").optString("q0"));
  }
}
