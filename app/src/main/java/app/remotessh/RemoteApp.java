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
  public String serviceTier = "", inheritedServiceTier = "", effectiveServiceTier = "", effectiveModel = "";
  boolean inheritedTierKnown, tierOverrideApplied;
  public ModelSettings modelSettings = new ModelSettings();
  public boolean modelSettingsLoading;
  private int settingsRevision;
  private boolean serviceTierProtocol;
  public PermissionMode permissionMode = PermissionMode.INHERIT;
  PermissionMode effectivePermissionMode = PermissionMode.INHERIT;
  String journalPath = "";
  ConversationJournal journal = new ConversationJournal();
  private String journalScript;
  InteractionHistory interactions = new InteractionHistory();
  final ThumbnailCache thumbnails = new ThumbnailCache();
  private final Set<String> journalPolling = new HashSet<>();
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
  final List<FileAttachment> draftFiles = new ArrayList<>();
  public final Map<String, android.graphics.Bitmap> draftImagePreviews = new HashMap<>();
  public android.graphics.Bitmap pendingImagePreview;
  public boolean attaching;
  boolean attachingFiles;
  public int imageUploadIndex, imageUploadCount;
  public final List<ChatItem> items = new ArrayList<>();
  public final List<JSONObject> approvals = new ArrayList<>();
  public JSONArray threads = new JSONArray(), models = new JSONArray();
  public RemoteVersion remoteVersion = new RemoteVersion();
  private boolean restartingRemote;
  private final List<String> remoteSessionIdentities = new ArrayList<>();
  private String remoteSessionFolder = "";
  public boolean modelsLoading;
  public String modelsStatus = "";
  public String nextCursor = "";
  public Runnable observer;
  public final Set<Runnable> stateListeners = new HashSet<>();
  public boolean replaying;
  public String trustHost = "", trustFingerprint = "";
  private CompletableFuture<Boolean> trustAnswer;
  private volatile SshTransport transport;
  private RpcClient rpc;
  private volatile int generation;
  int connectionGeneration() { return generation; }
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

  private final ConversationPreferences preferences = new ConversationPreferences(this);
  final ConversationStore conversationStore = new ConversationStore(this);
  private final TurnController turns = new TurnController(this);
  private final AttachmentController attachments = new AttachmentController(this);
  private final DirectoryController directories = new DirectoryController(this);
  private final ArrayDeque<String> restoreQueue = new ArrayDeque<>();
  private String restoringThread = "", pendingNavigation = "", loadingThread = "", replayThread = "";
  private String loadingStage="";
  private ThreadSnapshot preparedThread;
  private boolean multiSessionHost = true;

  void checkpoint() { conversationStore.checkpoint(); }

  List<String> conversationIds() { return conversationStore.ids(); }

  void inConversation(String id, Runnable action) { conversationStore.dispatch(id, action); }

  boolean canSwitchConversation() {
    return connected && !restartingRemote && !attaching && !(busy && threadId.isEmpty()) && restoringThread.isEmpty() && loadingThread.isEmpty() && restoreQueue.isEmpty();
  }

  boolean recoveryInProgress() {
    return connected && (!loadingThread.isEmpty() || !restoringThread.isEmpty() || !restoreQueue.isEmpty() || !replayThread.isEmpty() || replaying);
  }

  String recoveryStatus() {
    if(connecting)return state;
    if(!replayThread.isEmpty() || replaying)return "正在同步离线消息…";
    if(!loadingThread.isEmpty())return loadingStage.isEmpty()?"正在获取对话历史…":loadingStage;
    return recoveryInProgress()?"正在恢复其他对话…":"";
  }

  private void select(String id) {
    if (!id.equals(threadId)) leaveIdleConversation();
    conversationStore.select(id);
    if (profile != null) { profile.lastThread=id; save(); }
  }

  List<JSONObject> allApprovals() { return conversationStore.approvals(); }

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
    String id = restoreQueue.removeFirst(); restoringThread=id; loadingThread=id;loadingStage="正在检查对话状态…";
    inConversation(id, () -> {
      busy=true;changed();
      request("thread/read", Json.obj("threadId", id, "includeTurns", false), snapshot -> {
        if (Json.object(Json.object(snapshot, "thread"), "status").optString("type").equals("active")) {
          loadingStage="正在接续对话…";changed();
          request("thread/resume", Json.obj("threadId", id), r -> {
            applyThread(r, id, false);
            if (!r.optBoolean("remoteReplay")) { restoringThread=""; main.post(this::restoreNext); }
          });
        } else {
          loadingStage="正在获取对话历史…";changed();
          request("thread/read",Json.obj("threadId",id,"includeTurns",true),history->{
            applyThread(history,id,true);restoringThread="";
            request("thread/unsubscribe", Json.obj("threadId", id), ignored -> {});
            main.post(this::restoreNext);
          });
        }
      });
    });
  }

  public void setForeground(boolean visible) {
    setForeground(visible, false);
  }

  void setForeground(boolean visible, boolean keepConversation) {
    foreground = visible;
    // Keep the selected writer across backgrounding; an async question may arrive later.
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
    ConversationState cached = conversationStore.get(id);
    if (cached != null) return !cached.replaying && (cached.remoteRunning
        || (cached.busy && !cached.turnId.isEmpty()) || !cached.approvals.isEmpty());
    return Json.object(thread, "status").optString("type").equals("active");
  }

  void leaveIdleConversation() {
    if (attaching || busy || replaying || currentThreadRunning() || threadId.isEmpty()) return;
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
    busy = true; loadingThread = id; loadingStage="正在获取对话历史…";changed();
    readThread(id, true);
  }

  JSONObject visibleApproval() {
    if (!replayThread.isEmpty()) return null;
    if (!replaying && !approvals.isEmpty()) return approvals.get(0);
    checkpoint();
    for (ConversationState c : conversationStore.values())
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
      ConversationState c = conversationStore.get(id);
      if (loadedThreads.contains(id) || !c.remoteRunning || !statusPolling.add(id)) continue;
      worker.execute(() -> client.request("thread/read", Json.obj("threadId", id, "includeTurns", false))
          .whenComplete((r, e) -> main.post(() -> {
            if (g != generation) return;
            statusPolling.remove(id);
            if (e != null || revision != statusRevision || loadedThreads.contains(id)) return;
            inConversation(id, () -> {
              remoteRunning = Json.object(Json.object(r, "thread"), "status").optString("type").equals("active");
              if (!remoteRunning) { busy=false; turnId=""; clearServerRequests(); }
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
    worker.execute(() -> c.request("thread/read", Json.obj("threadId", id, "includeTurns", true), (r, e) -> {
          ThreadSnapshot snapshot=e==null?new ThreadSnapshot(r):null;
          main.post(() -> {
          if (g != generation) return;
          readPolling = false;
          if (!id.equals(threadId) || !readOnly || busy || revision != statusRevision) return;
          if (e == null && snapshot.failure==null) {
            ThreadSnapshot previous=preparedThread;preparedThread=snapshot;
            try {applyThread(r,id,true);}finally{preparedThread=previous;}
          }
          else {
            state = "历史自动刷新失败，可手动重试";
            changed();
          }
        });
        }));
  }

  void pollConversationJournals() {
    if(!connected || transport==null || profile==null)return;
    final SshTransport current=transport;final int g=generation;final long revision=statusRevision;
    for(String id:conversationIds())inConversation(id,()->{
      String path=journalPath;ConversationJournal reader=journal;
      if(!path.startsWith("/") || !path.endsWith(".jsonl") || !journalPolling.add(id))return;
      long offset=reader.offset;
      JSONArray questionCalls=new JSONArray();for(String key:interactions.entries.keySet())if(key.startsWith("call:"))questionCalls.put(key.substring(5));
      worker.execute(()->{
        try {
          String script;
          synchronized(this){
            if(journalScript==null)try(java.io.InputStream input=getAssets().open("conversation_state.py")){
              java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();MediaFiles.copy(input,bytes,65536);
              journalScript=bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            }
            script=journalScript;
          }
          JSONObject result=current.jsonCommand(script,Json.obj("path",path,"offset",offset,"questionCalls",questionCalls),20);
          main.post(()->{
            if(g!=generation || current!=transport)return;
            journalPolling.remove(id);
            if(revision!=statusRevision)return;
            inConversation(id,()->{
              if(journal!=reader || !journalPath.equals(path))return;
              JSONArray records=result.optJSONArray("records");
              if(result.optLong("offset")==reader.offset && !result.optBoolean("reset") && result.optJSONObject("context")==null
                  && (records==null || records.length()==0))return;
              reader.apply(result,id);
              long historyRevision=interactions.revision;interactions.archive(result,id);
              PermissionMode mode=reader.context==null ? null : PermissionMode.fromRemote(reader.context.opt("approval_policy"),reader.context.optJSONObject("sandbox_policy"));
              if(mode!=null)effectivePermissionMode=mode;
              if(result.optLong("offset")>=result.optLong("size"))syncJournalQuestions();
              if(interactions.revision!=historyRevision)saveInteractions();
              changed();
            });
          });
        }catch(Exception ignored){main.post(()->{if(g==generation)journalPolling.remove(id);});}
      });
    });
  }

  void syncJournalQuestions() {
    Set<String> dismissed=getSharedPreferences("question-answers",MODE_PRIVATE).getStringSet(tierPreferenceKey(threadId),Collections.emptySet());
    journal.dismissed.addAll(dismissed);
    approvals.removeIf(req->{boolean removed=ConversationJournal.savedQuestion(req)
        && (!journal.pending.containsKey(Json.object(req,"params").optString("callId")) || journal.dismissed.contains(Json.object(req,"params").optString("callId")));
      if(removed)interactions.status(req,"expired");return removed;});
    for(JSONObject req:journal.pending.values()) {
      String call=Json.object(req,"params").optString("callId");
      if(journal.dismissed.contains(call))continue;
      boolean present=false;for(JSONObject known:approvals)if(known.optString("id").equals(req.optString("id")))present=true;
      if(!present)approvals.add(req);
      interactions.question(req);interactions.status(req,"pending");
    }
    saveInteractions();
  }

  private void clearServerRequests() { approvals.removeIf(req->{if(ConversationJournal.savedQuestion(req))return false;interactions.status(req,"expired");return true;});saveInteractions(); }

  List<ChatItem> displayItems() {return interactions.present(items);}
  long savedInteractionRevision;
  void restoreInteractions(ConversationState state, String id) { preferences.restoreInteractions(state, id); }
  private void saveInteractions() { preferences.saveInteractions(); }

  void cancelQuestion(JSONObject req) {
    String target=Json.object(req,"params").optString("threadId",threadId);
    inConversation(target,()->{
      if(!ConversationJournal.savedQuestion(req)){interrupt();return;}
      dismissSavedQuestion(req);changed();
    });
  }

  private void dismissSavedQuestion(JSONObject req) {
    interactions.status(req,"skipped");saveInteractions();
    journal.dismiss(req);approvals.remove(req);
    Set<String> old=getSharedPreferences("question-answers",MODE_PRIVATE).getStringSet(tierPreferenceKey(threadId),Collections.emptySet());
    LinkedHashSet<String> ids=new LinkedHashSet<>(old);ids.add(Json.object(req,"params").optString("callId"));
    while(ids.size()>100)ids.remove(ids.iterator().next());
    getSharedPreferences("question-answers",MODE_PRIVATE).edit().putStringSet(tierPreferenceKey(threadId),ids).apply();
  }

  void restorePermission(ConversationState state, String id) { preferences.restorePermission(state, id); }
  void choosePermission(PermissionMode mode) { permissionMode = mode; savePermission(); changed(); }
  void savePermission() { preferences.savePermission(); }
  PermissionMode displayedPermission() {return permissionMode==PermissionMode.INHERIT ? effectivePermissionMode:permissionMode;}


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
    if (conversationStore.deferChanges()) return;
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
    String previousProfileId = profile == null ? "" : profile.id;
    boolean sameHost = profile != null && profile.id.equals(p.id);
    disconnect();
    conversationStore.clear();
    if (!sameHost) permissionMode = PermissionMode.INHERIT;
    profile = p;
    journalPath="";journal=new ConversationJournal();effectivePermissionMode=PermissionMode.INHERIT;interactions=new InteractionHistory();savedInteractionRevision=0;
    reconnectThread = p.lastThread;
    model = "";
    reasoningEffort = "";
    serviceTier = ""; inheritedServiceTier = ""; effectiveServiceTier = ""; effectiveModel = "";
    inheritedTierKnown = false; tierOverrideApplied = false; serviceTierProtocol = false;
    modelSettings = new ModelSettings();
    draftImages.clear();
    draftFiles.clear();
    draftImagePreviews.clear();
    pendingImagePreview = null;
    attaching = false; attachingFiles = false;
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
              // Profiles for Wi-Fi, a DNS name, and Tailscale can all reach the
              // same remote account. Give the host helper every local legacy ID
              // so it can reuse an already-running pre-1.0.23 daemon instead of
              // starting a second app-server that conflicts on thread writers.
              java.util.LinkedHashSet<String> identities = new java.util.LinkedHashSet<>();
              synchronized (vault) {
                // A live route switch has the strongest signal: the profile we
                // just left owns the writer. After an app restart, prefer an
                // older alias that remembers the same thread over the target
                // profile, whose duplicate daemon may only contain a failed
                // resume attempt.
                if (!previousProfileId.isEmpty() && !previousProfileId.equals(p.id))
                  identities.add(previousProfileId);
                for (Profile candidate : vault.profiles)
                  if (!candidate.id.equals(p.id)
                      && candidate.user.equals(p.user)
                      && candidate.command.equals(p.command)
                      && !p.lastThread.isEmpty()
                      && candidate.lastThread.equals(p.lastThread)) identities.add(candidate.id);
                identities.add(p.id);
                for (Profile candidate : vault.profiles) identities.add(candidate.id);
              }
              remoteSessionIdentities.clear();
              remoteSessionIdentities.addAll(identities);
              t.setPersistentIdentities(identities);
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
                              if (g == generation && !restartingRemote) failConnection(e, t);
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
                              "name", "remote_ssh_android", "title", "Remote", "version", "1.0.29"),
                          "capabilities", Json.obj("experimentalApi", true)),
                      r -> {
                        remoteVersion.running = RemoteVersion.fromHandshake(r);
                        completeInitialization(c, t, g);
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
                    request("remote/session", new JSONObject(), session -> {
                      remoteVersion.restartSupported = session.optBoolean("safeRestart");
                      remoteSessionFolder = session.optString("sessionFolder");
                      restoreSession(session, resumeId);
                    });
                  } else if (!resumeId.isEmpty()) viewConversation(resumeId);
                  refreshModels();
                  refreshModelSettings();
                  checkRemoteVersion(true);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (g == generation) failConnection(e, t);
                });
          }
        });
  }

  private String maintenanceScript() throws java.io.IOException {
    try (java.io.InputStream input = getAssets().open("remote_maintenance.py")) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192]; int n;
      while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
      return bytes.toString("UTF-8");
    }
  }

  public void checkRemoteVersion(boolean prompt) {
    if (!connected || transport == null || profile == null || remoteVersion.checking || remoteVersion.updating) return;
    RemoteVersion version = remoteVersion;
    SshTransport current = transport;
    int g = generation;
    String command = profile.command;
    version.checking = true; version.error = ""; changed();
    worker.execute(() -> {
      JSONObject result;
      try { result = current.maintenance(maintenanceScript(), Json.obj("action", "check", "command", command)); }
      catch (Exception e) { result = Json.obj("error", message(e)); }
      JSONObject value = result;
      main.post(() -> {
        if (g != generation || current != transport || version != remoteVersion) return;
        version.checking = false; version.accept(value);
        version.prompt = prompt && version.updateAvailable(); changed();
      });
    });
  }

  public void updateRemoteVersion(RemoteVersion expected, String target) {
    if (expected != remoteVersion || !connected || transport == null || profile == null
        || !expected.canUpdate() || !expected.latest.equals(target)) return;
    SshTransport current = transport;
    int g = generation;
    String command = profile.command;
    expected.updating = true; expected.prompt = false; expected.error = ""; changed();
    worker.execute(() -> {
      JSONObject result;
      try { result = current.maintenance(maintenanceScript(), Json.obj("action", "update", "command", command,
          "target", target, "path", expected.path, "installed", expected.installed)); }
      catch (Exception e) { result = Json.obj("error", message(e)); }
      JSONObject value = result;
      main.post(() -> {
        if (g != generation || current != transport || expected != remoteVersion) return;
        expected.updating = false; expected.accept(value);
        expected.prompt = value.has("error") || expected.needsRestart();
        changed();
      });
    });
  }

  boolean remoteHasWork() {
    if (busy || attaching || replaying || !restoringThread.isEmpty() || !loadingThread.isEmpty()
        || !restoreQueue.isEmpty() || !approvals.isEmpty()) return true;
    for (ConversationState state : conversationStore.values())
      if (state.busy || state.remoteRunning || !state.approvals.isEmpty()) return true;
    for (int i = 0; i < threads.length(); i++) {
      JSONObject thread = threads.optJSONObject(i);
      if (thread != null && threadRunning(thread)) return true;
    }
    return false;
  }

  public void restartRemoteVersion(RemoteVersion expected) {
    if (expected != remoteVersion || !connected || profile == null || restartingRemote) return;
    restartingRemote = true;
    expected.error = "";
    state = "正在停止远端任务并重启 Codex…";
    changed();
    SshTransport current = transport;
    int g = generation;
    boolean persistent = profile.persistent;
    String command = profile.command;
    JSONArray identities = new JSONArray(remoteSessionIdentities);
    String sessionFolder = remoteSessionFolder;
    worker.execute(() -> {
      try {
        if (persistent) {
          JSONObject result = current.maintenance(maintenanceScript(), Json.obj(
              "action", "restart", "command", command, "identities", identities,
              "sessionFolder", sessionFolder));
          if (result.has("error")) throw new java.io.IOException(result.optString("error"));
        }
        main.post(() -> {
          if (g != generation) return;
          Profile target = profile;
          String previousThread = threadId.isEmpty() ? target.lastThread : threadId;
          String draft = unsentText;
          String savedDraft = draftText, savedModel = model, savedEffort = reasoningEffort;
          List<String> savedImages = new ArrayList<>(draftImages);
          List<FileAttachment> savedFiles = new ArrayList<>(draftFiles);
          Map<String, android.graphics.Bitmap> savedPreviews = new HashMap<>(draftImagePreviews);
          disconnect();
          state = "正在重启远端 Codex…"; changed();
          int disconnectedGeneration = generation;
          main.postDelayed(() -> {
            if (generation != disconnectedGeneration || connected || connecting) return;
            connect(target); reconnectThread = previousThread; unsentText = draft;
            resumeDraftThread = previousThread;
            draftText = savedDraft; model = savedModel; reasoningEffort = savedEffort;
            draftImages.addAll(savedImages); draftImagePreviews.putAll(savedPreviews); draftFiles.addAll(savedFiles);
          }, persistent ? 1200 : 0);
        });
      } catch (Exception e) {
        main.post(() -> {
          if (g != generation) return;
          restartingRemote = false;
          expected.error = "重启未执行：" + message(e); changed();
        });
      }
    });
  }

  public void refreshModels() {
    if (!connected || rpc == null || modelsLoading) return;
    final RpcClient client = rpc;
    final int g = generation;
    modelsLoading = true;
    modelsStatus = "正在刷新模型…";
    changed();
    worker.execute(() -> {
      try {
        JSONArray refreshed = ModelCatalog.load(params -> {
          if (client != rpc || g != generation) throw new java.io.IOException("连接已切换");
          return client.request("model/list", params).get(35, TimeUnit.SECONDS);
        });
        main.post(() -> {
          if (g != generation || client != rpc) return;
          models = refreshed;
          serviceTierProtocol |= ServiceTierOptions.hasProtocolMetadata(refreshed);
          modelsLoading = false;
          modelsStatus = models.length() == 0 ? "远端未返回可选模型，可使用远端默认" : "已同步远端模型";
          changed();
        });
      } catch (Exception e) {
        main.post(() -> {
          if (g != generation || client != rpc) return;
          modelsLoading = false;
          modelsStatus = "刷新失败，保留上次列表：" + message(e);
          changed();
        });
      }
    });
  }

  public void refreshModelSettings() {
    if (!connected || rpc == null) return;
    String cwd = currentProject();
    if (modelSettingsLoading && modelSettings.cwd.equals(cwd)) return;
    final RpcClient client = rpc; final int g = generation, revision = ++settingsRevision;
    modelSettingsLoading = true;
    // Keep the last usable snapshot while refreshing the same project.
    if (!modelSettings.cwd.equals(cwd)) { modelSettings = new ModelSettings(); modelSettings.cwd = cwd; }
    changed();
    worker.execute(() -> {
      ModelSettings refreshed = ModelSettings.load(request -> {
        if (g != generation || client != rpc) throw new java.io.IOException("连接已切换");
        return client.request(request.getString("method"), request.getJSONObject("params")).get(20, TimeUnit.SECONDS);
      }, cwd);
      main.post(() -> {
        if (g != generation || client != rpc || revision != settingsRevision) return;
        if (!refreshed.globalFeaturesKnown && modelSettings.globalFeaturesKnown) {
          refreshed.globalFastEnabled &= modelSettings.globalFastEnabled;
          refreshed.fastEnabled &= refreshed.globalFastEnabled;
          refreshed.globalFeaturesKnown = true;
        }
        if (!refreshed.configKnown && modelSettings.configKnown && modelSettings.cwd.equals(cwd)) {
          refreshed.model = modelSettings.model; refreshed.managedModel = modelSettings.managedModel;
          refreshed.tier = modelSettings.tier; refreshed.configKnown = true;
          refreshed.developerInstructions = modelSettings.developerInstructions;
          refreshed.questionToolAllowed &= modelSettings.questionToolAllowed;
          refreshed.fastEnabled &= modelSettings.fastEnabled;
          refreshed.globalFastEnabled &= modelSettings.globalFastEnabled;
        }
        modelSettings = refreshed; modelSettingsLoading = false; changed();
      });
    });
  }

  private String tierPreferenceKey(String id) { return preferences.key(id); }
  void saveServiceTier() { preferences.saveServiceTier(); }
  void restoreServiceTier(ConversationState state, String id) { preferences.restoreServiceTier(state, id); }

  JSONObject selectedModel(String selection) {
    String id = selection;
    if (id.isEmpty()) {
      if (!threadId.isEmpty() && !effectiveModel.isEmpty()) id = effectiveModel;
      else if (modelSettings.configKnown && modelSettings.cwd.equals(currentProject())) {
        id = reasoningEffort.isEmpty() && !modelSettings.managedModel.isEmpty() ? modelSettings.managedModel : modelSettings.model;
        if (id.isEmpty()) for (int i = 0; i < models.length(); i++) {
          JSONObject entry = models.optJSONObject(i);
          if (entry != null && entry.optBoolean("isDefault")) { id = ModelOptions.id(entry); break; }
        }
      }
    }
    return ServiceTierOptions.model(models, id);
  }

  boolean supportsServiceTier() { return serviceTierProtocol || ServiceTierOptions.hasProtocolMetadata(models); }

  boolean speedSelectionEnabled() {
    return modelSettings.cwd.equals(currentProject()) ? modelSettings.fastEnabled : modelSettings.globalFastEnabled;
  }

  String defaultServiceTier(JSONObject selected) {
    String tier = inheritedTierKnown ? inheritedServiceTier : "";
    if (!inheritedTierKnown && modelSettings.configKnown && modelSettings.cwd.equals(currentProject())) tier = modelSettings.tier;
    if (!inheritedTierKnown && tier.isEmpty() && selected != null && !selected.isNull("defaultServiceTier"))
      tier = selected.optString("defaultServiceTier");
    if (tier.isEmpty() || tier.equals(ServiceTierOptions.STANDARD)) return ServiceTierOptions.STANDARD;
    if (ServiceTierOptions.hasMetadata(selected)
        && ServiceTierOptions.supportedId(selected, tier).isEmpty())
      return ServiceTierOptions.STANDARD;
    return !ServiceTierOptions.hasMetadata(selected) ? tier : ServiceTierOptions.supportedId(selected, tier);
  }

  void chooseServiceTier(String choice) { serviceTier = choice; saveServiceTier(); changed(); }

  String serviceTierSummary() {
    JSONObject selected = selectedModel(model);
    String tier = serviceTier.isEmpty() ? (!tierOverrideApplied && !effectiveServiceTier.isEmpty() ? effectiveServiceTier : defaultServiceTier(selected)) : serviceTier;
    return serviceTier.isEmpty() && tier.equals(ServiceTierOptions.STANDARD) ? "" : ServiceTierOptions.label(tier, selected);
  }

  boolean validateServiceTier() {
    if (serviceTier.isEmpty()) return true;
    if (serviceTier.equals(ServiceTierOptions.STANDARD) && supportsServiceTier()) return true;
    JSONObject selected = selectedModel(model);
    if (!supportsServiceTier() || !speedSelectionEnabled() || selected == null
        || ServiceTierOptions.supportedId(selected, serviceTier).isEmpty()) {
      error = "当前远端模型未提供所选速度档位，请在模型面板重新选择"; changed(); return false;
    }
    return true;
  }

  String requestedServiceTier() {
    if (serviceTier.isEmpty()) return defaultServiceTier(selectedModel(model));
    if (serviceTier.equals(ServiceTierOptions.STANDARD)) return serviceTier;
    return ServiceTierOptions.supportedId(selectedModel(model), serviceTier);
  }

  boolean shouldSendServiceTier() {
    if (!supportsServiceTier()) return false;
    if (!serviceTier.isEmpty() || tierOverrideApplied) return true;
    JSONObject selected = selectedModel(model);
    // A model change must not carry an inherited Fast tier onto a model which explicitly lacks it.
    return !model.isEmpty() && !model.equals(effectiveModel) && !effectiveServiceTier.isEmpty()
        && !effectiveServiceTier.equals(ServiceTierOptions.STANDARD) && ServiceTierOptions.hasMetadata(selected)
        && ServiceTierOptions.supportedId(selected, effectiveServiceTier).isEmpty();
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
    journalPolling.clear();
    recoveringThreads.clear();
    statusPolling.clear();
    statusRevision++;
    pendingImagePreview = null;
    imageUploadIndex = imageUploadCount = 0;
    generation++;
    approvalTokens.clear();
    answering.clear();
    main.removeCallbacks(reconnectTask);
    retryEligible = false;
    retryAttempts = 0;
    readPolling = false;
    replaying = false;
    turns.clearReady();
    restoreQueue.clear(); restoringThread=""; pendingNavigation=""; loadingThread=""; replayThread="";
    loadingStage="";preparedThread=null;
    conversationStore.clear();
    remoteRunning = false;
    reconnectThread = "";
    resumeDraftThread = "";
    loadedThreads.clear();
    usage.clear();
    attaching = false; attachingFiles = false;
    if (trustAnswer != null) trustAnswer.complete(false);
    trustAnswer = null;
    trustHost = "";
    RpcClient old = rpc;
    rpc = null;
    modelsLoading = false;
    modelsStatus = "";
    modelSettingsLoading = false; settingsRevision++;
    remoteVersion = new RemoteVersion();
    restartingRemote = false;
    remoteSessionIdentities.clear();
    remoteSessionFolder = "";
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

  void request(
      String method, JSONObject params, java.util.function.Consumer<JSONObject> success) {
    request(method,params,success,null);
  }

  void request(String method,JSONObject params,java.util.function.Consumer<JSONObject> success,java.util.function.Consumer<Throwable> failure) {
    RpcClient c = rpc;
    if (c == null) {
      if(failure!=null)failure.accept(new java.io.IOException("SSH 未连接"));
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
            c.request(method, params,
                    (r, failureCause) -> {
                        ThreadSnapshot snapshot=failureCause==null && (method.equals("thread/resume") || (method.equals("thread/read") && params.optBoolean("includeTurns"))) ? new ThreadSnapshot(r) : null;
                        Throwable e=failureCause!=null?failureCause:snapshot==null?null:snapshot.failure;
                        main.post(
                            () -> {
                              if (g != generation) return;
                              ThreadSnapshot previous=preparedThread;preparedThread=snapshot;
                              try {
                              Runnable completion = () -> {
                              if (e != null) {
                                if(failure!=null)failure.accept(e);
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
                              } finally {preparedThread=previous;}
                            });
                    }));
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
                if (!remoteRunning) { busy=false; turnId=""; clearServerRequests(); }
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
    List<FileAttachment> files = new ArrayList<>(draftFiles);
    Map<String, android.graphics.Bitmap> previews = new HashMap<>(draftImagePreviews);
    connect(profile);
    reconnectThread = id;
    resumeDraftThread = id;
    unsentText = draft;
    draftImages.addAll(images);
    draftFiles.addAll(files);
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
    draftFiles.clear();
    draftImagePreviews.clear();
    attaching = false; attachingFiles = false;
    threadId = "";
    threadTitle = "";
    threadCwd = "";
    journalPath="";journal=new ConversationJournal();effectivePermissionMode=PermissionMode.INHERIT;interactions=new InteractionHistory();savedInteractionRevision=0;
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
    boolean cached = conversationStore.contains(id) && loadedThreads.contains(id);
    select(id);
    if (cached) { changed(); return; }
    error=""; busy=true; loadingThread=id; loadingStage="正在接续对话…";changed();
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
      busy = true;loadingThread=threadId;loadingStage="正在刷新对话历史…";
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
    ThreadSnapshot snapshot=preparedThread!=null && preparedThread.response==r ? preparedThread : new ThreadSnapshot(r);
    JSONObject thread = Json.object(r, "thread");
    for (int n = 0; n < threads.length(); n++) {
      JSONObject listed = threads.optJSONObject(n);
      if (listed != null && listed.optString("id").equals(id) && thread.has("status"))
        try { listed.put("status", thread.opt("status")); } catch (JSONException ignored) {}
    }
    if (!id.equals(threadId) && !id.equals(resumeDraftThread)) {
      draftImages.clear();
      draftFiles.clear();
      draftImagePreviews.clear();
    }
    resumeDraftThread = "";
    if (!id.equals(threadId)) select(id);
    if (!readonly && r.has("model") && !r.isNull("model")) model = r.optString("model");
    if (!readonly && r.has("model") && !r.isNull("model")) effectiveModel = r.optString("model");
    if (!readonly && r.has("serviceTier")) {
      serviceTierProtocol = true;
      // remoteReplay is a historical baseline; it must not replace a saved choice/base.
      if (!r.optBoolean("remoteReplay") || r.optBoolean("remoteConfigCurrent") || effectiveServiceTier.isEmpty())
        effectiveServiceTier = r.isNull("serviceTier") ? ServiceTierOptions.STANDARD : r.optString("serviceTier");
      if (!inheritedTierKnown && serviceTier.isEmpty() && !tierOverrideApplied) {
        inheritedServiceTier = effectiveServiceTier; inheritedTierKnown = true; saveServiceTier();
      }
    }
    if (!readonly && r.has("reasoningEffort")) reasoningEffort = r.isNull("reasoningEffort") ? "" : r.optString("reasoningEffort");
    PermissionMode remotePermission=PermissionMode.fromRemote(r.opt("approvalPolicy"),r.optJSONObject("sandbox"));
    if(remotePermission!=null && (!r.optBoolean("remoteReplay") || journal.context==null))effectivePermissionMode=remotePermission;
    String path=thread.optString("path");
    if(path.startsWith("/") && !path.equals(journalPath)){journalPath=path;journal=new ConversationJournal();}
    this.readOnly = readonly;
    remoteRunning = Json.object(thread, "status").optString("type").equals("active");
    threadId = thread.optString("id", id);
    if (!readonly) loadedThreads.add(threadId);
    else loadedThreads.remove(threadId);
    recoveringThreads.remove(threadId);
    threadTitle = restoredTitle(thread, threadId, threadTitle);
    Map<String, ChatItem> knownReasoning = new HashMap<>();
    for (ChatItem item : items)
      if (item.type.equals("reasoning") && !item.detail.trim().isEmpty()) knownReasoning.put(item.id, item);
    items.clear();
    if (!remoteRunning) clearServerRequests();
    busy = false;
    turnId = "";
    JSONArray turns = thread.optJSONArray("turns");
    if (turns != null)
      for (int i = 0; i < turns.length(); i++) {
        JSONObject turn = turns.optJSONObject(i);
        if (turn == null) continue;
        for (ChatItem incoming : snapshot.turns.get(i)) {
          if (incoming.type.equals("reasoning")) {
            preserveReasoning(incoming, knownReasoning.get(incoming.id));
            if (incoming.status.isEmpty()) incoming.status =
                i == turns.length() - 1 && turn.optString("status").equals("inProgress")
                    && (thread.optJSONObject("status") == null || remoteRunning) ? "inProgress" : "completed";
          }
          items.add(incoming);
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
    saveInteractions();
    restoreTitleFromMessages();
    if (!readonly && Json.object(thread, "status").optString("type").equals("active")) busy = true;
    main.post(this::pollConversationJournals);
    stopping = false;
    state = busy ? "正在处理…" : "已连接";
    String cwd = thread.optString("cwd");
    if (!cwd.startsWith("/")) cwd = r.optString("cwd");
    threadCwd = cwd.startsWith("/") ? cwd : "";
    if (cwd.startsWith("/")) {
      profile.cwd = cwd;
      profile.projects.add(cwd);
    }
    if (!conversationStore.isScoped()) { profile.lastThread = threadId; save(); }
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

  SshTransport currentTransport() { return transport; }
  void executeBackground(Runnable action) { worker.execute(action); }
  boolean isRestartingRemote() { return restartingRemote; }
  void rememberLoadedThread() { loadedThreads.add(threadId); }
  void noteServiceTierProtocol() { serviceTierProtocol = true; }

  public void send(String text) { turns.send(text); }
  public String queuedMessage() { return turns.queuedMessage(); }
  public boolean queueMessage(String text) { return turns.queueMessage(text, false); }
  public boolean queueMessage(String text, boolean includeAttachments) { return turns.queueMessage(text, includeAttachments); }
  public void cancelQueuedMessage() { turns.cancelQueuedMessage(); }
  public void sendQueuedMessage() { turns.sendQueuedMessage(); }
  int remainingImages() { return turns.remainingImages(); }
  int remainingFiles() { return turns.remainingFiles(); }
  public boolean canSteer() { return turns.canSteer(); }
  public boolean steer(String text) { return turns.steer(text); }
  void drainQueuedMessage() { turns.drain(); }

  public void attachImage(android.net.Uri uri) { attachments.attachImage(uri); }
  public void attachImages(List<android.net.Uri> selected) { attachments.attachImages(selected); }
  public void attachFiles(List<android.net.Uri> selected) { attachments.attachFiles(selected); }
  boolean canAttach() { return attachments.canAttach(); }
  String attachmentUnavailable() { return attachments.attachmentUnavailable(); }
  public void readMedia(String source, java.io.OutputStream out, long limit) throws Exception {
    attachments.readMedia(source, out, limit);
  }
  public void readMedia(String source, java.io.OutputStream out, long limit, java.util.function.BooleanSupplier active) throws Exception {
    attachments.readMedia(source, out, limit, active);
  }

  public void browseDirectory(String path, java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) { directories.browseDirectory(path, success, failure); }

  public void createProject(String parent, String name, java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) { directories.createProject(parent, name, success, failure); }

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
    if(ConversationJournal.savedQuestion(req)) {answerSavedQuestion(req,result);return;}
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
                    inConversation(target, () -> { interactions.answered(req,result);saveInteractions();approvals.remove(req); changed(); });
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

  private void answerSavedQuestion(JSONObject req,JSONObject result) {
    if(!connected || replaying || rpc==null || !approvals.contains(req) || !answering.add(req))return;
    changed();
    java.util.function.Consumer<Throwable> failure=e->{answering.remove(req);};
    Runnable send=()->{
      if(attaching || stopping || restartingRemote || (busy && turnId.isEmpty())) {
        answering.remove(req);error="请等待当前操作完成后提交回答";changed();return;
      }
      StringBuilder text=new StringBuilder("回答上一轮的问题：\n");
      JSONArray questions=Json.object(req,"params").optJSONArray("questions");JSONObject answers=Json.object(result,"answers");
      if(questions==null){answering.remove(req);return;}
      for(int i=0;i<questions.length();i++) {
        JSONObject q=questions.optJSONObject(i);if(q==null)continue;
        JSONArray values=Json.object(answers,q.optString("id")).optJSONArray("answers");
        if(values==null || values.length()==0 || values.optString(0).trim().isEmpty()){answering.remove(req);return;}
        text.append(q.optString("question")).append('\n').append(values.optString(0)).append("\n\n");
      }
      Runnable accepted=()->{interactions.answered(req,result);saveInteractions();answering.remove(req);dismissSavedQuestion(req);};
      if(busy && !turnId.isEmpty()) {
        request("turn/steer",Json.obj("threadId",threadId,"expectedTurnId",turnId,"input",new JSONArray().put(Json.obj("type","text","text",text.toString()))),r->{accepted.run();changed();},failure);
      }else {
        if(!validateServiceTier()){answering.remove(req);return;}
        busy=true;stopping=false;state="正在提交回答…";unsentText=text.toString();
        turns.startTurn(text.toString(),Collections.emptyList(),accepted,failure);
      }
    };
    if(readOnly)request("thread/resume",Json.obj("threadId",threadId),r->{applyThread(r,threadId,false);send.run();},failure);
    else send.run();
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

  private void preserveReasoning(ChatItem incoming, ChatItem previous) {
    if (incoming.type.equals("reasoning") && incoming.detail.trim().isEmpty()
        && previous != null && previous.type.equals("reasoning")) incoming.detail = previous.detail;
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
      if (conversationStore.contains(id) || e.has("id") || e.optString("method").equals("thread/status/changed")) inConversation(id, () -> applyEvent(e));
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
        clearServerRequests();
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
        interactions.question(e);saveInteractions();
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
      approvals.removeIf(a -> {if(!String.valueOf(a.opt("id")).equals(String.valueOf(id)))return false;interactions.status(a,"resolved");return true;});saveInteractions();
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
        clearServerRequests();
        state = "已连接";
      }
    } else if (method.equals("turn/started") && !readOnly) {
      for(JSONObject req:new ArrayList<>(approvals))if(ConversationJournal.savedQuestion(req))dismissSavedQuestion(req);
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
      clearServerRequests();
      JSONObject turn = Json.object(p, "turn");
      // Some servers deliver the final summary only in the turn snapshot.
      JSONArray completedItems = turn.optJSONArray("items");
      if (completedItems != null) for (int i = 0; i < completedItems.length(); i++) {
        JSONObject item = completedItems.optJSONObject(i);
        if (item == null || !item.optString("type").equals("reasoning")) continue;
        ChatItem incoming = ChatItem.from(item), previous = find(incoming.id);
        preserveReasoning(incoming, previous);
        incoming.status = "completed";
        if (previous == null) items.add(incoming);
        else items.set(items.indexOf(previous), incoming);
      }
      // Journal replay may complete older turns after loading an active baseline.
      if (!historical) for (ChatItem item : items)
        if (item.type.equals("reasoning") && item.status.equals("inProgress")) item.status = "completed";
      state = turn.optString("status").equals("interrupted") ? "已停止" : "已连接";
      if (turn.optJSONObject("error") != null) {
        String message = Json.object(turn, "error").optString("message");
        if (historical) historicalError(turn.optString("id"), message);
        else error = message;
      }
      if (!historical && turn.optString("status").equals("completed") && liveStatus != null && approvals.isEmpty())
        liveStatus.complete(threadId);
      if (!historical) refreshThreads(false);
      if (!historical && turn.optString("status").equals("completed") && !readOnly && !queuedMessage().isEmpty()) {
        turns.markReady();
        main.post(() -> inConversation(eventThread, this::drainQueuedMessage));
      } else turns.clearCurrentReady();
      String completedThread = threadId;
      if (!historical) main.post(() -> {
        if (!completedThread.equals(threadId))
          inConversation(completedThread, this::leaveIdleConversation);
      });
    } else if (method.equals("error")) {
      String message = Json.object(p, "error").optString("message", "远端发生错误");
      if (historical) historicalError(p.optString("turnId"), message);
      else error = message;
    } else if (method.equals("item/started") || method.equals("item/completed")) {
      ChatItem incoming = ChatItem.from(Json.object(p, "item"));
      incoming.turnId=p.optString("turnId",turnId);
      if (incoming.type.equals("reasoning")) incoming.status = method.equals("item/completed") ? "completed" : "inProgress";
      if (incoming.user()) unsentText = "";
      ChatItem old = find(incoming.id);
      if (old == null && incoming.user())
        for (ChatItem m : items)
          if (m.id.startsWith("local-") && m.text.equals(incoming.text) && m.images.equals(incoming.images)) {
            old = m;
            break;
          }
      if (old == null) items.add(incoming);
      else {
        int i = items.indexOf(old);
        if (method.equals("item/started") && incoming.text.isEmpty()) incoming.text = old.text;
        if(incoming.phase.isEmpty())incoming.phase=old.phase;
        preserveReasoning(incoming, old);
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
        m.turnId=p.optString("turnId",turnId);
        if (type.equals("reasoning")) m.status = "inProgress";
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
