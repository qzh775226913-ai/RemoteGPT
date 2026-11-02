package app.remotessh;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FileChangeLiveTest {
  private RemoteApp app;
  private Method event;

  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.connected=true;app.threadId="A";
    app.turnId="turn-A";app.busy=true;event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
  }
  private JSONArray changes(String path,String diff){return new JSONArray().put(Json.obj("path",path,"kind","update","diff",diff));}
  private void patch(String thread,String turn,String id,JSONArray value) throws Exception {
    event.invoke(app,Json.obj("method","item/fileChange/patchUpdated","params",Json.obj("threadId",thread,"turnId",turn,"itemId",id,"changes",value)));
  }

  @Test public void patchNotificationsUpdateExactItemAndRejectLateOrForeignTurns() throws Exception {
    ChatItem item=ChatItem.from(Json.obj("id","edit","type","fileChange","status","inProgress","changes",changes("A.java","-old\n+new\n")));
    item.turnId="turn-A";app.items.add(item);
    patch("A","turn-A","edit",changes("A.java","-old\n+new\n+more\n"));
    assertEquals(2,DiffSummary.fromItem(item).added());assertTrue(item.detail.contains("+more"));
    patch("A","wrong-turn","edit",changes("A.java","+incorrect\n"));assertEquals(2,DiffSummary.fromItem(item).added());
    item.status="completed";patch("A","turn-A","edit",changes("A.java","+stale\n"));assertEquals(2,DiffSummary.fromItem(item).added());
    app.inConversation("B",()->{app.turnId="turn-B";app.busy=true;});
    patch("B","turn-B","edit-B",changes("B.java","+one\n+two\n+three\n"));
    assertEquals("A",app.threadId);assertSame(item,app.items.get(0));assertEquals(2,DiffSummary.fromItem(item).added());
    app.inConversation("B",()->{assertEquals(1,app.items.size());assertEquals(3,DiffSummary.fromItem(app.items.get(0)).added());});
  }

  @Test public void patchBeforeStartSurvivesAnEmptyStartAndReplay() throws Exception {
    patch("A","turn-A","edit",changes("A.java","+one\n"));
    event.invoke(app,Json.obj("method","item/started","params",Json.obj("threadId","A","turnId","turn-A",
        "item",Json.obj("id","edit","type","fileChange","status","inProgress","changes",new JSONArray()))));
    assertEquals(1,app.items.size());assertEquals(1,DiffSummary.fromItem(app.items.get(0)).added());
    app.replaying=true;patch("A","turn-A","edit",changes("A.java","+one\n+two\n"));
    assertEquals(2,DiffSummary.fromItem(app.items.get(0)).added());
    patch("A","turn-A","edit",new JSONArray());assertTrue(app.items.get(0).detail.isEmpty());
  }
}
