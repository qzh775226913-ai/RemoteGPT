package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AttachmentStateTest {
  RemoteApp app;RpcClient rpc;PipedOutputStream server;ByteArrayOutputStream sent;Field field;
  @Before public void setup()throws Exception {
    app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.connected=true;app.threadId="t";
    server=new PipedOutputStream();sent=new ByteArrayOutputStream();
    rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){public void onEvent(JSONObject e){}public void onClosed(Exception e){}});
    field=RemoteApp.class.getDeclaredField("rpc");field.setAccessible(true);field.set(app,rpc);rpc.start();
  }
  @After public void close()throws Exception {rpc.close();server.close();field.set(app,null);}
  void await(java.util.function.BooleanSupplier ready)throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(!ready.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
    assertTrue(ready.getAsBoolean());
  }
  JSONObject outgoing(int index)throws Exception {
    await(()->sent.toString().endsWith("\n") && sent.toString().split("\n").length>index);
    return new JSONObject(sent.toString("UTF-8").split("\n")[index]);
  }
  void reply(JSONObject request,boolean failure)throws Exception {
    JSONObject response=Json.obj("id",request.get("id"));
    response.put(failure?"error":"result",failure?Json.obj("code",-32600,"message","turn ended"):Json.obj("turn",Json.obj("id","new-turn")));
    server.write((response+"\n").getBytes(StandardCharsets.UTF_8));server.flush();
  }
  void attachments(){app.draftImages.add("/remote/picture.png");app.draftFiles.add(new FileAttachment("报告.pdf","/remote/report.pdf",123));}
  @Test public void attachmentOnlySteerRetainsDraftOnFailureAndClearsOnlyAcceptedAttachments()throws Exception {
    app.busy=true;app.turnId="running";attachments();assertTrue(app.steer(""));JSONObject first=outgoing(0);
    assertEquals("turn/steer",first.getString("method"));JSONArray content=Json.object(first,"params").getJSONArray("input");
    assertEquals(2,content.length());assertTrue(content.getJSONObject(0).getString("text").contains("/remote/report.pdf"));
    assertEquals("localImage",content.getJSONObject(1).getString("type"));assertEquals("running",Json.object(first,"params").getString("expectedTurnId"));
    reply(first,true);await(()->!app.steering);assertEquals(1,app.draftImages.size());assertEquals(1,app.draftFiles.size());assertTrue(app.busy);assertTrue(app.items.isEmpty());
    assertTrue(app.steer(""));JSONObject second=outgoing(1);reply(second,false);await(()->!app.steering);
    assertTrue(app.draftFiles.isEmpty());assertTrue(app.draftImages.isEmpty());assertEquals(1,app.items.size());assertEquals(Collections.singletonList("/remote/picture.png"),app.items.get(0).images);
  }
  @Test public void ordinaryFileOnlyStartsTurnAndKeepsReferenceInHistory()throws Exception {
    app.draftFiles.add(new FileAttachment("data.csv","/remote/data.csv",8));app.send("");JSONObject request=outgoing(0);
    assertEquals("turn/start",request.getString("method"));JSONArray content=Json.object(request,"params").getJSONArray("input");
    assertEquals(1,content.length());assertEquals("text",content.getJSONObject(0).getString("type"));
    assertTrue(app.items.get(0).text.contains("data.csv"));reply(request,false);await(()->app.draftFiles.isEmpty());
  }
  @Test public void queuedAttachmentsAreScopedAndCancelRestoresThem() {
    app.busy=true;app.turnId="running";attachments();assertTrue(app.queueMessage("later",true));
    assertTrue(app.draftImages.isEmpty());assertTrue(app.draftFiles.isEmpty());assertEquals(3,app.remainingImages());assertEquals(7,app.remainingFiles());
    app.inConversation("other",()->{assertEquals("",app.queuedMessage());assertEquals(4,app.remainingImages());assertTrue(app.draftFiles.isEmpty());});
    app.cancelQueuedMessage();assertEquals(1,app.draftImages.size());assertEquals(1,app.draftFiles.size());
  }
  @Test public void queuedAttachmentsSendWithoutConsumingNewDrafts()throws Exception {
    app.busy=true;app.turnId="running";attachments();assertTrue(app.queueMessage("later",true));
    app.draftImages.add("/remote/new.png");app.draftFiles.add(new FileAttachment("new.txt","/remote/new.txt",1));
    app.busy=false;app.sendQueuedMessage();JSONObject request=outgoing(0);
    JSONArray content=Json.object(request,"params").getJSONArray("input");assertEquals(2,content.length());assertFalse(content.toString().contains("new.txt"));assertFalse(content.toString().contains("new.png"));
    reply(request,false);await(()->app.draftFiles.size()==1);assertEquals("new.txt",app.draftFiles.get(0).name);assertEquals(Collections.singletonList("/remote/new.png"),app.draftImages);
  }
  @Test public void submittingOrRecoveringTurnDoesNotAcceptAttachments()throws Exception {
    app.busy=true;assertFalse(app.canAttach());app.turnId="run";assertTrue(app.canAttach());
    app.attaching=true;assertFalse(app.canAttach());assertFalse(app.canSteer());app.attaching=false;
    app.replaying=true;assertFalse(app.canAttach());app.replaying=false;
    Field loading=RemoteApp.class.getDeclaredField("loadingThread");loading.setAccessible(true);loading.set(app,"t");assertFalse(app.canAttach());assertFalse(app.steer("wait"));
  }
  @Test public void fileNamesCannotIntroducePathsOrExtraMessageLines() {
    assertEquals("file",FileAttachment.safeName(".."));
    assertEquals(".._report.pdf_fake",FileAttachment.safeName("../report.pdf\nfake"));
  }
}
