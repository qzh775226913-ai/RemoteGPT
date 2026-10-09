package app.remotessh;

import com.jcraft.jsch.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class SshTransport implements AutoCloseable {
  public interface Trust {
    boolean confirm(String host, String fingerprint) throws Exception;
  }

  private final java.util.function.Consumer<String> progress;
  private volatile java.net.Socket connectingSocket;

  public SshTransport() { this(phase -> {}); }
  public SshTransport(java.util.function.Consumer<String> progress) { this.progress = progress; }
  private void phase(String value) { phase = value; progress.accept(value); }

  private byte[] persistentScript;
  private final List<String> persistentIdentities = new ArrayList<>();

  public void setPersistentScript(byte[] script) { persistentScript = script; }
  public void setPersistentIdentities(Collection<String> identities) {
    persistentIdentities.clear();
    if (identities != null) persistentIdentities.addAll(identities);
  }

  static String quote(String text) { return "'" + text.replace("'", "'\"'\"'") + "'"; }

  private String persistentCommand(Profile p) throws Exception {
    if (persistentScript == null) throw new IOException("缺少远端会话托管程序");
    phase("准备远端持久会话（需要 Python 3）");
    ChannelSftp files = sftp();
    try {
      String folder = files.getHome() + "/.remote-gpt";
      try { files.stat(folder); } catch (SftpException e) {
        if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
        files.mkdir(folder);
      }
      files.chmod(0700, folder);
      String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(
          MessageDigest.getInstance("SHA-256").digest(persistentScript));
      String script = folder + "/host-" + hash + ".py";
      try { files.stat(script); } catch (SftpException e) {
        if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
        String temp = script + "." + UUID.randomUUID();
        files.put(new ByteArrayInputStream(persistentScript), temp);
        files.chmod(0600, temp);
        files.rename(temp, script);
      }
      org.json.JSONArray identities = new org.json.JSONArray();
      for (String id : persistentIdentities) if (id != null && !id.isEmpty()) identities.put(id);
      if (identities.length() == 0) identities.put(p.id);
      return "python3 " + quote(script) + " " + quote(identities.toString()) + " " + quote(p.command);
    } finally { files.disconnect(); }
  }

  private volatile Session session;
  private volatile ChannelExec channel;
  private volatile boolean closed;
  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private volatile String phase = "SSH 连接与认证";

  public RpcClient connect(Profile p, HostPins vault, Trust trust, RpcClient.Events events)
      throws Exception {
    p.validate();
    JSch jsch = new JSch();
    String target = p.host + ":" + p.port;
    jsch.setHostKeyRepository(
        new HostKeyRepository() {
          public int check(String host, byte[] key) {
            try {
              String fingerprint =
                  "SHA256:"
                      + Base64.getEncoder()
                          .withoutPadding()
                          .encodeToString(MessageDigest.getInstance("SHA-256").digest(key));
              synchronized (vault) {
                String old = vault.getPin(target);
                if (old != null) return old.equals(fingerprint) ? OK : CHANGED;
              }
              if (!trust.confirm(target, fingerprint)) return NOT_INCLUDED;
              synchronized (vault) {
                vault.rememberPin(target, fingerprint);
              }
              return OK;
            } catch (Exception e) {
              return NOT_INCLUDED;
            }
          }

          public void add(HostKey k, UserInfo u) {}

          public void remove(String h, String t) {}

          public void remove(String h, String t, byte[] k) {}

          public String getKnownHostsRepositoryID() {
            return "Android encrypted host pins";
          }

          public HostKey[] getHostKey() {
            return new HostKey[0];
          }

          public HostKey[] getHostKey(String h, String t) {
            return new HostKey[0];
          }
        });
    if (!p.tailscaleSsh && !p.privateKey.isEmpty())
      jsch.addIdentity(
          "android-key",
          p.privateKey.getBytes(StandardCharsets.UTF_8),
          null,
          p.passphrase.getBytes(StandardCharsets.UTF_8));
    Session s = jsch.getSession(p.user, p.host, p.port);
    session = s;
    s.setSocketFactory(new SocketFactory() {
      public java.net.Socket createSocket(String host, int port) throws IOException {
        phase("连接主机（TCP）");
        java.net.Socket socket = new java.net.Socket();
        connectingSocket = socket;
        try {
          if (closed) throw new IOException("连接已取消");
          socket.connect(new java.net.InetSocketAddress(host, port), 20000);
          if (closed) throw new IOException("连接已取消");
          phase("SSH 握手与认证");
          return socket;
        } catch (IOException e) { socket.close(); throw e; }
      }
      public InputStream getInputStream(java.net.Socket socket) throws IOException { return socket.getInputStream(); }
      public OutputStream getOutputStream(java.net.Socket socket) throws IOException { return socket.getOutputStream(); }
    });
    s.setConfig("StrictHostKeyChecking", "yes");
    s.setConfig("PreferredAuthentications", "publickey,password");
    if (!p.tailscaleSsh) s.setPassword(p.password);
    s.setServerAliveInterval(15000);
    s.setServerAliveCountMax(3);
    if (closed) throw new IOException("连接已取消");
    s.connect(p.tailscaleSsh ? 90000 : 20000);
    if (closed) {
      s.disconnect();
      throw new IOException("连接已取消");
    }
    ChannelExec c = (ChannelExec) s.openChannel("exec");
    phase("启动远端程序");
    channel = c;
    c.setPty(false);
    c.setCommand(p.persistent ? persistentCommand(p) : p.command);
    c.setErrStream(
        new OutputStream() {
          public void write(int b) {
            synchronized (diagnostics) {
              if (diagnostics.size() < 16384) diagnostics.write(b);
            }
          }

          public void write(byte[] bytes, int offset, int length) {
            synchronized (diagnostics) {
              int count = Math.min(length, 16384 - diagnostics.size());
              if (count > 0) diagnostics.write(bytes, offset, count);
            }
          }
        });
    InputStream in = c.getInputStream();
    OutputStream out = c.getOutputStream();
    c.connect(20000);
    if (closed) {
      close();
      throw new IOException("连接已取消");
    }
    phase("远端协议握手 / 通信");
    return new RpcClient(in, out, events);
  }

  public String phase() {
    return phase;
  }

  public int exitStatus() {
    ChannelExec c = channel;
    return c == null ? -1 : c.getExitStatus();
  }

  public String diagnostic() {
    synchronized (diagnostics) {
      return new String(diagnostics.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  /** A separate SSH channel keeps version checks/install output out of JSON-RPC. */
  public org.json.JSONObject maintenance(String script, org.json.JSONObject request) throws Exception {
    return jsonCommand(script,request,request.optString("action").equals("update") ? 650 : 90);
  }

  org.json.JSONObject jsonCommand(String script, org.json.JSONObject request, int timeout) throws Exception {
    Session current = session;
    if (closed || current == null || !current.isConnected()) throw new IOException("SSH 未连接");
    ChannelExec exec = (ChannelExec) current.openChannel("exec");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    OutputStream bounded = new OutputStream() {
      public synchronized void write(int value) throws IOException {
        if (output.size() >= 65536) throw new IOException("远端响应超过大小限制");
        output.write(value);
      }
      public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        if (output.size() + length > 65536) throw new IOException("远端响应超过大小限制");
        output.write(bytes, offset, length);
      }
    };
    try {
      exec.setPty(false);
      exec.setCommand("python3 -c " + quote(script) + " " + quote(request.toString()));
      exec.setInputStream(null);
      exec.setOutputStream(bounded);
      exec.setErrStream(new OutputStream() { public void write(int value) {} });
      exec.connect(15000);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(
          timeout);
      while (!exec.isClosed()) {
        if (closed || !current.isConnected()) throw new IOException("SSH 已断开，请重连后检查版本和更新结果");
        if (System.nanoTime() > deadline) throw new IOException("远端版本操作超时，请重新检查更新结果");
        Thread.sleep(50);
      }
      if (exec.getExitStatus() != 0) throw new IOException("远端版本检测不可用，请检查 Python 3 和执行权限");
      return new org.json.JSONObject(output.toString(StandardCharsets.UTF_8.name()).trim());
    } finally { exec.disconnect(); }
  }

  private ChannelSftp sftp() throws Exception {
    Session current = session;
    if (closed || current == null || !current.isConnected()) throw new IOException("SSH 未连接");
    ChannelSftp sftp = (ChannelSftp) current.openChannel("sftp");
    try {
      sftp.connect(15000);
      return sftp;
    } catch (Exception e) {
      sftp.disconnect();
      throw e;
    }
  }

  public RemoteDirectory listDirectories(String path) throws Exception {
    ChannelSftp channel = sftp();
    try {
      String canonical = channel.realpath(path.isEmpty() ? "." : path);
      if (!channel.stat(canonical).isDir()) throw new IOException("所选路径不是文件夹");
      List<String> names = new ArrayList<>();
      boolean[] truncated = {false};
      channel.ls(
          canonical,
          entry -> {
            String name = entry.getFilename();
            if (!name.equals(".")
                && !name.equals("..")
                && (entry.getAttrs().isDir() || entry.getAttrs().isLink())) {
              if (names.size() >= 1000) {
                truncated[0] = true;
                return ChannelSftp.LsEntrySelector.BREAK;
              }
              names.add(name);
            }
            return ChannelSftp.LsEntrySelector.CONTINUE;
          });
      Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
      return new RemoteDirectory(canonical, names, truncated[0]);
    } finally {
      channel.disconnect();
    }
  }

  public RemoteDirectory createDirectory(String parent, String name) throws Exception {
    ChannelSftp channel = sftp();
    try {
      String canonical = channel.realpath(parent);
      if (!channel.stat(canonical).isDir()) throw new IOException("父路径不是文件夹");
      String target = RemoteDirectory.child(canonical, name.trim());
      channel.mkdir(target);
      return new RemoteDirectory(channel.realpath(target), new ArrayList<>(), false);
    } finally {
      channel.disconnect();
    }
  }

  public String uploadImage(File file) throws Exception {
    ChannelSftp c = sftp();
    try {
      String folder = c.getHome() + "/.remote-ssh-attachments";
      try { c.stat(folder); } catch (SftpException e) {
        if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
        c.mkdir(folder);
      }
      c.chmod(0700, folder);
      String target = folder + "/" + UUID.randomUUID() + file.getName().substring(file.getName().lastIndexOf('.'));
      try (InputStream in = new FileInputStream(file)) { c.put(in, target); }
      c.chmod(0600, target);
      return target;
    } finally { c.disconnect(); }
  }

  public void download(String path, OutputStream out, long limit) throws Exception {
    ChannelSftp c = sftp();
    try {
      if (c.stat(path).getSize() > limit) throw new IOException("文件超过下载大小限制");
      try (InputStream in = c.get(path)) { MediaFiles.copy(in, out, limit); }
    } finally { c.disconnect(); }
  }

  @Override
  public void close() {
    closed = true;
    java.net.Socket socket = connectingSocket;
    if (socket != null) try { socket.close(); } catch (IOException ignored) {}
    ChannelExec c = channel;
    if (c != null) c.disconnect();
    Session s = session;
    if (s != null) s.disconnect();
  }
}
