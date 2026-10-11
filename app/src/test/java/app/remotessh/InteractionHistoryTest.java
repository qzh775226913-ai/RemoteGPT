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

  @Test public void asyncDeliveryEchoFoldsBeforeAndAfterAnswerAndRestore() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("call","t1");h.question(req);
    ChatItem echo=message("call","agentMessage","t1","选择方案");echo.phase="final_answer";
    List<ChatItem> canonical=new ArrayList<>();canonical.add(echo);
    assertEquals(1,h.present(canonical).size());assertEquals("pending",h.present(canonical).get(0).status);
    canonical.add(message("answer","userMessage","t2","回答上一轮的问题：\n选择方案\nB\n\n"));
    List<ChatItem> shown=h.present(canonical);assertEquals(1,shown.size());assertEquals("answered",shown.get(0).status);
    assertEquals("B",Json.object(shown.get(0).interaction,"answers").optString("q0"));
    InteractionHistory restored=new InteractionHistory();restored.restore(h.serialize());
    assertEquals(1,restored.present(canonical).size());assertEquals("answered",restored.present(canonical).get(0).status);
    assertEquals(2,canonical.size());assertEquals("选择方案",echo.text);
  }

  @Test public void echoMatchingRequiresCallTurnFullTextAndNoImages() {
    InteractionHistory h=new InteractionHistory();h.question(question("call","t1"));
    ChatItem genuine=message("call","agentMessage","t1","选择方案\n我建议选择 B，因为兼容性更好。");
    ChatItem otherCall=message("different","agentMessage","t1","选择方案");
    ChatItem otherTurn=message("call","agentMessage","t2","选择方案");
    ChatItem picture=message("call","agentMessage","t1","选择方案");picture.images.add("/result.png");
    for(ChatItem m:Arrays.asList(genuine,otherCall,otherTurn,picture))assertTrue(h.present(Arrays.asList(m)).contains(m));
    assertTrue(h.present(Arrays.asList(message("call","agentMessage","t1","选择方案"),otherTurn)).contains(otherTurn));
    ChatItem user=message("call","userMessage","t1","选择方案");assertTrue(h.present(Arrays.asList(user)).contains(user));
    h.status(question("call","t1"),"unavailable");
    ChatItem echo=message("call","agentMessage","t1","选择方案");assertSame(echo,h.present(Arrays.asList(echo)).get(0));
  }

  @Test public void nativeArchiveAliasesFoldAsyncEchoAndKeepActualReply() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("call","t1");
    JSONObject nativeQ=Json.obj("id",8,"method","item/tool/requestUserInput","params",Json.object(req,"params"));
    Json.object(nativeQ,"params").remove("callId");h.question(nativeQ);h.question(question("call","t1"));
    ChatItem echo=message("call","agentMessage","t1","选择方案");echo.phase="final_answer";
    ChatItem reply=message("result","agentMessage","t2","已收到选择，开始执行。");reply.phase="final_answer";
    List<ChatItem> shown=h.present(Arrays.asList(echo,message("a","userMessage","t2","回答上一轮的问题：\n选择方案\nA\n\n"),reply));
    assertEquals(2,shown.size());assertEquals("question",shown.get(0).type);assertSame(reply,shown.get(1));
    assertEquals("A",Json.object(shown.get(0).interaction,"answers").optString("q0"));
  }

  @Test public void multiQuestionEchoMustContainEveryQuestion() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("call","t1");
    Json.object(req,"params").optJSONArray("questions").put(Json.obj("id","q1","question","补充城市"));h.question(req);
    ChatItem echo=message("call","agentMessage","t1","选择方案\r\n\r\n补充城市\n");
    assertEquals("question",h.present(Arrays.asList(echo)).get(0).type);assertEquals(1,h.present(Arrays.asList(echo)).size());
    ChatItem partial=message("call","agentMessage","t1","选择方案");assertTrue(h.present(Arrays.asList(partial)).contains(partial));
  }

  @Test public void questionEchoWithOptionBulletsFoldsAfterAnswerAndArchiveRestore() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("call","t1");
    Json.object(req,"params").optJSONArray("questions").put(Json.obj("id","q1","question","补充设备型号"));h.question(req);
    h.answered(req,Json.obj("answers",Json.obj("q0",Json.obj("answers",new JSONArray().put("B")),"q1",Json.obj("answers",new JSONArray().put("稍后补充")))));
    ChatItem echo=message("call","agentMessage","t1","选择方案\n\n- A\n- B\n\n补充设备型号");echo.phase="final_answer";
    for(InteractionHistory history:Arrays.asList(h,restored(h))) {
      List<ChatItem> shown=history.present(Arrays.asList(echo));
      assertEquals(1,shown.size());assertEquals("question",shown.get(0).type);assertEquals("answered",shown.get(0).status);
      assertEquals("B",Json.object(shown.get(0).interaction,"answers").optString("q0"));
    }
    assertEquals("agentMessage",echo.type);assertTrue(echo.text.contains("- A"));
  }

  @Test public void optionEchoRequiresAllOptionsAndRetainsActualRepliesAndOtherIdentities() {
    InteractionHistory h=new InteractionHistory();h.question(question("call","t1"));
    for(String text:Arrays.asList("选择方案\n- A", "选择方案\n- A\n- B\n我建议选择 B。", "选择方案\n- B\n- A", "选择方案\n- A\n- C")) {
      ChatItem actual=message("call","agentMessage","t1",text);assertTrue(h.present(Arrays.asList(actual)).contains(actual));
    }
    ChatItem other=message("other","agentMessage","t1","选择方案\n- A\n- B");assertTrue(h.present(Arrays.asList(other)).contains(other));
    ChatItem numbered=message("call","agentMessage","t1","**选择方案**\r\n1. A\r\n2. B");
    assertEquals(1,h.present(Arrays.asList(numbered)).size());assertEquals("question",h.present(Arrays.asList(numbered)).get(0).type);
  }

  private static InteractionHistory restored(InteractionHistory source) {
    InteractionHistory history=new InteractionHistory();history.restore(source.serialize());return history;
  }

  private static List<String> ids(List<ChatItem> messages) {
    List<String> result=new ArrayList<>();for(ChatItem item:messages)result.add(item.id);return result;
  }

  @Test public void answeringAndContinuingTheSameTurnDoesNotMoveItsEarlierQuestion() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("call","t1");h.question(req);
    List<ChatItem> messages=new ArrayList<>();messages.add(message("call","agentMessage","t1","选择方案"));
    assertEquals(Arrays.asList("interaction-call:call"),ids(h.present(messages)));
    h.answered(req,Json.obj("answers",Json.obj("q0",Json.obj("answers",new JSONArray().put("B")))));
    messages.add(message("work","commandExecution","t1","继续执行"));
    ChatItem finalReply=message("final","agentMessage","t1","执行完毕");finalReply.phase="final_answer";messages.add(finalReply);
    messages.add(message("next","userMessage","t2","继续下一个任务"));
    for(InteractionHistory history:Arrays.asList(h,restored(h))) {
      assertEquals(Arrays.asList("interaction-call:call","work","final","next"),ids(history.present(messages)));
      assertEquals("answered",history.present(messages).get(0).status);
    }
  }

  @Test public void repeatedQuestionInSameTurnCannotStealAnAnswerBeforeItsOwnCall() {
    InteractionHistory h=new InteractionHistory();h.question(question("first","t1"));h.question(question("second","t1"));
    ChatItem finalReply=message("final","agentMessage","t1","执行结束");finalReply.phase="final_answer";
    List<ChatItem> canonical=Arrays.asList(message("first","agentMessage","t1","选择方案"),
        message("answer","userMessage","t1","回答上一轮的问题：\n选择方案\nB\n\n"),
        message("work","commandExecution","t1","继续执行"),message("second","agentMessage","t1","选择方案"),finalReply);
    List<ChatItem> shown=h.present(canonical);
    assertEquals(Arrays.asList("interaction-call:first","work","interaction-call:second","final"),ids(shown));
    assertEquals("answered",shown.get(0).status);assertEquals("B",Json.object(shown.get(0).interaction,"answers").optString("q0"));
    assertEquals("pending",shown.get(2).status);
    assertEquals("pending",h.entries.get("call:first").optString("status"));
  }

  @Test public void missingSourceKeepsItsFirstObservedPositionDuringLaterUpdates() {
    InteractionHistory h=new InteractionHistory();JSONObject req=question("missing","t1");h.question(req);
    List<ChatItem> canonical=new ArrayList<>();canonical.add(message("before","commandExecution","t1","检查配置"));
    assertEquals(Arrays.asList("before","interaction-call:missing"),ids(h.present(canonical)));
    h.answered(req,Json.obj("answers",Json.obj("q0",Json.obj("answers",new JSONArray().put("A")))));
    canonical.add(message("after","commandExecution","t1","执行后续步骤"));
    ChatItem finalReply=message("final","agentMessage","t1","完成");finalReply.phase="final_answer";canonical.add(finalReply);
    assertEquals(Arrays.asList("before","interaction-call:missing","after","final"),ids(h.present(canonical)));
  }

  @Test public void nativeQuestionStaysBesideItsSourceToolInsteadOfFinalReply() {
    InteractionHistory h=new InteractionHistory();
    JSONObject request=Json.obj("id",8,"method","item/tool/requestUserInput","params",Json.obj("turnId","t1","itemId","tool","questions",Json.object(question("unused","t1"),"params").optJSONArray("questions")));
    h.question(request);
    ChatItem finalReply=message("final","agentMessage","t1","完成");finalReply.phase="final_answer";
    List<ChatItem> canonical=Arrays.asList(message("tool","mcpToolCall","t1","交互工具"),message("after","commandExecution","t1","继续执行"),finalReply);
    assertEquals(Arrays.asList("tool","interaction-native:t1:tool","after","final"),ids(h.present(canonical)));
  }

  @Test public void sourceOrderWinsOverArchiveArrivalOrderAndSurvivesRestore() {
    InteractionHistory h=new InteractionHistory();h.question(question("second","t1"));h.question(question("first","t1"));
    List<ChatItem> canonical=Arrays.asList(message("first","agentMessage","t1","选择方案"),message("work","commandExecution","t1","执行"),message("second","agentMessage","t1","选择方案"));
    for(InteractionHistory history:Arrays.asList(h,restored(h)))
      assertEquals(Arrays.asList("interaction-call:first","work","interaction-call:second"),ids(history.present(canonical)));
  }
}
