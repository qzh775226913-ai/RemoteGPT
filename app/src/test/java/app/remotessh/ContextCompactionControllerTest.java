package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ContextCompactionControllerTest {
  private RemoteApp app;
  private RpcClient rpc;
  private PipedOutputStream server;
  private ByteArrayOutputStream sent;

  @Before public void setup() throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.profile=new Profile();app.connected=true;app.threadId="A";app.readOnly=true;
    server=new PipedOutputStream();sent=new ByteArrayOutputStream();
    rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events() {
      public void onEvent(JSONObject event) {}
      public void onClosed(Exception error) {}
    });
    Field field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);
    rpc.start();
  }

  @After public void close() throws Exception { rpc.close();server.close(); }

  @Test public void readOnlyConversationResumesBeforeCompaction() throws Exception {
    assertTrue(app.contextCompaction.start("A"));
    JSONObject resume=outgoing("thread/resume");
    assertNull(find("thread/compact/start"));
    respond(resume);
    await(() -> !app.readOnly);
    JSONObject compact=outgoing("thread/compact/start");
    assertEquals("A",Json.object(compact,"params").optString("threadId"));
    assertFalse(app.readOnly);
  }

  @Test public void changingSelectionCancelsPendingCompaction() throws Exception {
    assertTrue(app.contextCompaction.start("A"));
    JSONObject resume=outgoing("thread/resume");
    app.threadId="B";app.changed();
    respond(resume);
    Thread.sleep(100);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertNull(find("thread/compact/start"));
  }

  @Test public void runningConversationCannotStartCompaction() throws Exception {
    app.remoteRunning=true;
    assertFalse(app.contextCompaction.canStart());
    assertFalse(app.contextCompaction.start("A"));
    assertNull(find("thread/resume"));
  }

  @Test public void pendingCompactionHasItsOwnStatusAndDoesNotLeakToAnotherThread() throws Exception {
    app.readOnly=false;
    assertTrue(app.contextCompaction.start("A"));
    assertTrue(app.contextCompaction.isCompressing());
    assertFalse(app.contextCompaction.canStart());
    app.threadId="B";app.changed();
    assertFalse(app.contextCompaction.isCompressing());
  }

  private JSONObject snapshot() {
    return Json.obj("approvalPolicy","never","sandbox",Json.obj("type","dangerFullAccess"),
        "thread",Json.obj("id","A","status",Json.obj("type","idle"),"turns",new JSONArray()));
  }

  private void respond(JSONObject request) throws Exception {
    server.write((Json.obj("id",request.opt("id"),"result",snapshot())+"\n").getBytes("UTF-8"));
    server.flush();
  }

  private JSONObject outgoing(String method) throws Exception {
    JSONObject[] found={null};
    await(() -> { found[0]=find(method);return found[0]!=null; });
    return found[0];
  }

  private JSONObject find(String method) {
    try {
      for(String line:sent.toString("UTF-8").split("\n")) {
        if (!line.endsWith("}")) continue;
        JSONObject request=new JSONObject(line);
        if (method.equals(request.optString("method"))) return request;
      }
    } catch(Exception ignored) {}
    return null;
  }

  private void await(BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+3_000_000_000L;
    while(!condition.getAsBoolean() && System.nanoTime()<end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);
    }
    assertTrue(condition.getAsBoolean());
  }
}
