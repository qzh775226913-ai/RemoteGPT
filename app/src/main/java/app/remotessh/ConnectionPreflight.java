package app.remotessh;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.json.JSONObject;

/** Bounded, credential-free checks before starting the remote protocol. Never runs a model. */
final class ConnectionPreflight {
  interface Probe { String run(String command) throws Exception; }
  static final String MARKER = "REMOTE_GPT_CHECK_V1";
  private static final Pattern DIRECT = Pattern.compile("^\\s*(?:'([^']+)'|\"([^\"$`]+)\"|([^\\s'\"$`;|&<>]+))\\s+app-server(?:\\s+[^;&|<>]*)?\\s*$");

  static IOException failure(String code, String explanation) {
    return new IOException("环境检查 · " + code + "\n" + explanation);
  }

  static String executable(String command) {
    Matcher match = DIRECT.matcher(command);
    if (!match.matches()) return ""; // Preserve custom protocol-compatible launchers.
    for (int n = 1; n <= 3; n++) if (match.group(n) != null) return match.group(n);
    return "";
  }

  static String command(Profile profile) {
    StringBuilder script = new StringBuilder("printf '" + MARKER + "\\n'; ");
    if (profile.persistent) {
      script.append("if ! command -v python3 >/dev/null 2>&1; then printf 'PYTHON_MISSING\\n'; ")
          .append("elif ! python3 -c ")
          .append(SshTransport.quote("import sys; sys.exit(0 if sys.version_info >= (3,8) else 1)"))
          .append(" >/dev/null 2>&1; then printf 'PYTHON_VERSION\\n'; ")
          .append("elif ! python3 -c ")
          .append(SshTransport.quote("import sqlite3, fcntl, socket; assert hasattr(socket, 'AF_UNIX')"))
          .append(" >/dev/null 2>&1; then printf 'PYTHON_MODULES\\n'; else printf 'PYTHON_OK\\n'; fi; ");
    }
    String executable = executable(profile.command);
    if (!executable.isEmpty()) {
      String program = SshTransport.quote(executable);
      script.append("if ! command -v ").append(program).append(" >/dev/null 2>&1; then printf 'CODEX_MISSING\\n'; ")
          .append("elif ! ").append(program).append(" --version >/dev/null 2>&1; then printf 'CODEX_RUNTIME\\n'; ")
          .append("elif ! ").append(program).append(" app-server --help >/dev/null 2>&1; then printf 'APP_SERVER_MISSING\\n'; ")
          .append("else printf 'CODEX_OK\\n'; fi; ");
    } else script.append("printf 'CUSTOM_COMMAND\\n'; ");
    script.append("printf 'CHECK_DONE\\n'");
    return "sh -c " + SshTransport.quote(script.toString());
  }

  static void check(Profile profile, Probe probe) throws Exception {
    String output = probe.run(command(profile));
    if (!output.contains(MARKER + "\n") || !output.contains("CHECK_DONE"))
      throw failure("CHECK_UNAVAILABLE", "SSH 已认证，但无法完成环境检查。请确认账号允许非交互命令执行，且可运行 sh；检查超时不会启动 Codex。");
    for (String line : output.split("\\r?\\n")) {
      switch (line) {
        case "PYTHON_MISSING": throw failure(line, "SSH 已认证，但非交互环境找不到 python3。请用同一 SSH 账号安装 Python 3.8 或以上并加入 PATH，或在连接信息中选择关闭持久会话后重连。关闭后断线续跑不保证，版本维护和部分问答恢复也不可用。");
        case "PYTHON_VERSION": throw failure(line, "远端 python3 无法运行或版本低于 3.8。请更新 Python 并检查非交互 SSH 的 PATH；也可以显式关闭持久会话后重连。");
        case "PYTHON_MODULES": throw failure(line, "Python 缺少 sqlite3、fcntl 或 Unix socket 支持，无法托管持久会话。请安装包含这些标准库的 Python，或显式关闭持久会话后重连。");
        case "CODEX_MISSING": throw failure(line, "SSH 已认证，但找不到 Codex 启动程序。请用同一账号安装 Codex，执行 command -v codex，将完整路径加上 app-server 填入启动命令。交互终端的 PATH 不一定适用于本 App。");
        case "CODEX_RUNTIME": throw failure(line, "已找到 Codex，但程序无法运行。请在同一账号检查 codex --version、执行权限和安装方式所需的运行环境。");
        case "APP_SERVER_MISSING": throw failure(line, "Codex 无法提供 app-server 子命令。请检查 CLI 版本或安装；本 App 需要 stdio JSON-RPC，不能使用交互式 codex 命令替代。");
        default: break;
      }
    }
  }

  static void checkAccount(RpcClient rpc) throws Exception {
    JSONObject account;
    try { account = rpc.request("account/read", Json.obj("refreshToken", false)).get(12, TimeUnit.SECONDS); }
    catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = ConnectionFailure.unwrap(e);
      if (cause instanceof RpcException && ((RpcException) cause).code == -32601) return;
      throw failure("ACCOUNT_CHECK_FAILED", "App Server 已启动，但账户检查失败。请检查远端登录和服务版本；环境检查未调用模型。");
    }
    if (account.optBoolean("requiresOpenaiAuth") && account.isNull("account"))
      throw failure("CODEX_LOGIN_REQUIRED", "App Server 已启动，但服务器报告需要 Codex 登录。请用同一 SSH 账号执行 codex login（无浏览器可用 codex login --device-auth），完成后重连。本 App 不会读取或显示登录凭据。");
  }

  static boolean canDisablePersistence(String diagnostic) {
    return diagnostic.contains("环境检查 · PYTHON_MISSING") || diagnostic.contains("环境检查 · PYTHON_VERSION")
        || diagnostic.contains("环境检查 · PYTHON_MODULES");
  }
}
