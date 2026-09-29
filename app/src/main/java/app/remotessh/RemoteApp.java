package app.remotessh;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** All visible state belongs to the application; rotation never restarts an SSH command. */
public final class RemoteApp extends Application {
  public final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newCachedThreadPool();
  public Vault vault;
  public Profile profile;
  public String state = "未连接", error = "", threadId = "", turnId = "", model = "", unsentText = "";
  public boolean connecting, connected, busy, steering;
  public String reasoningEffort = "", draftText = "";
  public PermissionMode permissionMode = PermissionMode.INHERIT;
  public boolean readOnly, remoteRunning;
  public final UsageLimits usage = new UsageLimits();
  public String threadCwd = "";
  private String reconnectThread = "", resumeDraftThread = "";
  public String threadTitle = "", historyProject = "", lastError = "";
  private String observedError = "";
  private final Set<String> loadedThreads = new HashSet<>();
  private final Set<String> recoveringThreads = new HashSet<>();
  private final Set<String> statusPolling = new HashSet<>();
  private long statusRevision;
  private final Runnable hideError =
      () -> {
        error = "";
        observedError = "";
        if (this.observer != null) this.observer.run();
      };
  public final List<String> draftImages = new ArrayList<>();
  public final Map<String, android.graphics.Bitmap> draftImagePreviews = new HashMap<>();
  public android.graphics.Bitmap pendingImagePreview;
  public boolean attaching;
  public final List<ChatItem> items = new ArrayList<>();
  public final List<JSONObject> approvals = new ArrayList<>();
  public JSONArray threads = new JSONArray(), models = new JSONArray();
  public String nextCursor = "";
  public Runnable observer;
  public final Set<Runnable> stateListeners = new HashSet<>();
  public boolean replaying;
  public String trustHost = "", trustFingerprint = "";
  private CompletableFuture<Boolean> trustAnswer;
  private SshTransport transport;
  private RpcClient rpc;
  private int generation;
  public LiveStatusNotifications liveStatus;
  private final Map<JSONObject, String> approvalTokens = new IdentityHashMap<>();
  private final Set<JSONObject> answering = Collections.newSetFromMap(new IdentityHashMap<>());
  boolean stopping;
  private boolean foreground, retryEligible, readPolling;
  private int retryAttempts;
  private final Runnable reconnectTask = () -> {
    if (!foreground || !retryEligible || connected || connecting || profile == null || retryAttempts >= 3) return;
    int attempt = retryAttempts + 1;
    reconnectAndResume();
    retryAttempts = attempt;
  };

  private final Map<String, ConversationState> conversations = new LinkedHashMap<>();
  private int scopeDepth;
  private boolean scopeChanged;
  private final ArrayDeque<String> restoreQueue = new ArrayDeque<>();
  private String restoringThread = "", pendingNavigation = "", loadingThread = "", replayThread = "";
  private boolean multiSessionHost = true;

  void checkpoint() {
    conversations.computeIfAbsent(threadId, key -> new ConversationState()).capture(this);
  }

  List<String> conversationIds() {
    checkpoint();
    List<String> ids = new ArrayList<>(conversations.keySet()); ids.remove(""); return ids;
  }

  // All state and event dispatch run on the main looper. Never expose a temporary
  // background context to UI listeners; publish once the selected context is restored.
  void inConversation(String id, Runnable action) {
    if (id.equals(threadId)) { action.run(); return; }
    checkpoint();
    ConversationState selected = conversations.get(threadId);
    ConversationState target = conversations.computeIfAbsent(id, key -> {
      ConversationState c = new ConversationState(); c.threadId=key; return c;
    });
    String selectedProject = profile == null ? "" : profile.cwd;
    String selectedLast = profile == null ? "" : profile.lastThread;
    scopeDepth++;
    target.apply(this);
    try { action.run(); }
    finally {
      checkpoint(); selected.apply(this); scopeDepth--;
      if (profile != null) { profile.cwd=selectedProject; profile.lastThread=selectedLast; }
      if (scopeDepth == 0 && scopeChanged) { scopeChanged=false; changed(); }
    }
  }

  boolean canSwitchConversation() {
    return connected && !attaching && !(busy && threadId.isEmpty()) && restoringThread.isEmpty() && loadingThread.isEmpty() && restoreQueue.isEmpty();
  }

  private void select(String id) {
    if (!id.equals(threadId)) leaveIdleConversation();
    checkpoint();
    ConversationState target = conversations.computeIfAbsent(id, key -> {
      ConversationState c = new ConversationState(); c.threadId=key; c.model=model; c.effort=reasoningEffort; c.permission=permissionMode; return c;
    });
    target.apply(this);
    if (profile != null) { profile.lastThread=id; save(); }
  }

  List<JSONObject> allApprovals() {
    checkpoint(); List<JSONObject> result = new ArrayList<>();
    for (ConversationState c : conversations.values()) result.addAll(c.approvals);
    return result;
  }

  android.content.Intent conversationIntent(Class<?> activity, String id) {
    return new android.content.Intent(this, activity).setAction(android.content.Intent.ACTION_VIEW)
        .setData(new android.net.Uri.Builder().scheme("remotegpt").authority("conversation")
            .appendPath(profile == null ? "" : profile.id).appendPath(id).build())
        .putExtra("profileId", profile == null ? "" : profile.id).putExtra("threadId", id);
  }

  boolean ownsIntent(android.content.Intent intent) {
    return intent != null && profile != null && profile.id.equals(intent.getStringExtra("profileId"));
  }

  void openConversation(String id) {
    if (!canSwitchConversation()) { pendingNavigation=id; return; }
    viewConversation(id);
  }

  private void refreshOtherLegacyThreads(String restored) {
    if (multiSessionHost) return;
    for (String id : new ArrayList<>(loadedThreads)) {
      if (id.equals(restored)) continue;
      inConversation(id, () -> request("thread/read", Json.obj("threadId", id, "includeTurns", true), r -> {
        applyThread(r, id, readOnly);
      }));
    }
  }

  void restoreSession(JSONObject session, String resumeId) {
    multiSessionHost=session.optBoolean("multiSession");
    String remoteId = session.optString("lastThread");
    String restoreId = resumeId.isEmpty() ? remoteId : resumeId;
    JSONArray ids = session.optJSONArray("threads");
    java.util.LinkedHashSet<String> restore = new java.util.LinkedHashSet<>();
    if (!restoreId.isEmpty()) restore.add(restoreId);
    if (ids != null) for (int i=0;i<ids.length();i++) restore.add(ids.optString(i));
    restore.addAll(getSharedPreferences("sessions", MODE_PRIVATE).getStringSet(profile.id, Collections.emptySet()));
    restore.addAll(recoveringThreads);
    restore.remove("");
    recoveringThreads.addAll(restore);
    if (!restoreId.isEmpty()) select(restoreId);
    restoreQueue.addAll(restore); restoreNext();
  }

