package app.remotessh;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Bidirectional JSON-RPC over SSH stdio, with independent request and notification paths. */
public final class RpcClient implements AutoCloseable {
  public interface Events {
    void onEvent(JSONObject event);

    void onClosed(Exception error);
  }

  private final ConcurrentHashMap<Long, CompletableFuture<JSONObject>> pending =
      new ConcurrentHashMap<>();
  private final AtomicLong ids = new AtomicLong();
  private final ConcurrentHashMap<Long, String> methods = new ConcurrentHashMap<>();
  private final BufferedWriter writer;
  private final InputStream input;
  private final Events events;
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private volatile boolean closed;

  public RpcClient(InputStream input, OutputStream output, Events events) {
    this.input = input;
    this.writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    this.events = events;
  }

  public void start() {
    Thread t = new Thread(this::readLoop, "remote-rpc-reader");
    t.setDaemon(true);
    t.start();
  }

  public CompletableFuture<JSONObject> request(String method, JSONObject params) {
    long id = ids.incrementAndGet();
    CompletableFuture<JSONObject> f = new CompletableFuture<>();
    if (closed) {
      f.completeExceptionally(new IOException("SSH 已断开"));
      return f;
    }
    pending.put(id, f);
    methods.put(id, method);
    try {
      write(Json.obj("id", id, "method", method, "params", params));
      ScheduledFuture<?> timeout =
          timer.schedule(
              () -> {
                CompletableFuture<JSONObject> p = pending.remove(id);
                methods.remove(id);
                if (p != null) p.completeExceptionally(new IOException(method + " 请求超时"));
              },
              60,
              TimeUnit.SECONDS);
      f.whenComplete((r, e) -> timeout.cancel(false));
    } catch (Exception e) {
      pending.remove(id);
      methods.remove(id);
      f.completeExceptionally(e);
    }
    return f;
  }

  public void notify(String method, JSONObject params) throws IOException {
    write(Json.obj("method", method, "params", params));
  }

  public void respond(Object id, JSONObject result) throws IOException {
    write(Json.obj("id", id, "result", result));
  }

  public void unsupported(Object id) throws IOException {
    write(
        Json.obj(
            "id",
            id,
            "error",
            Json.obj("code", -32601, "message", "This client does not support this request")));
  }

  private synchronized void write(JSONObject j) throws IOException {
    if (closed) throw new IOException("SSH 已断开");
    writer.write(j.toString());
    writer.newLine();
    writer.flush();
  }

  private void readLoop() {
    Exception failure = new EOFException("远端程序已退出，请检查启动命令和远端登录状态");
    try {
      InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
      StringBuilder line = new StringBuilder();
      char[] buf = new char[8192];
      int n;
      while (!closed && (n = reader.read(buf)) != -1) {
        for (int i = 0; i < n; i++) {
          char ch = buf[i];
          if (ch == '\n') {
            if (line.length() > 0) receive(new JSONObject(line.toString()));
            line.setLength(0);
          } else {
            line.append(ch);
            if (line.length() > 32 * 1024 * 1024) throw new IOException("远端消息超过 32 MiB 限制");
          }
        }
      }
    } catch (Exception e) {
      failure = e;
    }
    if (!closed) {
      shutdown(failure);
      events.onClosed(failure);
    }
  }

  private void receive(JSONObject j) {
    if (j.has("method")) {
      events.onEvent(j);
      return;
    }
    long id = j.optLong("id", -1);
    CompletableFuture<JSONObject> f = pending.remove(id);
    String method = methods.remove(id);
    if (f == null) return;
    if (j.has("error"))
      f.completeExceptionally(
          new RpcException(method == null ? "unknown" : method, Json.object(j, "error")));
    else f.complete(Json.object(j, "result"));
  }

  @Override
  public void close() {
    shutdown(new IOException("SSH 已断开"));
  }

  private void shutdown(Exception failure) {
    closed = true;
    timer.shutdownNow();
    pending.forEach((id, f) -> f.completeExceptionally(failure));
    pending.clear();
    methods.clear();
    try {
      input.close();
    } catch (IOException ignored) {
    }
  }
}
