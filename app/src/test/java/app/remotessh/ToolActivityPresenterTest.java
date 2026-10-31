package app.remotessh;

import static org.junit.Assert.*;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ToolActivityPresenterTest {
  @Test public void groupsConsecutiveActionsAndKeepsProseAsBoundary() {
    UiText.initialize(RuntimeEnvironment.getApplication());
    ChatItem read=ChatItem.from(Json.obj("id","read","type","commandExecution","command","cat A.java",
        "commandActions",new JSONArray().put(Json.obj("type","read","path","A.java","command","cat A.java","name","A.java"))));
    read.turnId="turn";
    ChatItem edit=ChatItem.from(Json.obj("id","edit","type","fileChange","changes",
        new JSONArray().put(Json.obj("path","A.java","diff","@@ -1 +1 @@\n-old\n+new\n"))));
    edit.turnId="turn";
    ChatItem prose=new ChatItem("reply","agentMessage","Done");prose.turnId="turn";prose.phase="commentary";
    ChatItem command=new ChatItem("next","commandExecution","pwd");command.turnId="turn";
    List<ChatItem> presented=ToolActivityPresenter.present(Arrays.asList(read,edit,prose,command),"");
    assertEquals(3,presented.size());
    assertEquals("activityGroup",presented.get(0).type);
    assertEquals(2,presented.get(0).children.size());
    assertTrue(presented.get(0).text.contains("1"));
    assertSame(prose,presented.get(1));
    assertSame(command,presented.get(2));
  }

  @Test public void finalSummaryUsesLatestAggregatedDiffOnce() {
    UiText.initialize(RuntimeEnvironment.getApplication());
    ChatItem edit=ChatItem.from(Json.obj("id","edit","type","fileChange","changes",
        new JSONArray().put(Json.obj("path","A.java","diff","+old\n"))));edit.turnId="turn";
    ChatItem snapshot=new ChatItem("diff-turn","fileChange","查看本轮更改");snapshot.turnId="turn";
    snapshot.detail="diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1 +1,2 @@\n-old\n+new\n+more\n";
    ChatItem finalText=new ChatItem("answer","agentMessage","Done");finalText.turnId="turn";finalText.phase="final_answer";
    List<ChatItem> presented=ToolActivityPresenter.present(Arrays.asList(edit,snapshot,finalText),"");
    assertEquals(3,presented.size());
    assertEquals("fileSummary",presented.get(2).type);
    assertEquals(1,presented.get(2).children.size());
    assertEquals("+2 −1",presented.get(2).detail);
    assertEquals(2,DiffSummary.count(presented.get(2).children.get(0).detail)[0]);
    assertEquals(2,ToolActivityPresenter.present(Arrays.asList(edit,snapshot,finalText),"turn").size());
  }

  @Test public void activeGroupShowsLiveFileCountsAndSettlesWhenTurnEnds() {
    UiText.initialize(RuntimeEnvironment.getApplication());
    ChatItem command=new ChatItem("cmd","commandExecution","pwd");command.turnId="turn";
    ChatItem edit=ChatItem.from(Json.obj("id","edit","type","fileChange","changes",
        new JSONArray().put(Json.obj("path","A.java","diff","@@ -1 +1,2 @@\n-old\n+new\n+more\n"))));
    edit.turnId="turn";
    List<ChatItem> source=Arrays.asList(command,edit);
    ChatItem active=ToolActivityPresenter.present(source,"turn").get(0);
    assertEquals("activityGroup",active.type);
    assertEquals("inProgress",active.status);
    assertTrue(active.text.contains("+2 −1"));
    ChatItem finished=ToolActivityPresenter.present(source,"").get(0);
    assertEquals("completed",finished.status);
    assertEquals(active.text,finished.text);
  }

  @Test public void reasoningAndExecutionSummariesShareOneRunningDisclosure() {
    UiText.initialize(RuntimeEnvironment.getApplication());
    ChatItem thought=new ChatItem("thought","reasoning","思考过程");
    thought.turnId="turn";thought.status="inProgress";thought.detail="先检查状态";
    ChatItem command=new ChatItem("cmd","commandExecution","git status");command.turnId="turn";
    ChatItem edit=ChatItem.from(Json.obj("id","edit","type","fileChange","changes",
        new JSONArray().put(Json.obj("path","Main.java","diff","@@ -1 +1 @@\n-old\n+new\n"))));
    edit.turnId="turn";
    ChatItem commentary=new ChatItem("text","agentMessage","继续检查");
    commentary.turnId="turn";commentary.phase="commentary";
    List<ChatItem> source=Arrays.asList(thought,command,edit,commentary);
    List<ChatItem> first=ToolActivityPresenter.present(source,"turn");
    assertEquals(2,first.size());assertEquals("activityGroup",first.get(0).type);
    assertEquals(3,first.get(0).children.size());
    assertTrue(first.get(0).text.contains(UiText.get(R.string.ui_reasoning)));
    assertTrue(first.get(0).text.contains("+1 −1"));
    edit.changes=new JSONArray().put(Json.obj("path","Main.java","diff","@@ -1 +1,2 @@\n-old\n+new\n+more\n"));
    edit.detail="Main.java\n@@ -1 +1,2 @@\n-old\n+new\n+more\n";
    ChatItem progressed=ToolActivityPresenter.present(source,"turn").get(0);
    assertTrue(progressed.text.contains("+2 −1"));
    assertNotEquals(first.get(0).detail,progressed.detail);
    assertEquals("completed",ToolActivityPresenter.present(source,"").get(0).status);
  }
}