  private void restoreNext() {
    if (!restoringThread.isEmpty()) return;
    if (restoreQueue.isEmpty()) {
      if (!pendingNavigation.isEmpty()) { String id=pendingNavigation; pendingNavigation=""; viewConversation(id); }
      return;
    }
    String id = restoreQueue.removeFirst(); restoringThread=id; loadingThread=id;
    inConversation(id, () -> {
      busy=true;
      request("thread/read", Json.obj("threadId", id, "includeTurns", true), snapshot -> {
        if (Json.object(Json.object(snapshot, "thread"), "status").optString("type").equals("active")) {
          request("thread/resume", Json.obj("threadId", id), r -> {
            applyThread(r, id, false);
            if (!r.optBoolean("remoteReplay")) { restoringThread=""; main.post(this::restoreNext); }
          });
        } else {
          applyThread(snapshot, id, true);
          restoringThread="";
          request("thread/unsubscribe", Json.obj("threadId", id), ignored -> {});
          main.post(this::restoreNext);
        }
      });
    });
  }

  public void setForeground(boolean visible) {
    foreground = visible;
    if (!visible) leaveIdleConversation();
    main.removeCallbacks(reconnectTask);
    if (visible && retryEligible && !connected && !connecting) main.post(reconnectTask);
  }

  boolean currentThreadRunning() {
    return connected && !threadId.isEmpty() && !replaying && replayThread.isEmpty()
        && (remoteRunning || (busy && !turnId.isEmpty()) || !approvals.isEmpty());
  }

  public boolean threadRunning(JSONObject thread) {
    if (!connected || !replayThread.isEmpty()) return false;
    String id = thread.optString("id");
    if (id.equals(threadId)) return currentThreadRunning();
    ConversationState cached = conversations.get(id);
    if (cached != null) return !cached.replaying && (cached.remoteRunning
        || (cached.busy && !cached.turnId.isEmpty()) || !cached.approvals.isEmpty());
    return Json.object(thread, "status").optString("type").equals("active");
  }

  void leaveIdleConversation() {
    if (busy || replaying || currentThreadRunning() || threadId.isEmpty()) return;
    releaseThread(threadId);
    readOnly = true;
    checkpoint();
  }

  // Browsing history must not resume a writer or update its remote timestamp.
  public void viewConversation(String id) {
    if (!canSwitchConversation() || id.isEmpty()) return;
    checkpoint();
    boolean loaded = loadedThreads.contains(id);
    select(id);
    if (loaded) { changed(); return; }
    busy = true; loadingThread = id; changed();
    readThread(id, true);
  }

  JSONObject visibleApproval() {
    if (!replayThread.isEmpty()) return null;
    if (!replaying && !approvals.isEmpty()) return approvals.get(0);
    checkpoint();
    for (ConversationState c : conversations.values())
      if (!c.replaying && !c.approvals.isEmpty()) return c.approvals.get(0);
    return null;
  }

  // Passive threads have no subscription: recheck them even with the app in background.
  void pollPassiveStatus() {
    if (!connected || rpc == null) return;
    final RpcClient client = rpc;
    final int g = generation;
    final long revision = statusRevision;
    for (String id : conversationIds()) {
      ConversationState c = conversations.get(id);
      if (loadedThreads.contains(id) || !c.remoteRunning || !statusPolling.add(id)) continue;
      worker.execute(() -> client.request("thread/read", Json.obj("threadId", id, "includeTurns", false))
          .whenComplete((r, e) -> main.post(() -> {
            if (g != generation) return;
            statusPolling.remove(id);
            if (e != null || revision != statusRevision || loadedThreads.contains(id)) return;
            inConversation(id, () -> {
              remoteRunning = Json.object(Json.object(r, "thread"), "status").optString("type").equals("active");
              if (!remoteRunning) { busy=false; turnId=""; approvals.clear(); }
              changed();
            });
          })));
    }
  }

  public void pollReadOnly() {
    if (!connected || !readOnly || busy || readPolling || rpc == null || threadId.isEmpty()) return;
    final String id = threadId;
    final int g = generation;
    final RpcClient c = rpc;
    final long revision = statusRevision;
    readPolling = true;
    worker.execute(() -> c.request("thread/read", Json.obj("threadId", id, "includeTurns", true))
        .whenComplete((r, e) -> main.post(() -> {
          if (g != generation) return;
          readPolling = false;
          if (!id.equals(threadId) || !readOnly || busy || revision != statusRevision) return;
          if (e == null) applyThread(r, id, true);
          else {
            state = "历史自动刷新失败，可手动重试";
            changed();
          }
        })));
  }


  @Override
  public void onCreate() {
    super.onCreate();
    java.security.Security.removeProvider("BC");
    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    android.content.pm.ShortcutManager shortcuts = getSystemService(android.content.pm.ShortcutManager.class);
    if (shortcuts != null) {
      List<String> obsolete = new ArrayList<>();
      if (android.os.Build.VERSION.SDK_INT >= 30)
        for (android.content.pm.ShortcutInfo shortcut : shortcuts.getShortcuts(
            android.content.pm.ShortcutManager.FLAG_MATCH_DYNAMIC | android.content.pm.ShortcutManager.FLAG_MATCH_CACHED))
          obsolete.add(shortcut.getId());
      shortcuts.removeAllDynamicShortcuts();
      if (android.os.Build.VERSION.SDK_INT >= 30) shortcuts.removeLongLivedShortcuts(obsolete);
    }
    liveStatus = new LiveStatusNotifications(this);
    vault = new Vault(this);
    try {
      vault.read();
    } catch (Exception e) {
      error = "无法读取已保存的连接：" + e.getMessage();
    }
  }

