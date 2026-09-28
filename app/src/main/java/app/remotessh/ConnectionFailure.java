package app.remotessh;

/** Keeps the actual failure separate from remote stderr, which may contain harmless warnings. */
final class ConnectionFailure {
  static Throwable unwrap(Throwable e) {
    while ((e instanceof java.util.concurrent.CompletionException
            || e instanceof java.util.concurrent.ExecutionException)
        && e.getCause() != null) e = e.getCause();
    return e;
  }

  static String operation(String method) {
    switch (method) {
      case "thread/resume":
        return "打开历史对话";
      case "thread/read":
        return "读取对话历史";
      case "thread/list":
        return "加载对话列表";
      case "thread/start":
        return "创建对话";
      case "turn/steer":
        return "发送引导消息";
      case "turn/start":
        return "发送消息";
      case "turn/interrupt":
        return "停止回复";
      case "model/list":
        return "加载模型";
      case "sftp/list":
        return "浏览远端目录";
      case "sftp/mkdir":
        return "创建项目目录";
      default:
        return method;
    }
  }

  static String operationError(String method, org.json.JSONObject params, Throwable failure) {
    Throwable e = unwrap(failure);
    String code =
        e instanceof RpcException
            ? String.valueOf(((RpcException) e).code)
            : e instanceof com.jcraft.jsch.SftpException
                ? "SFTP_" + ((com.jcraft.jsch.SftpException) e).id
                : e instanceof IllegalArgumentException
                    ? "INVALID_ARGUMENT"
                    : e instanceof java.net.SocketTimeoutException
                        ? "TIMEOUT"
                        : e.getMessage() != null && e.getMessage().contains("超时")
                            ? "RPC_TIMEOUT"
                            : "CLIENT_" + e.getClass().getSimpleName();
    String result =
        operation(method)
            + "失败 · "
            + code
            + "\n\n操作："
            + operation(method)
            + "（"
            + method
            + "）\n错误代码："
            + code
            + "\n原因："
            + reason(e);
    if (params.has("threadId")) result += "\n线程：" + params.optString("threadId");
    if (params.has("path")) result += "\n路径：" + params.optString("path");
    if (e instanceof RpcException) {
      Object data = ((RpcException) e).data;
      if (data != null && data != org.json.JSONObject.NULL) result += "\n服务器详情：" + data;
    }
    if (RemoteApp.isWriterConflict(e))
      result +=
          "\n\n服务器报告此线程仍有写入占用，这不等于正在生成。将尝试只读查看历史。关闭 SSH 终端后，远端 Codex / tmux 进程仍可能保留；退出原 Codex 会话后点击接续，或重连并接续以刷新本 App 连接。若仍失败，请复制诊断排查远端占用。";
    else if (method.equals("thread/resume")) result += "\n\n当前对话已保留。可以刷新列表后重试，或在同一项目新建对话。";
    return result;
  }

  static String reason(Throwable error) {
    while ((error instanceof java.util.concurrent.CompletionException
            || error instanceof java.util.concurrent.ExecutionException)
        && error.getCause() != null) {
      error = error.getCause();
    }
    String name = error.getClass().getSimpleName();
    String message = error.getMessage();
    if (name.equals("NetworkOnMainThreadException"))
      return "连接初始化失败：网络操作运行在主线程（NetworkOnMainThreadException）";
    return message == null || message.trim().isEmpty() ? name : name + ": " + message;
  }

  static boolean upstreamForbidden(String diagnostic) {
    return diagnostic != null && diagnostic.contains("403")
        && (diagnostic.contains("responses_websocket") || diagnostic.contains("backend-api/codex/responses"));
  }

  static String describe(Throwable error, String phase, int exitStatus, String stderr) {
    if (stderr != null) stderr = stderr.replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "");
    String result = "连接失败 · " + phase + "\n\n原因：" + reason(error);
    if (upstreamForbidden(stderr)) result += "\n\n远端 Codex 连接模型服务的 WebSocket 收到 HTTP 403 拒绝。此错误发生在远端到模型服务的链路，不是手机 SSH 密码错误。需在同一远端用户下检查 Codex 登录状态、账户访问权限和代理/网络策略；仅凭日志不能确定是哪一项。UNDICI 实验性警告本身不是失败原因。若远端程序已退出，客户端无法继续接收该进程的输出。";
    if (phase.contains("TCP") || reason(error).contains("ConnectException") || reason(error).contains("UnknownHostException"))
      result += "\n\n尚未进入 SSH 身份认证。请核对目标地址和端口；若同手机其他 SSH 客户端能连接同一地址，检查 Tailscale → 头像 → 按应用分流，确保 Remote（app.remotessh）未被排除，并检查系统是否禁止本应用联网。也可重新连接 Tailscale 后重试。";
    else if (phase.contains("SSH")) result += "\n\n如使用 Tailscale：确认手机 VPN 已连接、主机在线、访问规则允许目标端口。MagicDNS 解析失败可改填 100.x 地址；普通 SSH 用密码/私钥，Tailscale SSH 用免密码方式。若要求重新验证，请打开 Tailscale App 完成验证后重试。";
    if (exitStatus >= 0) result += "\n远端退出码：" + exitStatus;
    if (exitStatus == 127) result += "\nSSH 已连接，但远端 shell 找不到启动程序；这不是 IPv6 连接失败。请在相同远端账号的终端执行 command -v codex，将输出的绝对路径填入启动命令（例如 /opt/homebrew/bin/codex app-server）。如果没有输出，需要先在该账号安装 Codex。";
    if (stderr != null && !stderr.trim().isEmpty())
      result += "\n\n远端诊断输出（警告不一定是失败原因）：\n" + stderr.trim();
    return result;
  }
}
