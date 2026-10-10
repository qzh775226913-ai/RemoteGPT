package app.remotessh;

import static org.junit.Assert.*;
import android.os.Looper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class RecoveryPerformanceTest {
  private void await(java.util.function.BooleanSupplier condition) throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(!condition.getAsBoolean() && System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
    assertTrue(condition.getAsBoolean());
  }
  private Field rpcField() throws Exception {Field f=RemoteApp.class.getDeclaredField("rpc");f.setAccessible(true);return f;}
  private JSONObject outgoing(ByteArrayOutputStream sent,int index) throws Exception {
    await(()->sent.toString().split("\n").length>index && sent.toString().endsWith("\n"));
    return new JSONObject(sent.toString("UTF-8").split("\n")[index]);
  }
  private void reply(PipedOutputStream server,JSONObject request,JSONObject result) throws Exception {
    server.write((Json.obj("id",request.opt("id"),"result",result)+"\n").getBytes(StandardCharsets.UTF_8));server.flush();
  }
  private RemoteApp app() {RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.connected=true;return app;}

  @Test public void largeBaselineIsAppliedBeforeFollowingLiveDelta() throws Exception {
    RemoteApp app=app();PipedOutputStream server=new PipedOutputStream();ByteArrayOutputStream sent=new ByteArrayOutputStream();
    Method event=RemoteApp.class.getDeclaredMethod("event",JSONObject.class);event.setAccessible(true);
    RpcClient rpc=new RpcClient(new PipedInputStream(server,65536),sent,new RpcClient.Events(){
      public void onEvent(JSONObject e){app.main.post(()->{try{event.invoke(app,e);}catch(Exception failure){throw new AssertionError(failure);}});}public void onClosed(Exception e){}
    });Field field=rpcField();field.set(app,rpc);rpc.start();
    try {
      app.resume("thread");JSONObject request=outgoing(sent,0);JSONArray items=new JSONArray();
      for(int i=0;i<2000;i++)items.put(Json.obj("id","m"+i,"type","agentMessage","text","baseline "+i));
      reply(server,request,Json.obj("thread",Json.obj("id","thread","status",Json.obj("type","active"),"turns",new JSONArray().put(Json.obj("id","turn","status","inProgress","items",items)))));
      server.write((Json.obj("method","item/agentMessage/delta","params",Json.obj("threadId","thread","itemId","m0","delta"," live"))+"\n").getBytes(StandardCharsets.UTF_8));server.flush();
      await(()->app.items.size()==2000 && app.items.get(0).text.endsWith(" live"));
      assertEquals("baseline 0 live",app.items.get(0).text);assertEquals("baseline 1999",app.items.get(1999).text);assertFalse(app.readOnly);assertFalse(app.recoveryInProgress());
    }finally{rpc.close();server.close();field.set(app,null);}
  }

  @Test public void idleRecoveryLoadsHistoryWithoutClaimingWriter() throws Exception {
    RemoteApp app=app();PipedOutputStream server=new PipedOutputStream();ByteArrayOutputStream sent=new ByteArrayOutputStream();
    RpcClient rpc=new RpcClient(new PipedInputStream(server),sent,new RpcClient.Events(){public void onEvent(JSONObject e){}public void onClosed(Exception e){}});
    Field field=rpcField();field.set(app,rpc);rpc.start();
    try {
      app.restoreSession(Json.obj("lastThread","idle","multiSession",true,"threads",new JSONArray().put("idle")),"idle");
      JSONObject status=outgoing(sent,0);assertEquals("thread/read",status.optString("method"));assertFalse(Json.object(status,"params").getBoolean("includeTurns"));
      reply(server,status,Json.obj("thread",Json.obj("id","idle","status",Json.obj("type","idle"))));
      JSONObject history=outgoing(sent,1);assertEquals("thread/read",history.optString("method"));assertTrue(Json.object(history,"params").getBoolean("includeTurns"));
      reply(server,history,Json.obj("thread",Json.obj("id","idle","status",Json.obj("type","idle"),"turns",new JSONArray().put(Json.obj("id","turn","items",new JSONArray().put(Json.obj("id","m","type","agentMessage","text","空闲对话历史")))))));
      await(()->!app.items.isEmpty() && !app.recoveryInProgress());assertTrue(app.readOnly);assertEquals("空闲对话历史",app.items.get(0).text);
      assertFalse(sent.toString().replace("\\/","/").contains("thread/resume"));
    }finally{rpc.close();server.close();field.set(app,null);}
  }
}