  public void changed() {
    if (scopeDepth > 0) { scopeChanged=true; return; }
    checkpoint();
    if (profile != null && connected) {
      Set<String> remembered = new HashSet<>(recoveringThreads);
      remembered.addAll(loadedThreads);
      getSharedPreferences("sessions", MODE_PRIVATE).edit().putStringSet(profile.id, remembered).apply();
    }
    if (liveStatus != null) liveStatus.observe();
    List<JSONObject> pendingApprovals = allApprovals();
    approvalTokens.keySet().removeIf(req -> !pendingApprovals.contains(req));
    if (!error.isEmpty() && !error.equals(observedError)) {
      observedError = error;
      lastError = error;
      main.removeCallbacks(hideError);
      int timeout = 6500;
      if (android.os.Build.VERSION.SDK_INT >= 29) {
        android.view.accessibility.AccessibilityManager am =
            getSystemService(android.view.accessibility.AccessibilityManager.class);
        if (am != null)
          timeout =
              am.getRecommendedTimeoutMillis(
                  timeout,
                  android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT
                      | android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS);
      }
      main.postDelayed(hideError, timeout);
    } else if (error.isEmpty()) observedError = "";
    if (observer != null) observer.run();
    for (Runnable listener : new ArrayList<>(stateListeners)) listener.run();
  }

  public void reportFailure(String method, JSONObject params, Throwable failure) {
    error = ConnectionFailure.operationError(method, params, failure);
    observedError = "";
    changed();
  }

  public void save() {
    try {
      vault.save();
    } catch (Exception e) {
      error = "保存失败：" + e.getMessage();
      changed();
    }
  }

  public boolean storeProfile(Profile profile) {
    synchronized (vault) {
      int index = -1;
      for (int i = 0; i < vault.profiles.size(); i++)
        if (vault.profiles.get(i).id.equals(profile.id)) {
          index = i;
          break;
        }
      Profile old = index < 0 ? null : vault.profiles.get(index);
      if (index < 0) vault.profiles.add(profile);
      else vault.profiles.set(index, profile);
      try {
        vault.save();
        changed();
        return true;
      } catch (Exception e) {
        if (index < 0) vault.profiles.remove(profile);
        else vault.profiles.set(index, old);
        error = "保存失败：" + message(e);
        changed();
        return false;
      }
    }
  }

