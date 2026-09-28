package app.remotessh;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

public class RpcClientTest {
  private PipedOutputStream server;
  private ByteArrayOutputStream sent;
  private RpcClient client;
  private BlockingQueue<JSONObject> events;

  @Before
  public void setUp() throws Exception {
    server = new PipedOutputStream();
    PipedInputStream in = new PipedInputStream(server, 16384);
    sent = new ByteArrayOutputStream();
    events = new LinkedBlockingQueue<>();
    client =
        new RpcClient(
            in,
            sent,
            new RpcClient.Events() {
              public void onEvent(JSONObject e) {
                events.add(e);
              }

              public void onClosed(Exception e) {}
            });
    client.start();
  }

  @After
  public void tearDown() throws Exception {
    client.close();
    server.close();
  }

  private void receive(JSONObject j) throws Exception {
    server.write((j + "\n").getBytes(StandardCharsets.UTF_8));
    server.flush();
  }

  @Test
  public void correlatesOutOfOrderResponses() throws Exception {
    CompletableFuture<JSONObject> a = client.request("thread/list", new JSONObject()),
        b = client.request("model/list", new JSONObject());
    receive(Json.obj("id", 2, "result", Json.obj("kind", "model")));
    receive(Json.obj("id", 1, "result", Json.obj("kind", "thread")));
    assertEquals("thread", a.get(2, TimeUnit.SECONDS).getString("kind"));
    assertEquals("model", b.get(2, TimeUnit.SECONDS).getString("kind"));
  }

  @Test
  public void routesServerRequestWithoutConsumingClientResponse() throws Exception {
    CompletableFuture<JSONObject> f = client.request("turn/start", new JSONObject());
    receive(
        Json.obj(
            "id",
            1,
            "method",
            "item/commandExecution/requestApproval",
            "params",
            Json.obj("command", "ls")));
    JSONObject approval = events.poll(2, TimeUnit.SECONDS);
    assertNotNull(approval);
    assertFalse(f.isDone());
    client.respond(approval.get("id"), Json.obj("decision", "decline"));
    receive(Json.obj("id", 1, "result", Json.obj("turn", Json.obj("id", "turn-1"))));
    assertEquals("turn-1", f.get(2, TimeUnit.SECONDS).getJSONObject("turn").getString("id"));
    assertTrue(sent.toString("UTF-8").contains("decline"));
  }

  @Test
  public void handlesUtf8AcrossPackets() throws Exception {
    byte[] data =
        (Json.obj("method", "item/agentMessage/delta", "params", Json.obj("delta", "你好 🌏")) + "\n")
            .getBytes(StandardCharsets.UTF_8);
    for (byte b : data) {
      server.write(b);
      server.flush();
    }
    JSONObject event = events.poll(2, TimeUnit.SECONDS);
    assertNotNull(event);
    assertEquals("你好 🌏", event.getJSONObject("params").getString("delta"));
  }

  @Test
  public void remoteErrorsFailOnlyTheirRequest() throws Exception {
    CompletableFuture<JSONObject> f = client.request("thread/resume", new JSONObject());
    receive(Json.obj("id", 1, "error", Json.obj("message", "missing thread")));
    try {
      f.get(2, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      assertEquals("missing thread", e.getCause().getMessage());
    }
  }

  @Test
  public void preservesRemoteErrorCodeMethodAndData() throws Exception {
    CompletableFuture<JSONObject> f = client.request("thread/resume", Json.obj("threadId", "busy"));
    receive(
        Json.obj(
            "id",
            1,
            "error",
            Json.obj(
                "code",
                -32600,
                "message",
                "thread busy already has an active writer",
                "data",
                Json.obj("detail", "conflict"))));
    try {
      f.get(2, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException e) {
      RpcException error = (RpcException) e.getCause();
      assertEquals(-32600, error.code);
      assertEquals("thread/resume", error.method);
      assertEquals("conflict", ((JSONObject) error.data).getString("detail"));
    }
  }

  @Test
  public void disconnectUnblocksPendingRequests() throws Exception {
    CompletableFuture<JSONObject> f = client.request("turn/start", new JSONObject());
    client.close();
    assertTrue(f.isCompletedExceptionally());
    assertTrue(client.request("thread/list", new JSONObject()).isCompletedExceptionally());
  }

  @Test
  public void malformedStreamFailsPendingRequests() throws Exception {
    CompletableFuture<JSONObject> f = client.request("initialize", new JSONObject());
    server.write("not json\n".getBytes(StandardCharsets.UTF_8));
    server.flush();
    try {
      f.get(2, TimeUnit.SECONDS);
      fail();
    } catch (ExecutionException expected) {
      assertTrue(f.isCompletedExceptionally());
      assertTrue(
          "Preserve protocol errors instead of replacing them with disconnect",
          expected.getCause() instanceof JSONException);
    }
  }
}
