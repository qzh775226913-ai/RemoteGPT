package app.remotessh;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

/** Requires the explicitly enabled local fixture. Never reads real SSH credentials. */
public class SshIntegrationTest {
  private final Map<String, String> pins = new HashMap<>();
  private final HostPins store =
      new HostPins() {
        public String getPin(String target) {
          return pins.get(target);
        }

        public void rememberPin(String target, String fingerprint) {
          pins.put(target, fingerprint);
        }
      };
  private SshTransport transport;
  private RpcClient rpc;
  private Profile profile;

  @Before
  public void setup() {
    Assume.assumeTrue(Boolean.getBoolean("remote.ssh.fixture"));
    profile = new Profile();
    profile.persistent = false;
    profile.host = "127.0.0.1";
    profile.port = 22228;
    profile.user = "fixture";
    profile.password = "test-only";
    profile.command = "fixture-server";
    transport = new SshTransport();
  }

  @After
  public void close() {
    if (rpc != null) rpc.close();
    if (transport != null) transport.close();
  }

  private RpcClient connect(SshTransport.Trust trust) throws Exception {
    return transport.connect(
        profile,
        store,
        trust,
        new RpcClient.Events() {
          public void onEvent(JSONObject e) {}

          public void onClosed(Exception e) {}
        });
  }

