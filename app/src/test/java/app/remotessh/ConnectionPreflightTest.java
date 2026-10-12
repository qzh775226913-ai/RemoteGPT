package app.remotessh;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Test;

public class ConnectionPreflightTest {
  private String run(String command) throws Exception {
    Process process = new ProcessBuilder("/bin/sh", "-c", command).start();
    assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  @Test public void checksPythonWithoutInvokingCustomLauncherOrLeakingItsEnvironment() throws Exception {
    Profile profile = new Profile();
    profile.command = "PRIVATE_TOKEN=do-not-print custom-wrapper --stdio";
    String command = ConnectionPreflight.command(profile);
    assertFalse(command.contains("PRIVATE_TOKEN"));
    assertFalse(command.contains("do-not-print"));
    String output = run(command);
    assertTrue(output.contains("PYTHON_OK"));
    assertTrue(output.contains("CUSTOM_COMMAND"));
    ConnectionPreflight.check(profile, ignored -> output);
  }

  @Test public void missingPythonExplainsExplicitDowngradeWithoutCodexMisdiagnosis() throws Exception {
    Profile profile = new Profile();
    try {
      ConnectionPreflight.check(profile, ignored -> ConnectionPreflight.MARKER + "\nPYTHON_MISSING\nCODEX_OK\nCHECK_DONE\n");
      fail("Missing Python accepted");
    } catch (IOException error) {
      assertTrue(error.getMessage().contains("Python 3.8"));
      assertTrue(ConnectionPreflight.canDisablePersistence(error.getMessage()));
      assertTrue(profile.persistent);
      assertFalse(error.getMessage().contains("安装 Codex"));
    }
  }

  @Test public void directBinaryWithSpacesIsQuotedAndHelpProbeCannotCallAModel() throws Exception {
    Path folder = Files.createTempDirectory("preflight space ");
    Path binary = folder.resolve("codex");
    try {
      Files.write(binary, "#!/bin/sh\ncase \"$*\" in '--version'|'app-server --help') exit 0;; *) exit 99;; esac\n".getBytes(StandardCharsets.UTF_8));
      assertTrue(binary.toFile().setExecutable(true));
      Profile profile = new Profile(); profile.persistent = false;
      profile.command = SshTransport.quote(binary.toString()) + " app-server";
      assertEquals(binary.toString(), ConnectionPreflight.executable(profile.command));
      assertFalse(ConnectionPreflight.command(profile).contains("python3"));
      ConnectionPreflight.check(profile, this::run);
    } finally { Files.deleteIfExists(binary); Files.deleteIfExists(folder); }
  }

  @Test public void absentCodexIsIdentifiedBeforeProtocolStartup() throws Exception {
    Profile profile = new Profile(); profile.persistent = false;
    profile.command = "/definitely-missing-remote-gpt/codex app-server";
    try { ConnectionPreflight.check(profile, this::run); fail("Missing CLI accepted"); }
    catch (IOException error) {
      assertTrue(error.getMessage().contains("CODEX_MISSING"));
      assertTrue(error.getMessage().contains("完整路径"));
      assertFalse(ConnectionPreflight.canDisablePersistence(error.getMessage()));
    }
  }

  @Test public void brokenRuntimeAndUnsupportedProtocolHaveDifferentRecoveryInstructions() throws Exception {
    for (String code : new String[] {"PYTHON_VERSION", "PYTHON_MODULES", "CODEX_RUNTIME", "APP_SERVER_MISSING"}) {
      try { ConnectionPreflight.check(new Profile(), ignored -> ConnectionPreflight.MARKER + "\n" + code + "\nCHECK_DONE\n"); fail(code); }
      catch (IOException error) { assertTrue(error.getMessage().contains("环境检查 · " + code)); }
    }
  }

  @Test public void malformedOrIncompleteCheckCannotBeReportedAsSuccess() throws Exception {
    try { ConnectionPreflight.check(new Profile(), ignored -> "warning\nPYTHON_OK\n"); fail("Incomplete check accepted"); }
    catch (IOException error) { assertTrue(error.getMessage().contains("CHECK_UNAVAILABLE")); }
    assertEquals("", ConnectionPreflight.executable("codex app-server; echo unsafe"));
    assertEquals("", ConnectionPreflight.executable("TOKEN=private codex app-server"));
  }

  private void account(org.json.JSONObject result, org.json.JSONObject error) throws Exception {
    PipedOutputStream server = new PipedOutputStream();
    ByteArrayOutputStream output = new ByteArrayOutputStream() {
      @Override public void flush() throws IOException {
        try {
          org.json.JSONObject request = new org.json.JSONObject(toString("UTF-8").trim());
          assertEquals("account/read", request.getString("method"));
          assertFalse(request.getJSONObject("params").getBoolean("refreshToken"));
          org.json.JSONObject reply = Json.obj("id", request.opt("id"));
          reply.put(error == null ? "result" : "error", error == null ? result : error);
          server.write((reply + "\n").getBytes(StandardCharsets.UTF_8)); server.flush();
        } catch (org.json.JSONException failure) { throw new IOException(failure); }
      }
    };
    RpcClient rpc = new RpcClient(new PipedInputStream(server), output, new RpcClient.Events() {
      public void onEvent(org.json.JSONObject event) {}
      public void onClosed(Exception error) {}
    });
    rpc.start();
    try { ConnectionPreflight.checkAccount(rpc); }
    finally { rpc.close(); server.close(); }
  }

  @Test public void requiredLoginIsReportedWithoutReadingAuthFiles() throws Exception {
    try { account(Json.obj("requiresOpenaiAuth", true, "account", org.json.JSONObject.NULL), null); fail("Required login accepted"); }
    catch (IOException error) { assertTrue(error.getMessage().contains("CODEX_LOGIN_REQUIRED")); }
  }

  @Test public void customProviderAndOlderAccountApiRemainUsable() throws Exception {
    account(Json.obj("requiresOpenaiAuth", false, "account", org.json.JSONObject.NULL), null);
    account(Json.obj("requiresOpenaiAuth", true, "account", Json.obj("type", "apiKey")), null);
    account(null, Json.obj("code", -32601, "message", "unsupported"));
  }
}
