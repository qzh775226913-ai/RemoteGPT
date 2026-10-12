package app.remotessh;

import static org.junit.Assert.*;

import java.io.*;
import java.util.concurrent.CompletionException;
import org.junit.Test;

public class ConnectionFailureTest {
  @Test public void websocketForbiddenIsNotMisdiagnosedAsSshAuthentication() {
    String diagnostic = "\u001b[31mERROR responses_websocket: HTTP error: 403 Forbidden, url: wss://chatgpt.com/backend-api/codex/responses";
    assertTrue(ConnectionFailure.upstreamForbidden(diagnostic));
    assertFalse(ConnectionFailure.upstreamForbidden("[UNDICI-EHPA] Warning"));
    String message = ConnectionFailure.describe(new EOFException("exited"), "远端协议握手 / 通信", -1, diagnostic);
    assertTrue(message.contains("远端到模型服务的链路"));
    assertFalse(message.contains("\u001b"));
  }

  @Test
  public void exceptionWithoutMessageKeepsItsType() {
    assertEquals("IOException", ConnectionFailure.reason(new IOException()));
  }

  @Test
  public void diagnosticWarningDoesNotReplaceActualFailure() {
    String result =
        ConnectionFailure.describe(
            new EOFException("process exited"), "远端协议握手", 127, "[UNDICI-EHPA] Warning");
    assertTrue(result.contains("EOFException: process exited"));
    assertTrue(result.contains("退出码：127"));
    assertTrue(result.indexOf("原因：") < result.indexOf("[UNDICI-EHPA]"));
    assertTrue(result.contains("绝对路径"));
  }

  @Test
  public void unwrapsFutureWithoutDiscardingMeaningfulException() {
    assertEquals(
        "IOException: connection reset",
        ConnectionFailure.reason(new CompletionException(new IOException("connection reset"))));
  }
  @Test public void tcpFailureExplainsRoutingBeforeAuthentication() {
    String message = ConnectionFailure.describe(new com.jcraft.jsch.JSchException("java.net.ConnectException: ECONNABORTED"), "连接主机（TCP）", -1, "");
    assertTrue(message.contains("尚未进入 SSH 身份认证"));
    assertTrue(message.contains("按应用分流"));
    assertFalse(message.contains("用免密码方式"));
  }
  @Test public void pythonExitDoesNotSuggestInstallingCodex() {
    String message = ConnectionFailure.describe(new EOFException("exited"), "远端协议握手", 127, "sh: python3: command not found");
    assertTrue(message.contains("Python 3.8"));
    assertFalse(message.contains("command -v codex"));
  }
}