  public void connect(Profile p) {
    boolean sameHost = profile != null && profile.id.equals(p.id);
    disconnect();
    conversations.clear();
    if (!sameHost) permissionMode = PermissionMode.INHERIT;
    profile = p;
    reconnectThread = p.lastThread;
    model = "";
    reasoningEffort = "";
    draftImages.clear();
    draftImagePreviews.clear();
    pendingImagePreview = null;
    attaching = false;
    threadId = "";
    threadTitle = "";
    threadCwd = "";
    readOnly = false;
    historyProject = "";
    loadedThreads.clear();
    items.clear();
    threads = new JSONArray();
    models = new JSONArray();
    nextCursor = "";
    connecting = true;
    state = "正在连接…";
    error = "";
    startForegroundService(new Intent(this, ConnectionService.class));
    changed();
    int g = generation;
    SshTransport t = new SshTransport(phase -> main.post(() -> {
      if (g == generation && connecting) { state = phase + "…"; changed(); }
    }));
    transport = t;
    worker.execute(
        () -> {
          try {
            if (p.persistent) {
              try (java.io.InputStream script = getAssets().open("remote_session.py")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = script.read(buffer)) != -1) bytes.write(buffer, 0, n);
                t.setPersistentScript(bytes.toByteArray());
              }
            }
            RpcClient c =
                t.connect(
                    p,
                    vault,
                    (host, fingerprint) -> {
                      CompletableFuture<Boolean> answer = new CompletableFuture<>();
                      main.post(
                          () -> {
                            if (g != generation) {
                              answer.complete(false);
                              return;
                            }
                            trustAnswer = answer;
                            trustHost = host;
                            trustFingerprint = fingerprint;
                            changed();
                          });
                      try {
                        return answer.get(90, TimeUnit.SECONDS);
                      } finally {
                        main.post(
                            () -> {
                              if (trustAnswer == answer) {
                                trustAnswer = null;
                                trustHost = "";
                                changed();
                              }
                            });
                      }
                    },
                    new RpcClient.Events() {
                      public void onEvent(JSONObject event) {
                        main.post(
                            () -> {
                              if (g == generation) event(event);
                            });
                      }

                      public void onClosed(Exception e) {
                        main.post(
                            () -> {
                              if (g == generation) failConnection(e, t);
                            });
                      }
                    });
            main.post(
                () -> {
                  if (g != generation) {
                    worker.execute(
                        () -> {
                          c.close();
                          t.close();
                        });
                    return;
                  }
                  rpc = c;
                  c.start();
                  request(
                      "initialize",
                      Json.obj(
                          "clientInfo",
                          Json.obj(
                              "name", "remote_ssh_android", "title", "Remote", "version", "1.0.22"),
                          "capabilities", Json.obj("experimentalApi", true)),
                      r -> completeInitialization(c, t, g));
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (g == generation) failConnection(e, t);
                });
          }
        });
  }

  // JSON-RPC notifications write to the SSH socket, just like requests do.
  // Never perform this handshake on the Android main thread.
  void completeInitialization(RpcClient c, SshTransport t, int g) {
    worker.execute(
        () -> {
          try {
            c.notify("initialized", new JSONObject());
            main.post(
                () -> {
                  if (g != generation) return;
                  connecting = false;
                  connected = true;
                  if (profile != null && profile.persistent)
                    recoveringThreads.addAll(getSharedPreferences("sessions", MODE_PRIVATE)
                        .getStringSet(profile.id, Collections.emptySet()));
                  state = "已连接";
                  changed();
                  refreshThreads(false);
                  refreshUsage();
                  String resumeId = reconnectThread;
                  reconnectThread = "";
                  if (profile != null && profile.persistent) {
                    request("remote/session", new JSONObject(), session -> restoreSession(session, resumeId));
                  } else if (!resumeId.isEmpty()) viewConversation(resumeId);
                  request(
                      "model/list",
                      Json.obj("limit", 100),
                      m -> {
                        models = m.optJSONArray("data");
                        if (models == null) models = new JSONArray();
                        changed();
                      });
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (g == generation) failConnection(e, t);
                });
          }
        });
  }

  private void failConnection(Throwable e, SshTransport t) {
    String detail = t == null ? "" : t.diagnostic();
    String failure =
        ConnectionFailure.describe(
            e, t == null ? "初始化远端程序" : t.phase(), t == null ? -1 : t.exitStatus(), detail);
    boolean retry = (connected || retryAttempts > 0) && !ConnectionFailure.upstreamForbidden(detail);
    int attempts = retryAttempts;
    disconnect();
    retryAttempts = attempts;
    retryEligible = retry;
    if (retry && foreground && retryAttempts < 3)
      main.postDelayed(reconnectTask, 2000L * (retryAttempts + 1));
    error = failure;
    changed();
  }

  public void trust(boolean accept) {
    if (trustAnswer != null) trustAnswer.complete(accept);
    trustHost = "";
    changed();
  }

  public void disconnect() {
    recoveringThreads.clear();
    statusPolling.clear();
    statusRevision++;
    pendingImagePreview = null;
    generation++;
    approvalTokens.clear();
    answering.clear();
    main.removeCallbacks(reconnectTask);
    retryEligible = false;
    retryAttempts = 0;
    readPolling = false;
    replaying = false;
    readyMessages.clear();
    restoreQueue.clear(); restoringThread=""; pendingNavigation=""; loadingThread=""; replayThread="";
    conversations.clear();
    remoteRunning = false;
    reconnectThread = "";
    resumeDraftThread = "";
    loadedThreads.clear();
    usage.clear();
    attaching = false;
    if (trustAnswer != null) trustAnswer.complete(false);
    trustAnswer = null;
    trustHost = "";
    RpcClient old = rpc;
    rpc = null;
    SshTransport t = transport;
    transport = null;
    if (old != null || t != null)
      worker.execute(
          () -> {
            if (old != null) old.close();
            if (t != null) t.close();
          });
    connected = false;
    connecting = false;
    steering = false;
    busy = false;
    stopping = false;
    turnId = "";
    state = "未连接";
    approvals.clear();
    stopService(new Intent(this, ConnectionService.class));
    changed();
  }

  public void clearError() {
    error = "";
    main.removeCallbacks(hideError);
    changed();
  }

  private static String message(Throwable e) {
    return ConnectionFailure.reason(e);
  }

  private void request(
      String method, JSONObject params, java.util.function.Consumer<JSONObject> success) {
    RpcClient c = rpc;
    if (c == null) {
      if (method.equals("thread/resume")
          || method.equals("thread/read")
          || method.equals("thread/start")
          || method.startsWith("turn/")) busy = false;
      error = "请先连接 SSH 主机";
      changed();
      return;
    }
    int g = generation;
    String requestThread = params.optString("threadId", threadId);
    boolean threadRequest = method.startsWith("thread/") && !method.equals("thread/list")
        || method.startsWith("turn/");
    worker.execute(
        () ->
            c.request(method, params)
                .whenComplete(
                    (r, e) ->
                        main.post(
                            () -> {
                              if (g != generation) return;
                              Runnable completion = () -> {
                              if (e != null) {
                                if (method.equals("account/rateLimits/read")) {
                                  usage.loading = false;
                                  usage.status = "用量暂不可用：" + message(e);
                                  changed();
                                  return;
                                }
                                if (method.equals("turn/steer")) {
                                  steering = false;
                                  reportFailure(method, params, e);
                                  drainQueuedMessage();
                                  return; // A rejected steer does not stop the active turn.
                                }
                                reportFailure(method, params, e);
                                if (method.equals("initialize")) {
                                  failConnection(e, transport);
                                  return;
                                } else if (method.equals("thread/resume") && isWriterConflict(e)) {
                                  readThread(params.optString("threadId"), true);
                                  return;
                                } else if (method.startsWith("turn/")
                                    || method.equals("thread/start")
                                    || method.equals("thread/resume")
                                    || method.equals("thread/read")) {
                                  busy = false;
                                  stopping = false;
                                }
                                changed();
                              } else success.accept(r);
                              };
                              if (threadRequest) inConversation(requestThread, completion); else completion.run();
                              if (e != null && requestThread.equals(loadingThread) && !isWriterConflict(e)) {
                                loadingThread=""; main.post(this::restoreNext);
                              }
                              if (e != null && requestThread.equals(restoringThread) && !isWriterConflict(e)) {
                                restoringThread=""; main.post(this::restoreNext);
                              }
                            })));
  }

  public void refreshThreads(boolean more) {
    JSONObject p = Json.obj("limit", 50, "sortKey", "updated_at");
    try {
      if (!historyProject.isEmpty()) p.put("cwd", historyProject);
    } catch (JSONException ignored) {
    }
    String requestedProject = historyProject;
    long requestedRevision = statusRevision;
    if (more && !nextCursor.isEmpty())
      try {
        p.put("cursor", nextCursor);
      } catch (JSONException ignored) {
      }
    request(
        "thread/list",
        p,
        r -> {
          if (!requestedProject.equals(historyProject)) return;
          JSONArray data = r.optJSONArray("data");
          if (!more) threads = new JSONArray();
          if (data != null) for (int i = 0; i < data.length(); i++) {
            JSONObject listed = data.optJSONObject(i);
            threads.put(data.opt(i));
            if (listed == null) continue;
            String id = listed.optString("id");
            // Subscribed events own loaded threads; fresh snapshots own passive history.
            if (requestedRevision == statusRevision && !id.isEmpty() && !loadedThreads.contains(id) && !id.equals(loadingThread))
              inConversation(id, () -> {
                remoteRunning = Json.object(listed, "status").optString("type").equals("active");
                if (!remoteRunning) { busy=false; turnId=""; approvals.clear(); }
                threadTitle = restoredTitle(listed, id, threadTitle);
              });
          }
          if (profile != null) {
            boolean added = false;
            for (int i = 0; i < threads.length(); i++) {
              JSONObject thread = threads.optJSONObject(i);
              if (thread != null) {
                String cwd = thread.optString("cwd");
                if (cwd.startsWith("/") && profile.projects.size() < 200)
                  added |= profile.projects.add(cwd);
              }
            }
            if (added) save();
          }
          nextCursor = r.isNull("nextCursor") ? "" : r.optString("nextCursor");
          changed();
        });
  }

  public void refreshUsage() {
    if (!connected || usage.loading) return;
    usage.loading = true;
    changed();
    request("account/rateLimits/read", new JSONObject(), r -> {
      usage.loading = false;
      usage.update(r, true);
      changed();
    });
  }

  public String currentProject() {
    return !threadId.isEmpty() ? threadCwd : profile == null ? "" : profile.cwd;
  }

  public void reconnectAndResume() {
    if (profile == null || connecting || attaching || busy) return;
    String id = threadId.isEmpty() ? profile.lastThread : threadId;
    String draft = unsentText;
    List<String> images = new ArrayList<>(draftImages);
    Map<String, android.graphics.Bitmap> previews = new HashMap<>(draftImagePreviews);
    connect(profile);
    reconnectThread = id;
    resumeDraftThread = id;
    unsentText = draft;
    draftImages.addAll(images);
    draftImagePreviews.putAll(previews);
  }

  private void releaseThread(String id) {
    if (id.isEmpty() || !loadedThreads.remove(id) || !connected) return;
    request("thread/unsubscribe", Json.obj("threadId", id), r -> {});
  }

  public void newChat() {
    if (!canSwitchConversation()) return;
    select("");
    busy=false; stopping=false; steering=false; replaying=false;
    approvals.clear();
    draftImages.clear();
    draftImagePreviews.clear();
    attaching = false;
    threadId = "";
    threadTitle = "";
    threadCwd = "";
    readOnly = false;
    remoteRunning = false;
    turnId = "";
    items.clear();
    error = "";
    unsentText = ""; draftText="";
    changed();
  }

  public void resume(String id) {
    if (!canSwitchConversation() || id.isEmpty()) return;
    checkpoint();
    boolean cached = conversations.containsKey(id) && loadedThreads.contains(id);
    select(id);
    if (cached) { changed(); return; }
    error=""; busy=true; loadingThread=id; changed();
    request("thread/resume", Json.obj("threadId", id), r -> applyThread(r, id, false));
  }

  static boolean isWriterConflict(Throwable error) {
    Throwable cause = ConnectionFailure.unwrap(error);
    return cause instanceof RpcException
        && ((RpcException) cause).code == -32600
        && cause.getMessage() != null
        && cause.getMessage().toLowerCase(Locale.ROOT).contains("already has an active writer");
  }

  private void readThread(String id, boolean readOnly) {
    request(
        "thread/read",
        Json.obj("threadId", id, "includeTurns", true),
        r -> {
          if (!readOnly && Json.object(Json.object(r, "thread"), "status").optString("type").equals("notLoaded")) {
            loadedThreads.remove(id);
            busy = false;
            resume(id);
          } else applyThread(r, id, readOnly);
        });
  }

  public void refreshReadOnly() {
    if (connected && readOnly && !busy) {
      busy = true;
      changed();
      readThread(threadId, true);
    }
  }

  private static String nonempty(JSONObject object, String key) {
    return object.isNull(key) ? "" : object.optString(key, "").trim();
  }

  private String restoredTitle(JSONObject thread, String id, String previous) {
    String name = nonempty(thread, "name");
    if (!name.isEmpty()) return name;
    if (!previous.trim().isEmpty()) return previous;
    String preview = nonempty(thread, "preview");
    for (int i = 0; i < threads.length(); i++) {
      JSONObject listed = threads.optJSONObject(i);
      if (listed == null || !id.equals(listed.optString("id"))) continue;
      name = nonempty(listed, "name");
      if (!name.isEmpty()) return name;
      if (preview.isEmpty()) preview = nonempty(listed, "preview");
    }
    return preview;
  }

  private void restoreTitleFromMessages() {
    if (!threadTitle.trim().isEmpty()) return;
    for (ChatItem item : items) if (item.user() && !item.text.trim().isEmpty()) {
      String title = item.text.replaceAll("\\s+", " ").trim();
      threadTitle = title.substring(0, Math.min(title.length(), 120));
      return;
    }
  }

  private void historicalError(String turn, String message) {
    if (message.trim().isEmpty()) return;
    String id = "historical-error-" + (turn.isEmpty() ? message : turn);
    ChatItem item = find(id);
    if (item == null) { item = new ChatItem(id, "historicalError", "历史任务错误"); items.add(item); }
    item.detail = "历史记录，并非本次接续失败。\n" + message;
    item.status = "failed";
  }

  private void applyThread(JSONObject r, String id, boolean readonly) {
    JSONObject thread = Json.object(r, "thread");
    for (int n = 0; n < threads.length(); n++) {
      JSONObject listed = threads.optJSONObject(n);
      if (listed != null && listed.optString("id").equals(id) && thread.has("status"))
        try { listed.put("status", thread.opt("status")); } catch (JSONException ignored) {}
    }
    if (!id.equals(threadId) && !id.equals(resumeDraftThread)) {
      draftImages.clear();
      draftImagePreviews.clear();
    }
    resumeDraftThread = "";
    if (!id.equals(threadId)) select(id);
    if (!readonly && r.has("model") && !r.isNull("model")) model = r.optString("model");
    if (!readonly && r.has("reasoningEffort")) reasoningEffort = r.isNull("reasoningEffort") ? "" : r.optString("reasoningEffort");
    this.readOnly = readonly;
    remoteRunning = Json.object(thread, "status").optString("type").equals("active");
    threadId = thread.optString("id", id);
    if (!readonly) loadedThreads.add(threadId);
    else loadedThreads.remove(threadId);
    recoveringThreads.remove(threadId);
    threadTitle = restoredTitle(thread, threadId, threadTitle);
    items.clear();
    if (!remoteRunning) approvals.clear();
    busy = false;
    turnId = "";
    JSONArray turns = thread.optJSONArray("turns");
    if (turns != null)
      for (int i = 0; i < turns.length(); i++) {
        JSONObject turn = turns.optJSONObject(i);
        if (turn == null) continue;
        JSONArray list = turn.optJSONArray("items");
        if (list != null)
          for (int k = 0; k < list.length(); k++) {
            JSONObject item = list.optJSONObject(k);
            if (item != null) items.add(ChatItem.from(item));
          }
        if (turn.optJSONObject("error") != null)
          historicalError(turn.optString("id"), Json.object(turn, "error").optString("message"));
        if (!readonly && i == turns.length() - 1
            && turn.optString("status").equals("inProgress")
            && (thread.optJSONObject("status") == null
                || Json.object(thread, "status").optString("type").equals("active"))) {
          busy = true;
          turnId = turn.optString("id");
        }
      }
    restoreTitleFromMessages();
    if (!readonly && Json.object(thread, "status").optString("type").equals("active")) busy = true;
    stopping = false;
    state = busy ? "正在处理…" : "已连接";
    String cwd = thread.optString("cwd");
    if (!cwd.startsWith("/")) cwd = r.optString("cwd");
    threadCwd = cwd.startsWith("/") ? cwd : "";
    if (cwd.startsWith("/")) {
      profile.cwd = cwd;
      profile.projects.add(cwd);
    }
    if (scopeDepth == 0) { profile.lastThread = threadId; save(); }
    if (r.optBoolean("remoteReplay")) {
      // Acknowledge only after the baseline has been applied on the UI thread.
      // This prevents a very fast RPC response from racing event delivery.
      replaying = true; replayThread=threadId;
      RpcClient current = rpc;
      int g = generation;
      String restoreThread = threadId;
      worker.execute(() -> {
        try { current.notify("remote/replay", Json.obj("threadId", restoreThread)); }
        catch (Exception e) { main.post(() -> { if (g == generation) failConnection(e, transport); }); }
      });
    } else if (id.equals(loadingThread)) {
      loadingThread="";
      if (id.equals(restoringThread)) restoringThread="";
      main.post(this::restoreNext);
    }
    changed();
  }

  public void send(String text) { send(text, false); }

  private void send(String text, boolean textOnly) {
    if (replaying || !connected || busy || readOnly || attaching || (text.trim().isEmpty() && draftImages.isEmpty())) return;
    final List<String> attachments = textOnly ? new ArrayList<>() : new ArrayList<>(draftImages);
    busy = true;
    stopping = false;
    unsentText = text;
    error = "";
    state = "正在思考…";
    changed();
    if (threadId.isEmpty()) {
      JSONObject p = permissionMode.startParams();
      // ThreadStartParams uses kebab-case configuration values; turn overrides use tagged
      // camel-case objects.
      try {
        if (!profile.cwd.isEmpty()) p.put("cwd", profile.cwd);
        if (!model.isEmpty()) p.put("model", model);
      } catch (JSONException ignored) {
      }
      request(
          "thread/start",
          p,
          r -> {
            threadId = Json.object(r, "thread").optString("id");
            if (threadId.isEmpty()) {
              busy = false;
              error = "远端没有返回会话 ID";
              changed();
              return;
            }
            threadCwd = Json.object(r, "thread").optString("cwd", r.optString("cwd", profile.cwd));
            profile.lastThread = threadId;
            loadedThreads.add(threadId);
            threadTitle = text;
            save();
            startTurn(text, attachments);
          });
    } else startTurn(text, attachments);
  }

  private final Map<String, String> pendingMessages = new HashMap<>();
  private final Set<String> readyMessages = new HashSet<>();

  private String queueKey() { return (profile == null ? "" : profile.id) + ":" + threadId; }
  public String queuedMessage() { return pendingMessages.getOrDefault(queueKey(), ""); }
  public boolean queueMessage(String text) {
    if (!canSteer() || text.trim().isEmpty() || !queuedMessage().isEmpty()) return false;
    pendingMessages.put(queueKey(), text);
    readyMessages.remove(queueKey());
    changed();
    return true;
  }
  public void cancelQueuedMessage() {
    pendingMessages.remove(queueKey()); readyMessages.remove(queueKey()); changed();
  }
  public void sendQueuedMessage() {
    if (!connected || busy || readOnly || attaching || steering || threadId.isEmpty()) return;
    String text = pendingMessages.remove(queueKey());
    readyMessages.remove(queueKey());
    if (text != null) send(text, true);
  }
  private void drainQueuedMessage() {
    if (readyMessages.contains(queueKey())) sendQueuedMessage();
  }

  public boolean canSteer() {
    return !replaying && connected && busy && !readOnly && !steering && !stopping && !turnId.isEmpty();
  }

  public boolean steer(String text) {
    if (!canSteer() || text.trim().isEmpty()) return false;
    steering = true;
    unsentText = text;
    final String target = threadId;
    Set<String> previous = new HashSet<>();
    for (ChatItem item : items) previous.add(item.id);
    request("turn/steer", Json.obj("threadId", target, "expectedTurnId", turnId,
        "input", new JSONArray().put(Json.obj("type", "text", "text", text))), r -> {
      steering = false;
      if (threadId.equals(target)) {
        boolean echoed = false;
        for (ChatItem item : items) if (!previous.contains(item.id) && item.user() && item.text.equals(text)) echoed = true;
        if (!echoed) items.add(new ChatItem("local-" + UUID.randomUUID(), "userMessage", text));
        if (unsentText.equals(text)) unsentText = "";
      }
      changed();
      drainQueuedMessage();
    });
    changed();
    return true;
  }

  private void startTurn(String text, List<String> attachments) {
    ChatItem optimistic = new ChatItem("local-" + UUID.randomUUID(), "userMessage", text);
    optimistic.images.addAll(attachments);
    items.add(optimistic);
    changed();
    JSONArray content = new JSONArray();
    if (!text.isEmpty()) content.put(Json.obj("type", "text", "text", text));
    for (String path : attachments) content.put(Json.obj("type", "localImage", "path", path));
    JSONObject params =
        Json.obj(
            "threadId",
            threadId,
            "input",
            content);
    try {
      if (!model.isEmpty()) params.put("model", model);
      if (!reasoningEffort.isEmpty()) params.put("effort", reasoningEffort);
      params.put("summary", "auto");
      permissionMode.applyTurn(params);
    } catch (JSONException ignored) {
    }
    request(
        "turn/start",
        params,
        r -> {
          unsentText = "";
          draftImages.removeAll(attachments);
          for (String path : attachments) draftImagePreviews.remove(path);
          JSONObject turn = Json.object(r, "turn");
          if (busy) {
            turnId = turn.optString("id");
            if (stopping) interrupt();
          }
          changed();
        });
  }

  public void attachImage(android.net.Uri uri) {
    if (!connected || busy || readOnly || attaching || draftImages.size() >= 4) {
      error = "连接后可添加最多 4 张图片，请等待当前操作完成"; changed(); return;
    }
    attaching = true;
    pendingImagePreview = null;
    final int token = generation;
    final SshTransport current = transport;
    changed();
    worker.execute(() -> {
      java.io.File file = null;
      try {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
          if (in == null) throw new java.io.IOException("无法读取图片");
          MediaFiles.copy(in, bytes, MediaFiles.IMAGE_LIMIT);
        }
        byte[] data = bytes.toByteArray();
        android.graphics.BitmapFactory.Options info = new android.graphics.BitmapFactory.Options();
        info.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, info);
        String mime = info.outMimeType == null ? "" : info.outMimeType;
        if (info.outWidth <= 0 || info.outHeight <= 0 || !java.util.Arrays.asList("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime))
          throw new java.io.IOException("请选择 PNG、JPEG、WebP 或 GIF 图片");
        String extension = mime.equals("image/jpeg") ? ".jpg" : "." + mime.substring(6);
        android.graphics.Bitmap preview = MediaFiles.decode(data, 256);
        main.post(() -> {
          if (token == generation) { pendingImagePreview = preview; changed(); }
        });
        file = java.io.File.createTempFile("attachment-", extension, getCacheDir());
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) { out.write(data); }
        String path = current.uploadImage(file);
        main.post(() -> {
          if (token == generation) {
            draftImages.add(path); draftImagePreviews.put(path, preview);
            pendingImagePreview = null; attaching = false; changed();
          }
        });
      } catch (Exception e) {
        main.post(() -> { if (token == generation) { pendingImagePreview = null; attaching = false; error = "图片上传失败：" + ConnectionFailure.reason(e); changed(); } });
      } finally { if (file != null) file.delete(); }
    });
  }

  public void readMedia(String source, java.io.OutputStream out, long limit) throws Exception {
    SshTransport current = transport;
    String cwd = profile == null ? "" : profile.cwd;
    if (source.startsWith("data:image/")) {
      int comma = source.indexOf(',');
      if (comma < 0 || !source.substring(0, comma).endsWith(";base64") || source.length() > limit * 2) throw new java.io.IOException("无效或过大的图片");
      byte[] bytes = android.util.Base64.decode(source.substring(comma + 1), android.util.Base64.DEFAULT);
      if (bytes.length > limit) throw new java.io.IOException("图片过大");
      out.write(bytes);
    } else if (source.startsWith("https://")) {
      java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(source).openConnection();
      c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
      try {
        if (c.getResponseCode() != 200) throw new java.io.IOException("图片服务器返回 HTTP " + c.getResponseCode());
        try (java.io.InputStream in = c.getInputStream()) { MediaFiles.copy(in, out, limit); }
      } finally { c.disconnect(); }
    } else {
      if (!connected || current == null) throw new java.io.IOException("请先连接图片所在 SSH 主机");
      current.download(MediaFiles.remotePath(source, cwd), out, limit);
    }
  }

  public void browseDirectory(
      String path,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    SshTransport current = transport;
    directoryOperation("sftp/list", path, () -> current.listDirectories(path), success, failure);
  }

  public void createProject(
      String parent,
      String name,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    SshTransport current = transport;
    directoryOperation(
        "sftp/mkdir", parent, () -> current.createDirectory(parent, name), success, failure);
  }

  private void directoryOperation(
      String method,
      String path,
      Callable<RemoteDirectory> task,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    if (!connected || transport == null) {
      failure.accept("请先连接 SSH 主机");
      return;
    }
    int g = generation;
    worker.execute(
        () -> {
          try {
            RemoteDirectory result = task.call();
            main.post(
                () -> {
                  if (g != generation) {
                    failure.accept("SSH 连接已改变，请重新打开目录");
                    return;
                  }
                  success.accept(result);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (g != generation) {
                    failure.accept("SSH 连接已改变，请重新打开目录");
                    return;
                  }
                  reportFailure(method, Json.obj("path", path), e);
                  failure.accept(
                      ConnectionFailure.operationError(method, Json.obj("path", path), e));
                });
          }
        });
  }

  public boolean useProject(String path) {
    if (profile == null || !canSwitchConversation()) {
      error = "请先连接主机，并等待当前操作结束";
      changed();
      return false;
    }
    if (!path.startsWith("/") || path.indexOf('\0') >= 0) return false;
    profile.cwd = path;
    profile.projects.add(path);
    newChat();
    save();
    return true;
  }

  public void interrupt() {
    if (replaying) return;
    if (!busy || readOnly) return;
    stopping = true;
    state = "正在停止…";
    changed();
    if (!turnId.isEmpty())
      request("turn/interrupt", Json.obj("threadId", threadId, "turnId", turnId), r -> {});
  }

  String notificationApprovalToken(JSONObject request) {
    return approvalTokens.computeIfAbsent(request, ignored -> UUID.randomUUID().toString());
  }

  boolean isAnswering(JSONObject request) { return answering.contains(request); }

  void answerNotification(String token, String decision) {
    if (!connected) return;
    for (JSONObject request : allApprovals()) {
      if (token.equals(approvalTokens.get(request)) && LiveStatusNotifications.isApproval(request)) {
        answer(request, Json.obj("decision", decision));
        return;
      }
    }
  }

  public void answer(JSONObject req, JSONObject result) {
    String target = Json.object(req, "params").optString("threadId", threadId);
    if (!target.equals(threadId)) { inConversation(target, () -> answer(req, result)); return; }
    RpcClient c = rpc;
    if (replaying || c == null || !approvals.contains(req) || !answering.add(req)) return;
    changed();
    int g = generation;
    worker.execute(
        () -> {
          try {
            c.respond(req.opt("id"), result);
            main.post(
                () -> {
                  if (g == generation) {
                    answering.remove(req);
                    inConversation(target, () -> { approvals.remove(req); changed(); });
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (g == generation) {
                    answering.remove(req);
                    inConversation(target, () -> { error = message(e); changed(); });
                  }
                });
          }
        });
  }

  public void rejectUnsupported(JSONObject req) {
    RpcClient c = rpc;
    if (c != null)
      worker.execute(
          () -> {
            try {
              c.unsupported(req.opt("id"));
            } catch (Exception ignored) {
            }
          });
  }

  private ChatItem find(String id) {
    for (ChatItem m : items) if (m.id.equals(id)) return m;
    return null;
  }

  private void event(JSONObject e) {
    String incomingMethod = e.optString("method");
    if (incomingMethod.equals("thread/status/changed") || incomingMethod.equals("turn/started")
        || incomingMethod.equals("turn/completed") || incomingMethod.equals("thread/closed") || e.has("id")) statusRevision++;
    JSONObject params = Json.object(e, "params");
    String id = params.optString("threadId");
    if (e.optString("method").equals("thread/status/changed")) {
      for (int i=0;i<threads.length();i++) {
        JSONObject listed=threads.optJSONObject(i);
        if (listed != null && listed.optString("id").equals(id))
          try { listed.put("status", params.opt("status")); } catch (JSONException ignored) {}
      }
    }
    if (e.optString("method").startsWith("remote/replay") && id.isEmpty()) id=replayThread.isEmpty() ? restoringThread : replayThread;
    if (e.optString("method").equals("serverRequest/resolved") && id.isEmpty()) {
      for (JSONObject req : allApprovals()) if (String.valueOf(req.opt("id")).equals(String.valueOf(params.opt("requestId")))) {
        id=Json.object(req,"params").optString("threadId"); break;
      }
    }
    if (!id.isEmpty() && !id.equals(threadId)) {
      if (conversations.containsKey(id) || e.has("id") || e.optString("method").equals("thread/status/changed")) inConversation(id, () -> applyEvent(e));
      return;
    }
    applyEvent(e);
  }

  private void applyEvent(JSONObject e) {
    String method = e.optString("method");
    JSONObject p = Json.object(e, "params");
    if (method.equals("remote/replayStarted")) {
      replaying = true; replayThread = threadId; changed(); return;
    }
    if (method.equals("remote/replayCompleted")) {
      replaying = false;
      if (threadId.equals(replayThread)) replayThread="";
      if (threadId.equals(loadingThread)) loadingThread="";
      changed();
      refreshOtherLegacyThreads(threadId);
      if (threadId.equals(restoringThread)) restoringThread="";
      main.post(this::restoreNext);
      return;
    }
    if (method.equals("account/rateLimits/updated")) {
      usage.update(p, false);
      changed();
      return;
    }
    // Older hosts replay journal events without per-event metadata. The replay
    // barrier also covers journal tails from other conversations on this socket.
    boolean historical = replaying || !replayThread.isEmpty();
    String eventThread = p.optString("threadId");
    if (method.equals("thread/status/changed")) {
      for (int n = 0; n < threads.length(); n++) {
        JSONObject listed = threads.optJSONObject(n);
        if (listed != null && listed.optString("id").equals(eventThread))
          try { listed.put("status", p.opt("status")); } catch (JSONException ignored) {}
      }
    }
    if (method.equals("thread/closed")
        || (method.equals("thread/status/changed") && Json.object(p, "status").optString("type").equals("notLoaded"))) {
      loadedThreads.remove(eventThread);
      if (eventThread.equals(threadId)) {
        busy = false;
        stopping = false;
        turnId = "";
        readOnly = true;
        remoteRunning = false;
        approvals.clear();
        state = "线程已释放，请接续";
        changed();
      }
      return;
    }
    if (e.has("id")) {
      if (method.equals("item/commandExecution/requestApproval")
          || method.equals("item/fileChange/requestApproval")
          || method.equals("item/tool/requestUserInput")) {
        approvals.removeIf(a -> String.valueOf(a.opt("id")).equals(String.valueOf(e.opt("id"))));
        approvals.add(e);
        remoteRunning = true;
        if (!readOnly) busy = true;
        if (p.has("turnId")) turnId = p.optString("turnId");
        changed();
      } else {
        rejectUnsupported(e);
        error = "远端请求暂不支持：" + method;
        changed();
      }
      return;
    }
    if (method.equals("serverRequest/resolved")) {
      Object id = p.opt("requestId");
      approvals.removeIf(a -> String.valueOf(a.opt("id")).equals(String.valueOf(id)));
      changed();
      return;
    }
    if (p.has("threadId") && !p.optString("threadId").equals(threadId)) return;
    if (method.equals("thread/status/changed"))
      remoteRunning = Json.object(p, "status").optString("type").equals("active");
    if (method.equals("thread/status/changed")) {
      String type = Json.object(p, "status").optString("type");
      if (type.equals("active") && !readOnly) busy = true;
      else if (type.equals("idle") || type.equals("systemError")) {
        busy = false;
        stopping = false;
        turnId = "";
        approvals.clear();
        state = "已连接";
      }
    } else if (method.equals("turn/started") && !readOnly) {
      busy = true;
      remoteRunning = true;
      turnId = Json.object(p, "turn").optString("id");
      if (stopping) interrupt();
    } else if (method.equals("turn/completed")) {
      retryAttempts = 0;
      remoteRunning = false;
      busy = false;
      stopping = false;
      turnId = "";
      approvals.clear();
      JSONObject turn = Json.object(p, "turn");
      state = turn.optString("status").equals("interrupted") ? "已停止" : "已连接";
      if (turn.optJSONObject("error") != null) {
        String message = Json.object(turn, "error").optString("message");
        if (historical) historicalError(turn.optString("id"), message);
        else error = message;
      }
      if (!historical) refreshThreads(false);
      if (!historical && turn.optString("status").equals("completed") && !readOnly && !queuedMessage().isEmpty()) {
        readyMessages.add(queueKey());
        main.post(() -> inConversation(eventThread, this::drainQueuedMessage));
      } else readyMessages.remove(queueKey());
      String completedThread = threadId;
      if (!historical) main.post(() -> {
        if (!completedThread.equals(threadId) || !foreground)
          inConversation(completedThread, this::leaveIdleConversation);
      });
    } else if (method.equals("error")) {
      String message = Json.object(p, "error").optString("message", "远端发生错误");
      if (historical) historicalError(p.optString("turnId"), message);
      else error = message;
    } else if (method.equals("item/started") || method.equals("item/completed")) {
      ChatItem incoming = ChatItem.from(Json.object(p, "item"));
      if (incoming.type.equals("reasoning")) incoming.status = method.equals("item/completed") ? "completed" : "inProgress";
      if (incoming.user()) unsentText = "";
      ChatItem old = find(incoming.id);
      if (old == null && incoming.user())
        for (ChatItem m : items)
          if (m.id.startsWith("local-") && m.text.equals(incoming.text)) {
            old = m;
            break;
          }
      if (old == null) items.add(incoming);
      else {
        int i = items.indexOf(old);
        if (method.equals("item/started") && incoming.text.isEmpty()) incoming.text = old.text;
        if (incoming.type.equals("reasoning") && incoming.detail.trim().isEmpty()) incoming.detail = old.detail;
        items.set(i, incoming);
      }
    } else if (method.equals("item/reasoning/summaryPartAdded")) {
      ChatItem m = find(p.optString("itemId"));
      if (m != null && !m.detail.isEmpty() && !m.detail.endsWith("\n\n")) m.detail += "\n\n";
    } else if (method.endsWith("/delta")
        || method.endsWith("/outputDelta")
        || method.endsWith("/summaryTextDelta")
        || method.equals("item/reasoning/textDelta")) {
      String id = p.optString("itemId");
      ChatItem m = find(id);
      if (m == null) {
        String type =
            method.contains("agentMessage")
                ? "agentMessage"
                : method.contains("plan")
                    ? "plan"
                    : method.contains("reasoning") ? "reasoning" : "commandExecution";
        m = new ChatItem(id, type, type.equals("reasoning") ? "思考过程" : "");
        items.add(m);
      }
      if (m.prose()) m.text += p.optString("delta");
      else m.detail += p.optString("delta");
    } else if (method.equals("turn/diff/updated")) {
      ChatItem d = find("diff-" + p.optString("turnId"));
      if (d == null) {
        d = new ChatItem("diff-" + p.optString("turnId"), "fileChange", "查看本轮更改");
        items.add(d);
      }
      d.detail = p.optString("diff");
    } else if ((method.equals("warning") || method.equals("configWarning")) && !historical) {
      error = p.optString("message", p.optString("summary"));
    }
    restoreTitleFromMessages();
    changed();
  }
}