  @Test
  public void persistentHostInstallsOverSftpAndSurvivesSshDisconnect() throws Exception {
    profile.persistent = true;
    profile.command = "python3 \"$HOME/fake_server.py\"";
    byte[] script = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/main/assets/remote_session.py"));
    transport.setPersistentScript(script);
    rpc = connect((host, fingerprint) -> true); rpc.start();
    rpc.request("initialize", Json.obj()).get(10, TimeUnit.SECONDS);
    rpc.notify("initialized", Json.obj());
    rpc.request("thread/start", Json.obj()).get(10, TimeUnit.SECONDS);
    rpc.request("turn/start", Json.obj()).get(10, TimeUnit.SECONDS);
    rpc.close(); transport.close();
    transport = new SshTransport(); transport.setPersistentScript(script);
    java.util.concurrent.BlockingQueue<JSONObject> events = new java.util.concurrent.LinkedBlockingQueue<>();
    rpc = transport.connect(profile, store, (host, fingerprint) -> true, new RpcClient.Events() {
      public void onEvent(JSONObject event) { events.add(event); }
      public void onClosed(Exception error) {}
    });
    rpc.start();
    rpc.request("initialize", Json.obj()).get(10, TimeUnit.SECONDS);
    rpc.notify("initialized", Json.obj());
    assertEquals("thread-1", rpc.request("remote/session", Json.obj()).get(10, TimeUnit.SECONDS).getString("lastThread"));
    rpc.request("thread/resume", Json.obj("threadId", "thread-1")).get(10, TimeUnit.SECONDS);
    rpc.notify("remote/replay", Json.obj("threadId", "thread-1"));
    int deltas = 0; boolean replayed = false, approval = false;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!(replayed && approval) && System.nanoTime() < deadline) {
      JSONObject event = events.poll(5, TimeUnit.SECONDS);
      assertNotNull(event);
      if (event.optString("method").equals("remote/replayStarted")) deltas = 0;
      if (event.optString("method").equals("remote/replayCompleted")) replayed = true;
      if (event.optString("method").equals("item/agentMessage/delta")) deltas++;
      if (event.optString("id").equals("approval-1")) approval = true;
    }
    assertTrue(replayed && approval);
    assertEquals(3000, deltas);
    rpc.respond("approval-1", Json.obj("decision", "decline"));
  }

  @Test
  public void authenticatesPinsAndStreamsOverActualSsh() throws Exception {
    rpc =
        connect(
            (host, fp) -> {
              assertTrue(fp.startsWith("SHA256:"));
              return true;
            });
    rpc.start();
    JSONObject response = rpc.request("test", Json.obj("text", "你好 🌏")).get(5, TimeUnit.SECONDS);
    assertEquals("real-ssh", response.getString("transport"));
    assertEquals("你好 🌏", response.getJSONObject("echo").getString("text"));
    assertEquals(1, pins.size());
    assertTrue(transport.diagnostic().contains("UNDICI-EHPA"));
    assertTrue(transport.diagnostic().contains("诊断：测试警告"));
  }

  @Test
  public void refusesUntrustedHost() throws Exception {
    try {
      connect((h, f) -> false);
      fail("Untrusted host connected");
    } catch (com.jcraft.jsch.JSchException expected) {
      assertTrue(pins.isEmpty());
    }
  }

  @Test
  public void refusesChangedHostKeyWithoutReprompting() throws Exception {
    pins.put("127.0.0.1:22228", "SHA256:wrong-key");
    try {
      connect(
          (h, f) -> {
            fail("Changed keys must not prompt as new hosts");
            return true;
          });
      fail("Changed key connected");
    } catch (com.jcraft.jsch.JSchException expected) {
      assertEquals("SHA256:wrong-key", pins.get("127.0.0.1:22228"));
    }
  }

  @Test
  public void refusesBadPassword() throws Exception {
    profile.password = "wrong";
    try {
      connect((h, f) -> true);
      fail("Bad password connected");
    } catch (com.jcraft.jsch.JSchException expected) {
      assertTrue(expected.getMessage().toLowerCase(Locale.ROOT).contains("auth"));
    }
  }

  @Test public void missingPythonFailsBeforeHostingAndDoesNotSilentlyDowngrade() throws Exception {
    profile.user = "fixture-no-python"; profile.persistent = true;
    try { connect((host, fingerprint) -> true); fail("Missing Python accepted"); }
    catch (java.io.IOException error) {
      assertTrue(error.getMessage().contains("PYTHON_MISSING"));
      assertTrue(profile.persistent);
    }
  }

  @Test public void directModeStillConnectsWithoutPython() throws Exception {
    profile.user = "fixture-no-python";
    rpc = connect((host, fingerprint) -> true); rpc.start();
    assertEquals("real-ssh", rpc.request("test", Json.obj()).get(5, TimeUnit.SECONDS).getString("transport"));
  }

  @Test public void sftpFailureIsDistinguishedFromSshAuthentication() throws Exception {
    profile.user = "fixture-no-sftp";
    try { connect((host, fingerprint) -> true); fail("Missing SFTP accepted"); }
    catch (java.io.IOException error) { assertTrue(error.getMessage().contains("SFTP_UNAVAILABLE")); }
  }

  @Test public void missingCodexIsDiagnosedOnActualExecChannel() throws Exception {
    profile.command = "/nonexistent-fixture/codex app-server";
    try { connect((host, fingerprint) -> true); fail("Missing Codex accepted"); }
    catch (java.io.IOException error) { assertTrue(error.getMessage().contains("CODEX_MISSING")); }
  }

  @Test
  public void authenticatesWithEncryptedPrivateKey() throws Exception {
    profile.password = "";
    profile.privateKey =
        new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("/tmp/remote-ssh-fixture-key.pem")),
            java.nio.charset.StandardCharsets.UTF_8);
    profile.passphrase = "fixture-passphrase";
    rpc = connect((h, f) -> true);
    rpc.start();
    assertEquals(
        "real-ssh",
        rpc.request("test", new JSONObject()).get(5, TimeUnit.SECONDS).getString("transport"));
  }

  @Test
  public void browsesAndCreatesUnicodeProjectOverSameSsh() throws Exception {
    rpc = connect((h, f) -> true);
    rpc.start();
    assertTrue(transport.listDirectories("/").directories.contains("projects"));
    String name = "中文 project " + UUID.randomUUID();
    assertEquals("/projects/" + name, transport.createDirectory("/projects", name).path);
    assertTrue(transport.listDirectories("/projects").directories.contains(name));
    try {
      transport.createDirectory("/projects", name);
      fail("Must not overwrite existing directory");
    } catch (com.jcraft.jsch.SftpException expected) {
      assertTrue(expected.id > 0);
    }
    assertEquals(
        "real-ssh",
        rpc.request("test", new JSONObject()).get(5, TimeUnit.SECONDS).getString("transport"));
  }

  @Test
  public void reusesTrustedPinWithoutPrompt() throws Exception {
    rpc = connect((h, f) -> true);
    rpc.close();
    transport.close();
    transport = new SshTransport();
    rpc =
        connect(
            (h, f) -> {
              fail("Already pinned host prompted again");
              return false;
            });
    rpc.start();
    assertEquals(
        "real-ssh",
        rpc.request("test", new JSONObject()).get(5, TimeUnit.SECONDS).getString("transport"));
  }
  @Test
  public void authenticatesWithoutCredentialsForTailnetIdentity() throws Exception {
    profile.user = "tailnet-fixture";
    profile.password = "";
    profile.tailscaleSsh = true;
    rpc = connect((h, f) -> true);
    rpc.start();
    assertEquals("real-ssh", rpc.request("test", new JSONObject()).get(5, TimeUnit.SECONDS).getString("transport"));
  }

  @Test
  public void transfersBinaryImagesAndEnforcesDownloadLimit() throws Exception {
    rpc = connect((h, f) -> true);
    rpc.start();
    byte[] bytes = new byte[] {0, 1, -1, 10, 13, 42};
    java.io.File file = java.io.File.createTempFile("fixture-image", ".jpg");
    try {
      java.nio.file.Files.write(file.toPath(), bytes);
      String path = transport.uploadImage(file);
      java.io.ByteArrayOutputStream downloaded = new java.io.ByteArrayOutputStream();
      transport.download(path, downloaded, 100);
      assertArrayEquals(bytes, downloaded.toByteArray());
      try { transport.download(path, new java.io.ByteArrayOutputStream(), 2); fail("Oversize accepted"); }
      catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("大小")); }
      assertEquals("real-ssh", rpc.request("test", new JSONObject()).get(5, TimeUnit.SECONDS).getString("transport"));
    } finally { file.delete(); }
  }

  @Test(timeout = 30000)
  public void retriesRejectedFileChannelWithoutInterruptingConversation() throws Exception {
    profile.user = "fixture-channel-once";
    rpc = connect((h, f) -> true); rpc.start();
    java.io.File file = java.io.File.createTempFile("channel-retry", ".png");
    try {
      byte[] bytes = new byte[] {0, 13, -1, 42};
      java.nio.file.Files.write(file.toPath(), bytes);
      String path = transport.uploadImage(file);
      java.io.ByteArrayOutputStream downloaded = new java.io.ByteArrayOutputStream();
      transport.download(path, downloaded, 100);
      assertArrayEquals(bytes, downloaded.toByteArray());
      assertEquals("real-ssh", rpc.request("test", Json.obj()).get(5, TimeUnit.SECONDS).getString("transport"));
    } finally { file.delete(); }
  }

  @Test(timeout = 30000)
  public void persistentFileChannelRejectionHasBoundedRetryAndActionableError() throws Exception {
    profile.user = "fixture-channel-denied";
    rpc = connect((h, f) -> true); rpc.start();
    java.io.File file = java.io.File.createTempFile("channel-denied", ".png");
    try {
      try { transport.uploadImage(file); fail("Rejected channel accepted"); }
      catch (java.io.IOException error) {
        assertTrue(error.getMessage().contains("SFTP 文件通道打开失败"));
        assertTrue(error.getMessage().contains("已重试一次"));
        assertTrue(error.getCause() instanceof com.jcraft.jsch.JSchException);
      }
      assertEquals("real-ssh", rpc.request("test", Json.obj()).get(5, TimeUnit.SECONDS).getString("transport"));
    } finally { file.delete(); }
  }

  @Test
  public void readsAcceptedQuestionOnAuxiliaryChannelWithoutInterruptingRpc() throws Exception {
    rpc = connect((h, f) -> true); rpc.start();
    java.io.File rollout = java.io.File.createTempFile("fixture-rollout", ".jsonl");
    try {
      String arguments = Json.obj("questions", new JSONArray().put(Json.obj("title", "选择中文方案", "options", new JSONArray().put("A").put("B")))).toString();
      String lines = Json.obj("type", "event_msg", "payload", Json.obj("type", "task_started", "turn_id", "test")) + "\n"
          + Json.obj("type", "response_item", "payload", Json.obj("type", "function_call", "name", "request_user_input_async", "call_id", "test-call", "arguments", arguments)) + "\n"
          + Json.obj("type", "response_item", "payload", Json.obj("type", "function_call_output", "call_id", "test-call", "output", "{\"accepted\":true}")) + "\n";
      java.nio.file.Files.write(rollout.toPath(), lines.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String path = transport.uploadImage(rollout);
      String script = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/main/assets/conversation_state.py")), java.nio.charset.StandardCharsets.UTF_8);
      JSONObject result = transport.jsonCommand(script, Json.obj("path", path, "offset", 0), 20);
      ConversationJournal journal = new ConversationJournal(); journal.apply(result, "thread");
      assertEquals(1, journal.pending.size());
      assertEquals("选择中文方案", Json.object(journal.pending.get("test-call"), "params").getJSONArray("questions").getJSONObject(0).getString("question"));
      assertEquals(rollout.length(), result.getLong("offset"));
      assertEquals(0, transport.jsonCommand(script, Json.obj("path", path, "offset", result.getLong("offset")), 20).getJSONArray("records").length());
      assertEquals("real-ssh", rpc.request("test", Json.obj()).get(5, TimeUnit.SECONDS).getString("transport"));
    } finally { rollout.delete(); }
  }
}
