package app.remotessh;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private RemoteApp app;
  private int bg, fg, muted, panel, line;
  private LinearLayout root, body, conversation, messages, empty, sidebar, composer;
  private ConversationScrollView scroll;
  private String renderedThread = "";
  private TextView status, title, error, busyLabel, modelButton, projectLabel, usageLabel;
  private EditText input, keyEditor;
  private IconView send, permissionButton;
  private ProgressBar runningIndicator;
  private LinearLayout attachmentStrip, queuedBar;
  private HorizontalScrollView attachmentScroll;
  private final Map<String, LinearLayout> mediaRows = new HashMap<>();
  private final Map<String, List<String>> mediaSources = new HashMap<>();
  private static final java.util.concurrent.ExecutorService mediaWorker = java.util.concurrent.Executors.newFixedThreadPool(2);
  private String pendingDownload = "", pendingDownloadHost = "";
  private int pendingImageGeneration = -1;
  private String pendingImageHost = "", pendingImageThread = "", pendingImageProject = "";

  private View jump;
  private final Map<String, TextView> texts = new HashMap<>();
  private final Map<String, MessageBodyView> messageBodies = new HashMap<>();
  private final Map<String, String> rendered = new HashMap<>();
  private final Map<String, ChatItem> currentItems = new HashMap<>();
  private final List<String> order = new ArrayList<>();
  private Dialog trustDialog, approvalDialog, drawer, editorDialog;
  private Dialog versionDialog;
  private RemoteVersion shownVersion;
  private RemoteBrowser browser;
  private DirectoryPicker directoryPicker;
  private LinearLayout readOnlyBar;
  private String shownApproval = "";
  private QuestionForm questionForm;
  private Bundle restoredQuestion;
  private String restoredQuestionToken = "";
  private boolean scheduled, firstRender = true, wide;
  private boolean latestScrollPending, messageUpdatesPaused;
  private final Runnable pinLatest = () -> {
    if (scroll == null || conversation == null || isDestroyed()) return;
    scroll.pin();
  };

  private void followLatest() {
    if (scroll == null || !scroll.isFollowing()) return;
    latestScrollPending = true;
    scroll.removeCallbacks(pinLatest);
    scroll.post(pinLatest);
  }

  private int pulse, pollTick;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          if (app.currentThreadRunning() && busyLabel != null && scroll != null && scroll.isFollowing())
            busyLabel.setText(
                (app.state.startsWith("正在停止") ? "正在停止" : "正在处理")
                    + new String[] {"·", "··", "···"}[pulse++ % 3]);
          if (++pollTick % 7 == 0) app.pollReadOnly();
          if (pollTick % 22 == 0 && app.connected && browser != null && browser.isShowing()) app.refreshThreads(false);
          app.main.postDelayed(this, 450);
        }
      };

  @Override
  protected void attachBaseContext(Context base) {
    float scale = base.getSharedPreferences("display", MODE_PRIVATE).getFloat("fontScale", 0);
    if (scale > 0) {
      Configuration config = new Configuration(base.getResources().getConfiguration());
      config.fontScale *= scale;
      base = base.createConfigurationContext(config);
    }
    super.attachBaseContext(base);
  }

  private void openNotificationConversation(Intent intent) {
    if (app.ownsIntent(intent)) {
      String id = intent.getStringExtra("threadId");
      if (id != null && !id.isEmpty() && !id.equals(app.threadId)) {
        if (input != null) app.draftText=input.getText().toString();
        app.openConversation(id);
      }
      String liveToken = intent.getStringExtra("liveToken");
      if (liveToken != null && !liveToken.isEmpty()) app.liveStatus.acknowledgeViewed(liveToken);
    }
  }
  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent); setIntent(intent); openNotificationConversation(intent);
  }

  @Override
  public void onCreate(Bundle saved) {
    setTheme(isDark() ? R.style.AppThemeDark : R.style.AppTheme);
    super.onCreate(saved);
    app = (RemoteApp) getApplication();
    openNotificationConversation(getIntent());
    colors();
    build();
    if (saved != null) {
      app.draftText=saved.getString("draft", "");
      input.setText(app.draftText);
      pendingDownload = saved.getString("download", "");
      pendingDownloadHost = saved.getString("downloadHost", "");
      pendingImageGeneration = saved.getInt("imageGeneration", -1);
      pendingImageHost = saved.getString("imageHost", "");
      pendingImageThread = saved.getString("imageThread", "");
      pendingImageProject = saved.getString("imageProject", "");
      restoredQuestion=saved.getBundle("questionAnswers");
      restoredQuestionToken=saved.getString("questionToken", "");
    }
  }

  private boolean isDark() {
    boolean dark =
        (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    int mode = getPreferences(0).getInt("theme", 0);
    if (mode != 0) dark = mode == 2;
    return dark;
  }

  private void colors() {
    boolean dark = isDark();
    bg = Color.parseColor(dark ? "#000000" : "#FFFFFF");
    fg = Color.parseColor(dark ? "#ECECEC" : "#171717");
    muted = Color.parseColor(dark ? "#A6A6A6" : "#777777");
    panel = Color.parseColor(dark ? "#303030" : "#F4F4F4");
    line = Color.parseColor(dark ? "#424242" : "#E8E8E8");
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            dark
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  @Override
  protected void onStart() {
    super.onStart();
    app.observer = this::scheduleRender;
    app.setForeground(true);
    if (app.connected) { app.refreshThreads(false); app.pollReadOnly(); }
    app.changed();
    render();
    app.main.post(ticker);
  }

  @Override
  protected void onStop() {
    super.onStop();
    app.observer = null;
    app.setForeground(false, pendingImageGeneration >= 0);
    app.main.removeCallbacks(ticker);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putString("draft", input.getText().toString());
    out.putString("download", pendingDownload);
    out.putString("downloadHost", pendingDownloadHost);
    out.putInt("imageGeneration", pendingImageGeneration);
    out.putString("imageHost", pendingImageHost);
    out.putString("imageThread", pendingImageThread);
    out.putString("imageProject", pendingImageProject);
    if (questionForm!=null && !shownApproval.isEmpty()) {
      out.putString("questionToken",shownApproval);out.putBundle("questionAnswers",questionForm.saveAnswers());
    }
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onDestroy() {
    if (scroll != null) scroll.removeCallbacks(pinLatest);
    if (trustDialog != null) trustDialog.dismiss();
    if (approvalDialog != null) approvalDialog.dismiss();
    if (drawer != null) drawer.dismiss();
    if (editorDialog != null) editorDialog.dismiss();
    if (browser != null) browser.dismiss();
    if (versionDialog != null) versionDialog.dismiss();
    if (directoryPicker != null) directoryPicker.dismiss();
    super.onDestroy();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private TextView text(String s, int size, int color) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setFontFeatureSettings("kern");
    return v;
  }

  private GradientDrawable rounded(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private void clickable(View v, Runnable r) {
    PressFeedback.apply(v, bg == Color.BLACK);
    v.setOnClickListener(
        w -> {
          w.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          r.run();
        });
  }

  private IconView icon(String name, String label, Runnable action) {
    IconView i = new IconView(this, name, fg);
    i.setContentDescription(label);
    i.setBackground(
        new RippleDrawable(
            android.content.res.ColorStateList.valueOf(line), null, rounded(fg, 30)));
    clickable(i, action);
    return i;
  }

  private TextView action(String label, Runnable r) {
    TextView t = text(label, 15, fg);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(12), dp(16), dp(12));
    t.setMinHeight(dp(48));
    t.setBackground(rounded(panel, 24));
    clickable(t, r);
    return t;
  }

  private void build() {
    root = column();
    root.setBackgroundColor(bg);
    root.setFitsSystemWindows(true);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int bottom = insets.getSystemWindowInsetBottom();
          root.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              bottom);
          return insets.consumeSystemWindowInsets();
        });
    body = row();
    body.setGravity(Gravity.TOP);
    root.addView(body, new LinearLayout.LayoutParams(-1, -1));
    wide = getResources().getConfiguration().screenWidthDp >= 840;
    int available = getResources().getConfiguration().screenWidthDp - (wide ? 260 : 0);
    if (wide) {
      sidebar = column();
      sidebar.setBackgroundColor(panel);
      sidebar.setPadding(dp(16), dp(20), dp(16), dp(16));
      ScrollView navigationScroll = new ScrollView(this);
      navigationScroll.setFillViewport(true);
      navigationScroll.setBackgroundColor(panel);
      navigationScroll.addView(sidebar);
      body.addView(navigationScroll, new LinearLayout.LayoutParams(dp(260), -1));
    }
    LinearLayout center = column();
    body.addView(center, new LinearLayout.LayoutParams(0, -1, 1));
    LinearLayout bar = row();
    bar.setPadding(dp(12), dp(4), dp(12), dp(4));
    center.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));
    bar.addView(
        icon("menu", "打开连接和聊天记录", this::showDrawer), new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout heading = column();
    heading.setGravity(Gravity.CENTER);
    title = text(getString(R.string.app_name), 18, fg);
    title.setMaxLines(1);
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setTypeface(null, Typeface.BOLD);
    heading.addView(title);
    status = text("通过 SSH 连接你的电脑", 11, muted);
    status.setMaxLines(1);
    status.setEllipsize(TextUtils.TruncateAt.END);
    heading.addView(status);
    bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    clickable(heading, this::conversationInfo);
    runningIndicator = new ProgressBar(this);
    runningIndicator.setIndeterminate(true);
    runningIndicator.setContentDescription("当前对话正在运行");
    bar.addView(runningIndicator, new LinearLayout.LayoutParams(dp(20), dp(20)));
    bar.addView(
        icon(
            "more",
            "对话菜单",
            this::chatMenu),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    projectLabel = text("", 12, muted);
    projectLabel.setPadding(dp(20), 0, dp(20), dp(4));
    projectLabel.setMaxLines(1);
    projectLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    clickable(projectLabel, this::conversationInfo);
    center.addView(projectLabel);
    usageLabel = text("", 12, muted);
    usageLabel.setPadding(dp(20), dp(4), dp(20), dp(8));
    usageLabel.setMaxLines(2);
    usageLabel.setEllipsize(TextUtils.TruncateAt.END);
    clickable(usageLabel, this::usageInfo);
    center.addView(usageLabel);
    readOnlyBar = column();
    readOnlyBar.setPadding(dp(16), dp(2), dp(16), dp(6));
    TextView readonly = text("只读历史 · 尚未取得写入权限", 13, muted);
    readonly.setPadding(dp(4), dp(6), dp(4), dp(6));
    readOnlyBar.addView(readonly);
    LinearLayout roActions = row();
    TextView[] readOnlyControls = {action("刷新", app::refreshReadOnly), action("接续", () -> app.resume(app.threadId)), action("新对话", this::startNewChat)};
    for (int i=0;i<readOnlyControls.length;i++) {
      readOnlyControls[i].setSingleLine(true); readOnlyControls[i].setEllipsize(TextUtils.TruncateAt.END);
      readOnlyControls[i].setPadding(dp(8),dp(12),dp(8),dp(12));
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
      if (i<readOnlyControls.length-1) cp.setMarginEnd(dp(3)); roActions.addView(readOnlyControls[i],cp);
    }
    readOnlyBar.addView(roActions);
    LinearLayout.LayoutParams reconnectParams=new LinearLayout.LayoutParams(-1,-2); reconnectParams.topMargin=dp(3);
    readOnlyBar.addView(action("重连并接续", app::reconnectAndResume),reconnectParams);
    readOnlyBar.setVisibility(View.GONE);
    center.addView(readOnlyBar);
    error = text("", 13, isDark() ? Color.rgb(255, 151, 137) : Color.rgb(165, 47, 36));
    error.setContentDescription("查看连接错误");
    error.setPadding(dp(20), dp(12), dp(20), dp(12));
    error.setBackground(rounded(panel, 12));
    error.setMaxLines(2);
    error.setEllipsize(TextUtils.TruncateAt.END);
    clickable(error, this::showConnectionError);
    center.addView(error);
    FrameLayout viewport = new FrameLayout(this);
    center.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
    scroll = new ConversationScrollView(this);
    scroll.setContentDescription("对话消息");
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.getViewTreeObserver().addOnPreDrawListener(() -> {
      if (latestScrollPending) {
        pinLatest.run();
        latestScrollPending = false;
      }
      return true;
    });
    viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    conversation = column();
    conversation.setGravity(Gravity.CENTER_HORIZONTAL);
    scroll.addView(conversation, new ScrollView.LayoutParams(-1, -2));
    conversation.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot) followLatest();
    });
    scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot || r - l != or - ol) followLatest();
    });
    empty = column();
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(28), dp(32), dp(28), dp(56));
    conversation.addView(empty, new LinearLayout.LayoutParams(-1, -1));
    messages = column();
    messages.setPadding(dp(24), dp(20), dp(24), dp(12));
    conversation.addView(
        messages, new LinearLayout.LayoutParams(wide ? dp(Math.min(740, available)) : -1, -2));
    busyLabel = text("正在处理…", 14, muted);
    busyLabel.setPadding(dp(24), dp(8), dp(24), dp(20));
    conversation.addView(
        busyLabel, new LinearLayout.LayoutParams(wide ? dp(Math.min(740, available)) : -1, -2));
    jump = icon("arrow", "滚动到最新消息", () -> { scroll.follow(); render(); followLatest(); });
    jump.setRotation(180);
    jump.setBackground(rounded(panel, 24));
    FrameLayout.LayoutParams jp =
        new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    jp.bottomMargin = dp(12);
    viewport.addView(jump, jp);
    jump.setVisibility(View.GONE);
    scroll.setOnScrollChangeListener(
        (v, x, y, ox, oy) -> {
          jump.setVisibility(scroll.isFollowing() ? View.GONE : View.VISIBLE);
          if (scroll.isFollowing() && messageUpdatesPaused) scheduleRender();
        });
    LinearLayout footer = column();
    footer.setGravity(Gravity.CENTER_HORIZONTAL);
    footer.setPadding(dp(16), dp(8), dp(16), dp(8));
    center.addView(footer);
    queuedBar = row();
    footer.addView(queuedBar, new LinearLayout.LayoutParams(wide ? dp(Math.min(720, available - 32)) : -1, -2));
    composer = column();
    composer.setBackground(rounded(panel, 28));
    composer.setPadding(dp(8), dp(4), dp(8), dp(8));
    footer.addView(
        composer, new LinearLayout.LayoutParams(wide ? dp(Math.min(720, available - 32)) : -1, -2));
    input = new EditText(this);
    input.setTextColor(fg);
    input.setHintTextColor(muted);
    input.setTextSize(16);
    input.setHint("发送消息");
    input.setBackgroundColor(Color.TRANSPARENT);
    input.setMinLines(1);
    input.setMaxLines(6);
    input.setPadding(dp(12), dp(12), dp(8), dp(8));
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setContentDescription("消息输入框");
    attachmentStrip = row();
    attachmentStrip.setPadding(dp(4), dp(8), dp(4), dp(4));
    attachmentScroll = new HorizontalScrollView(this);
    attachmentScroll.setHorizontalScrollBarEnabled(false);
    attachmentScroll.addView(attachmentStrip, new HorizontalScrollView.LayoutParams(-2, -2));
    composer.addView(attachmentScroll, new LinearLayout.LayoutParams(-1, -2));
    composer.addView(input, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout controls = row();
    composer.addView(controls);
    controls.addView(
        icon("plus", "添加图片", this::pickImage),
        new LinearLayout.LayoutParams(dp(40), dp(40)));
    modelButton = text("默认模型", 12, fg);
    modelButton.setPadding(dp(4), dp(6), dp(4), dp(6));
    modelButton.setMinHeight(dp(40));
    modelButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    modelButton.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(line), null, rounded(fg, 8)));
    modelButton.setContentDescription("选择模型与推理强度");
    clickable(modelButton, this::chooseModel);
    LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(0, -2, 1);
    modelParams.setMargins(0, 0, dp(4), 0);
    controls.addView(modelButton, modelParams);
    permissionButton = icon("shield", "权限：沿用远端权限", this::choosePermission);
    controls.addView(permissionButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
    send = icon("arrow", "发送消息", this::sendMessage);
    controls.addView(send, new LinearLayout.LayoutParams(dp(40), dp(40)));
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            app.draftText=s.toString();
            updateSend();
          }

          public void afterTextChanged(Editable e) {}
        });
    input.setOnKeyListener(
        (v, key, event) -> {
          if (key == KeyEvent.KEYCODE_ENTER
              && event.isCtrlPressed()
              && event.getAction() == KeyEvent.ACTION_DOWN) {
            sendMessage();
            return true;
          }
          return false;
        });
  }

  private void scheduleRender() {
    if (scheduled) return;
    scheduled = true;
    app.main.postDelayed(
        () -> {
          scheduled = false;
          if (!isDestroyed()) render();
        },
        40);
  }

  private boolean nearBottom() {
    return scroll.nearBottom();
  }

  private void render() {
    if (!renderedThread.equals(app.threadId)) {
      renderedThread = app.threadId;
      if (input != null) { input.setText(app.draftText); input.setSelection(input.length()); }
      scroll.follow();
      messageUpdatesPaused = false;
    }
    title.setText(app.threadTitle.isEmpty() ? (app.threadId.isEmpty() ? getString(R.string.app_name) : "未命名对话") : app.threadTitle);
    String project = app.currentProject();
    projectLabel.setText("项目 · " + (project.isEmpty() ? "远端默认目录" : project));
    projectLabel.setVisibility(app.profile == null ? View.GONE : View.VISIBLE);
    usageLabel.setText(app.usage.summary());
    usageLabel.setVisibility(app.connected ? View.VISIBLE : View.GONE);
    readOnlyBar.setVisibility(app.readOnly ? View.VISIBLE : View.GONE);
    input.setEnabled(!app.readOnly);
    input.setHint(app.readOnly ? "此线程只读，请接续或新建对话" : "发送消息");
    status.setText(app.connected ? "●  " + app.profile.title() + "  ·  SSH" : app.state);
    status.setTextColor(app.connected ? Color.rgb(48, 148, 112) : muted);
    error.setText(app.error.isEmpty() ? "" : app.error.split("\n", 2)[0] + "\n点按查看原因和诊断信息");
    error.setVisibility(app.error.isEmpty() ? View.GONE : View.VISIBLE);
    renderAttachments();
    renderQueuedMessage();
    updateSend();
    PermissionMode displayedPermission=app.displayedPermission();
    permissionButton.icon = permissionIcon(displayedPermission);
    permissionButton.color = permissionColor(displayedPermission);
    permissionButton.setContentDescription("权限：" + displayedPermission.label);
    permissionButton.setTooltipText(displayedPermission.label + (app.permissionMode==PermissionMode.INHERIT && displayedPermission!=PermissionMode.INHERIT ? " · 当前远端状态" : ""));
    permissionButton.invalidate();
    modelButton.setText(ModelOptions.displayName(app.models, app.model)
        + (app.reasoningEffort.isEmpty() ? "" : " · " + ModelOptions.label(app.reasoningEffort))
        + (app.serviceTierSummary().isEmpty() ? "" : " · " + app.serviceTierSummary()));
    modelButton.setTooltipText(modelButton.getText());
    runningIndicator.setVisibility(app.currentThreadRunning() ? View.VISIBLE : View.GONE);
    if (scroll.isFollowing()) {
      messageUpdatesPaused = false;
      jump.setTooltipText("滚动到最新消息");
      List<ChatItem> visibleItems = new ArrayList<>();
      for (ChatItem source : app.items) {
        ChatItem snapshot = new ChatItem(source.id, source.type, source.text);
        snapshot.detail = source.detail; snapshot.status = source.status;
        snapshot.images.addAll(source.images);
        visibleItems.add(snapshot);
      }
      empty.setVisibility(visibleItems.isEmpty() && !app.busy ? View.VISIBLE : View.GONE);
      messages.setVisibility(visibleItems.isEmpty() ? View.GONE : View.VISIBLE);
      busyLabel.setVisibility(app.currentThreadRunning() ? View.VISIBLE : View.GONE);
      if (app.remoteRunning && app.readOnly) busyLabel.setText("远端运行中 · 自动刷新历史");
      if (empty.getVisibility() == View.VISIBLE) renderEmpty();
      List<String> desired = new ArrayList<>();
      for (ChatItem item : visibleItems) desired.add(item.id);
      if (!desired.equals(order)) {
        boolean appendOnly = desired.size() >= order.size() && desired.subList(0, order.size()).equals(order);
        int from = appendOnly ? order.size() : 0;
        if (!appendOnly) {
          messages.removeAllViews();
          texts.clear();
          messageBodies.clear();
          mediaRows.clear();
          mediaSources.clear();
          rendered.clear();
          currentItems.clear();
        }
        order.clear();
        order.addAll(desired);
        for (int i = from; i < visibleItems.size(); i++) addItem(visibleItems.get(i), appendOnly && !firstRender);
      }
      for (ChatItem item : visibleItems) {
        currentItems.put(item.id, item);
        String key = item.text + "\u0000" + item.detail + "\u0000" + item.status + item.images.toString();
        if (!key.equals(rendered.get(item.id))) {
          rendered.put(item.id, key);
          MessageBodyView messageBody = messageBodies.get(item.id);
          if (messageBody != null) messageBody.setSource(item.text);
          TextView view = texts.get(item.id);
          if (view != null) {
            // Empty reasoning items are valid protocol events, not missing replies.
            ((View) view.getParent()).setVisibility(item.type.equals("reasoning")
                && !item.status.equals("inProgress") && item.detail.trim().isEmpty() ? View.GONE : View.VISIBLE);
            view.setText(
                item.prose()
                    ? (item.user() ? item.text : Markdown.render(item.text, muted, panel, this::openLink))
                    : toolTitle(item));
          }
        }
        renderMedia(item);
      }
      if (!visibleItems.isEmpty()) followLatest();
      firstRender = false;
    } else {
      messageUpdatesPaused = true;
      jump.setVisibility(View.VISIBLE);
      jump.setTooltipText("消息区已暂停刷新，回到底部查看最新内容");
    }
    if (wide) fillNavigation(sidebar, false);
    if (browser != null && browser.isShowing()) browser.refresh();
    if (drawer != null && drawer.isShowing())
      fillNavigation((LinearLayout) drawer.findViewById(R.id.navigation), true);
    showPending();
  }

  private String toolTitle(ChatItem m) {
    String symbol = m.status.equals("completed") ? "✓" : m.status.equals("failed") ? "!" : "›";
    return symbol + "  " + m.text.replaceAll("\\s+", " ") + "  ›";
  }

  private void renderEmpty() {
    empty.removeAllViews();
    IconView mark = new IconView(this, "computer", fg);
    mark.setBackground(rounded(panel, 28));
    empty.addView(mark, new LinearLayout.LayoutParams(dp(88), dp(88)));
    TextView h = text(app.connected ? "想在远端做些什么？" : "你的电脑，随时连接", 26, fg);
    h.setTypeface(null, Typeface.BOLD);
    h.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
    hp.topMargin = dp(26);
    empty.addView(h, hp);
    TextView sub = text(app.connected ? "从一个问题开始，让工作继续。" : "连接 SSH 主机，在这里继续你的工作。", 14, muted);
    sub.setGravity(Gravity.CENTER);
    sub.setLineSpacing(dp(5), 1);
    sub.setPadding(0, dp(12), 0, dp(28));
    empty.addView(sub);
    if (app.connected) {
      for (String suggestion : new String[] {"介绍一下这个项目", "检查项目中的问题", "继续之前的工作"}) {
        TextView chip =
            action(
                suggestion + "  ↗",
                () -> {
                  input.setText(suggestion);
                  input.setSelection(suggestion.length());
                  input.requestFocus();
                });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(wide ? dp(340) : -1, -2);
        cp.bottomMargin = dp(10);
        empty.addView(chip, cp);
      }
    } else {
      TextView button =
          action(
              app.connecting ? "取消连接" : "连接电脑",
              () -> {
                if (app.connecting) app.disconnect();
                else if (app.vault.profiles.isEmpty()) editProfile(new Profile(), true);
                else showDrawer();
              });
      button.setTextColor(bg);
      button.setBackground(rounded(fg, 28));
      empty.addView(button, new LinearLayout.LayoutParams(dp(180), dp(52)));
    }
  }

  private void addItem(ChatItem item, boolean animate) {
    LinearLayout wrap = column();
    wrap.setGravity(item.user() ? Gravity.END : Gravity.START);
    LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
    wp.bottomMargin = dp(item.prose() ? 28 : 2);
    messages.addView(wrap, wp);
    TextView content = text("", item.prose() ? 16 : 13, item.prose() ? fg : muted);
    content.setLineSpacing(dp(5), 1);
    content.setTextIsSelectable(true);
    content.setLinksClickable(true);
    content.setMovementMethod(LinkMovementMethod.getInstance());
    if (!item.type.equals("agentMessage")) texts.put(item.id, content);
    currentItems.put(item.id, item);
    if (item.user()) {
      content.setBackground(rounded(panel, 22));
      content.setPadding(dp(18), dp(12), dp(18), dp(12));
      content.setMaxWidth(
          dp(wide ? 510 : Math.max(180, getResources().getConfiguration().screenWidthDp - 92)));
    } else if (!item.prose()) {
      content.setTextIsSelectable(false);
      content.setBackground(rounded(panel, 16));
      content.setPadding(dp(10), dp(6), dp(10), dp(6));
      content.setSingleLine(true);
      content.setEllipsize(TextUtils.TruncateAt.END);
      clickable(
          content,
          () -> {
            ChatItem latest = currentItems.get(item.id);
            if (latest != null) showDetails(latest);
          });
    }
    if (item.type.equals("agentMessage")) {
      MessageBodyView messageBody = new MessageBodyView(this,fg,muted,panel,this::openLink,this::copy);
      messageBodies.put(item.id,messageBody); wrap.addView(messageBody,new LinearLayout.LayoutParams(-1,-2));
    } else wrap.addView(content, new LinearLayout.LayoutParams(item.prose() ? -2 : -1, -2));
    LinearLayout media = column();
    mediaRows.put(item.id, media);
    wrap.addView(media, new LinearLayout.LayoutParams(-1, -2));
    if (item.type.equals("agentMessage")) {
      LinearLayout actions = row();
      IconView copy = icon("copy", "复制回复", () -> copy(currentItems.get(item.id).text));
      actions.addView(copy, new LinearLayout.LayoutParams(dp(40), dp(40)));
      wrap.addView(actions);
    }
    if (animate) {
      wrap.setAlpha(0);
      wrap.setTranslationY(dp(6));
      wrap.animate().alpha(1).translationY(0).setDuration(180).start();
    }
  }

  private void updateSend() {
    if (send == null) return;
    boolean hasText = !input.getText().toString().trim().isEmpty();
    boolean stop = app.busy && !hasText;
    send.icon = stop ? "stop" : "arrow";
    boolean enabled =
        !app.replaying && !app.readOnly
            && !app.attaching
            && (app.busy ? (stop || app.canSteer()) : (app.connected && (hasText || !app.draftImages.isEmpty())));
    send.setAlpha(enabled ? 1 : .28f);
    send.color = bg;
    send.setBackground(rounded(fg, 24));
    send.setContentDescription(stop ? "停止生成" : app.busy ? "发送引导消息" : "发送消息");
    send.setEnabled(enabled);
    send.invalidate();
  }

  private void sendMessage() {
    if (app.readOnly) {
      toast("此线程正在其他客户端运行，请先接续或新建对话");
      return;
    }
    if (app.busy) {
      String guidance = input.getText().toString().trim();
      if (guidance.isEmpty()) app.interrupt();
      else showRunningSendOptions(guidance);
      return;
    }
    if (!app.connected) {
      showDrawer();
      return;
    }
    String s = input.getText().toString().trim();
    if (app.attaching) { toast("图片正在上传，请稍候"); return; }
    if (s.isEmpty() && app.draftImages.isEmpty()) return;
    app.send(s);
    input.setText("");
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(input.getWindowToken(), 0);
  }

  private void showRunningSendOptions(String text) {
    final String thread = app.threadId, turn = app.turnId;
    LinearLayout options = column();
    options.setPadding(dp(20), 0, dp(20), dp(8));
    TextView preview = text(text, 14, muted);
    preview.setMaxLines(2); preview.setEllipsize(TextUtils.TruncateAt.END);
    preview.setPadding(dp(8), dp(4), dp(8), dp(16)); options.addView(preview);
    Dialog[] holder = new Dialog[1];
    options.addView(action("立即引导", () -> {
      if (thread.equals(app.threadId) && turn.equals(app.turnId) && app.steer(text)) {
        if (input.getText().toString().trim().equals(text)) input.setText("");
      } else toast("本轮状态已变化，请重新发送");
      holder[0].dismiss();
    }));
    TextView later = action("稍后发送", () -> {
      if (thread.equals(app.threadId) && turn.equals(app.turnId) && app.queueMessage(text)) {
        if (input.getText().toString().trim().equals(text)) input.setText("");
      } else toast(app.queuedMessage().isEmpty() ? "本轮状态已变化，请重新发送" : "已有待发消息，请先取消或发送");
      holder[0].dismiss();
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
    options.addView(later, lp);
    holder[0] = sheet("发送方式", options);
  }

  private void renderQueuedMessage() {
    queuedBar.removeAllViews();
    String queued = app.queuedMessage();
    queuedBar.setVisibility(queued.isEmpty() ? View.GONE : View.VISIBLE);
    if (queued.isEmpty()) return;
    TextView preview = text("待发送 · " + queued, 12, muted);
    preview.setMaxLines(1); preview.setEllipsize(TextUtils.TruncateAt.END);
    preview.setPadding(dp(8), dp(8), dp(8), dp(8));
    queuedBar.addView(preview, new LinearLayout.LayoutParams(0, -2, 1));
    if (app.connected && !app.busy && !app.readOnly)
      queuedBar.addView(icon("arrow", "发送待发消息", app::sendQueuedMessage), new LinearLayout.LayoutParams(dp(40), dp(40)));
    queuedBar.addView(icon("close", "取消待发消息", app::cancelQueuedMessage), new LinearLayout.LayoutParams(dp(40), dp(40)));
  }

  private void pickImage() {
    if (!app.connected || app.busy || app.readOnly || app.attaching) { toast("请先连接并等待当前操作完成"); return; }
    if (app.draftImages.size() >= MediaFiles.MAX_ATTACHMENTS) { toast("每条消息最多 " + MediaFiles.MAX_ATTACHMENTS + " 张图片"); return; }
    if (pendingImageGeneration >= 0) { toast("请先完成当前选图"); return; }
    LinearLayout choices = column(); choices.setPadding(dp(20), 0, dp(20), dp(16));
    TextView hint = text("可多选，目前还能添加 " + (MediaFiles.MAX_ATTACHMENTS - app.draftImages.size()) + " 张。相册的搜索和多选由相册 App 提供；不支持时可使用系统选择器。", 13, muted);
    hint.setPadding(dp(4), 0, dp(4), dp(12)); choices.addView(hint);
    Dialog[] holder = new Dialog[1];
    String[] labels = {"手机相册", "其他相册 / 图片 App", "系统选择器"};
    ImagePicker.Source[] sources = ImagePicker.Source.values();
    for (int i = 0; i < labels.length; i++) {
      ImagePicker.Source source = sources[i];
      TextView option = action(labels[i], () -> { holder[0].dismiss(); launchImagePicker(source); });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8);
      choices.addView(option, params);
    }
    holder[0] = sheet("选择图片来源", choices);
  }

  private String imageProject() {
    return app.threadCwd + "\n" + (app.profile == null ? "" : app.profile.cwd);
  }

  private void launchImagePicker(ImagePicker.Source source) {
    if (!app.connected || app.busy || app.readOnly || app.attaching || app.profile == null) {
      toast("请先连接并等待当前操作完成"); return;
    }
    int remaining = MediaFiles.MAX_ATTACHMENTS - app.draftImages.size();
    if (remaining <= 0) { toast("每条消息最多 " + MediaFiles.MAX_ATTACHMENTS + " 张图片"); return; }
    pendingImageGeneration = app.connectionGeneration(); pendingImageHost = app.profile.id;
    pendingImageThread = app.threadId; pendingImageProject = imageProject();
    if (!ImagePicker.launch(this, source, remaining)) {
      pendingImageGeneration = -1; toast("没有可用的图片选择器，请安装或启用相册 / 文件 App");
    }
  }

  private void renderAttachments() {
    attachmentStrip.removeAllViews();
    attachmentScroll.setVisibility(app.attaching || !app.draftImages.isEmpty() ? View.VISIBLE : View.GONE);
    int index = 0;
    for (String path : new ArrayList<>(app.draftImages)) {
      addAttachmentPreview(app.draftImagePreviews.get(path), path, ++index);
    }
    if (app.attaching) {
      addAttachmentPreview(app.pendingImagePreview, null, index + 1);
      TextView uploading = text("图片上传中…" + (app.imageUploadCount > 1 ? "（" + app.imageUploadIndex + " / " + app.imageUploadCount + "）" : ""), 13, muted);
      uploading.setContentDescription("图片上传中…"); attachmentStrip.addView(uploading);
    }
  }

  private void addAttachmentPreview(android.graphics.Bitmap bitmap, String path, int index) {
    FrameLayout tile = new FrameLayout(this);
    tile.setBackground(rounded(bg, 8));
    tile.setClipToOutline(true);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(88));
    params.setMarginEnd(dp(8));
    attachmentStrip.addView(tile, params);
    if (bitmap != null) {
      ImageView preview = new ImageView(this);
      preview.setImageBitmap(bitmap);
      preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
      preview.setContentDescription("待发送图片 " + index);
      tile.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView label = text("图片 " + index, 12, muted);
      label.setGravity(Gravity.CENTER);
      tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
    }
    if (path != null) {
      IconView remove = icon("close", "移除图片 " + index, () -> {
        if (app.busy) return;
        app.draftImages.remove(path);
        app.draftImagePreviews.remove(path);
        app.changed();
      });
      remove.setBackground(rounded(panel, 8));
      remove.setEnabled(!app.busy);
      tile.addView(remove, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END));
    }
  }

  private void renderMedia(ChatItem item) {
    LinearLayout row = mediaRows.get(item.id);
    List<String> sources = MediaFiles.images(item);
    if (row == null || sources.equals(mediaSources.get(item.id))) return;
    mediaSources.put(item.id, sources);
    row.removeAllViews();
    String hostId = app.profile == null ? "" : app.profile.id;
    for (String source : sources) {
      ImageView preview = new ImageView(this);
      preview.setAdjustViewBounds(true);
      preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
      preview.setMaxHeight(dp(320));
      preview.setContentDescription("消息图片，点击查看大图");
      row.addView(preview, new LinearLayout.LayoutParams(-1, -2));
      TextView loading = text("正在加载图片…", 13, muted);
      row.addView(loading);
      Runnable load = () -> {
        loading.setText("正在加载图片…");
        mediaWorker.execute(() -> {
          try {
            if (app.profile == null || !hostId.equals(app.profile.id)) return;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            app.readMedia(source, bytes, MediaFiles.IMAGE_LIMIT);
            android.graphics.Bitmap bitmap = MediaFiles.decode(bytes.toByteArray());
            app.main.post(() -> {
              if (isDestroyed() || app.profile == null || !hostId.equals(app.profile.id)) { bitmap.recycle(); return; }
              if (!scroll.isFollowing()) {
                // Keep the existing placeholder geometry while reading. Reload
                // this image when the reader explicitly returns to live content.
                bitmap.recycle(); mediaSources.remove(item.id); return;
              }
              if (mediaRows.get(item.id) != row || preview.getParent() != row) { bitmap.recycle(); return; }
              preview.setImageBitmap(bitmap);
              loading.setText("保存图片");
              loading.setOnClickListener(v -> saveMedia(source));
              PressFeedback.apply(loading, bg == Color.BLACK);
              preview.setOnClickListener(v -> {
                ImageView full = new ImageView(this);
                full.setAdjustViewBounds(true); full.setImageBitmap(bitmap);
                new AlertDialog.Builder(this).setView(full)
                    .setPositiveButton("保存图片", (d, w) -> saveMedia(source)).setNegativeButton("关闭", null).show();
              });
              PressFeedback.apply(preview, bg == Color.BLACK);
            });
          } catch (Exception e) {
            app.main.post(() -> {
              if (isDestroyed()) return;
              if (!scroll.isFollowing()) { mediaSources.remove(item.id); return; }
              loading.setText("图片加载失败，点按重试：" + ConnectionFailure.reason(e));
            });
          }
        });
      };
      loading.setOnClickListener(v -> load.run());
      PressFeedback.apply(loading, bg == Color.BLACK);
      load.run();
    }
  }

  private void openLink(String source) {
    if (source.startsWith("https://") || source.startsWith("http://")) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(source))); }
      catch (ActivityNotFoundException e) { toast("没有可打开链接的浏览器"); }
    } else saveMedia(source);
  }

  private void saveMedia(String source) {
    try {
      String resolved = source.startsWith("data:") || source.startsWith("https://") ? source
          : MediaFiles.remotePath(source, app.profile == null ? "" : app.profile.cwd);
      if (pendingDownload.length() > 0) { toast("请先完成当前保存操作"); return; }
      pendingDownload = resolved;
      pendingDownloadHost = app.profile == null ? "" : app.profile.id;
      String name = source.startsWith("data:") ? "image.png" : android.net.Uri.parse(resolved).getLastPathSegment();
      if (name == null || name.isEmpty()) name = "download";
      startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream")
          .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), 44);
    } catch (Exception e) { pendingDownload = ""; toast(ConnectionFailure.reason(e)); }
  }

  private void copy(String s) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), s));
    toast("已复制");
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  private void showConnectionError() {
    String diagnostic = app.error.isEmpty() ? app.lastError : app.error;
    if (diagnostic.isEmpty()) {
      toast("暂无错误记录");
      return;
    }
    TextView detail = text(diagnostic, 14, fg);
    detail.setTextIsSelectable(true);
    detail.setPadding(dp(20), dp(12), dp(20), dp(12));
    ScrollView sc = new ScrollView(this);
    sc.addView(detail);
    new AlertDialog.Builder(this)
        .setTitle("连接信息")
        .setView(sc)
        .setNegativeButton("复制诊断", (d, w) -> copy(diagnostic))
        .setNeutralButton(
            app.unsentText.isEmpty() ? null : "恢复消息",
            (d, w) -> {
              input.setText(app.unsentText);
              input.setSelection(input.length());
              app.clearError();
            })
        .setPositiveButton("知道了", (d, w) -> app.clearError())
        .show();
  }

  /** Full-height editor: only the form scrolls; actions remain above the keyboard. */
  private Dialog profileDialog(String heading, ScrollView form, LinearLayout footer) {
    if (drawer != null) drawer.dismiss();
    Dialog dialog = new Dialog(this);
    LinearLayout shell = column();
    shell.setId(R.id.connection_editor);
    shell.setBackgroundColor(bg);
    LinearLayout header = row();
    header.setPadding(dp(8), dp(8), dp(8), dp(8));
    TextView title = text(heading, 21, fg);
    title.setTypeface(null, Typeface.BOLD);
    title.setPadding(dp(16), 0, 0, 0);
    header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    title.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(
        icon("close", "关闭连接设置", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48)));
    shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
    form.setFillViewport(true);
    form.setClipToPadding(false);
    form.setContentDescription("SSH 连接表单");
    shell.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
    footer.setPadding(dp(20), dp(10), dp(20), dp(16));
    shell.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    shell.setFocusableInTouchMode(true);
    dialog.setContentView(shell);
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      window.setSoftInputMode(
          WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
              | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      window.setGravity(wide ? Gravity.CENTER : Gravity.BOTTOM);
      if (Build.VERSION.SDK_INT >= 30) {
        window.setDecorFitsSystemWindows(false);
        shell.setOnApplyWindowInsetsListener(
            (v, insets) -> {
              android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
              android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
              shell.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
              return WindowInsets.CONSUMED;
            });
      } else shell.setFitsSystemWindows(true);
    }
    dialog.setOnDismissListener(
        d -> {
          if (editorDialog == dialog) {
            editorDialog = null;
            keyEditor = null;
          }
        });
    dialog.show();
    if (window != null) window.setLayout(wide ? dp(560) : -1, -1);
    shell.requestFocus();
    shell.requestApplyInsets();
    editorDialog = dialog;
    return dialog;
  }

  private int permissionColor(PermissionMode mode) {
    switch (mode) {
      case DEFAULT: return Color.rgb(40, 151, 110);
      case READ_ONLY: return Color.rgb(62, 133, 225);
      case FULL: return Color.rgb(221, 133, 37);
      default: return muted;
    }
  }

  private String permissionIcon(PermissionMode mode) {
    switch (mode) {
      case DEFAULT: return "shieldCheck";
      case READ_ONLY: return "lock";
      case FULL: return "bolt";
      default: return "shield";
    }
  }

  private void choosePermission() {
    LinearLayout choices = column();
    choices.setPadding(dp(12), 0, dp(12), dp(4));
    Dialog[] holder = new Dialog[1];
    for (PermissionMode mode : PermissionMode.values()) {
      LinearLayout option = row();
      option.setPadding(dp(8), 0, dp(12), 0);
      option.setBackground(rounded(mode == app.permissionMode ? panel : bg, 16));
      IconView glyph = new IconView(this, permissionIcon(mode), permissionColor(mode));
      option.addView(glyph, new LinearLayout.LayoutParams(dp(44), dp(52)));
      TextView label = text(mode == PermissionMode.INHERIT ? "沿用当前对话" : mode.label, 15, fg);
      option.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1));
      label.setGravity(Gravity.CENTER_VERTICAL);
      if (mode == app.permissionMode) option.addView(new IconView(this, "check", fg), new LinearLayout.LayoutParams(dp(28), dp(28)));
      option.setContentDescription("权限选项：" + mode.label);
      clickable(option, () -> { app.choosePermission(mode); holder[0].dismiss(); });
      choices.addView(option);
    }
    TextView note = text("下一轮生效" + (app.effectivePermissionMode==PermissionMode.INHERIT ? "" : "\n当前远端："+app.effectivePermissionMode.label), 12, muted);
    note.setPadding(dp(16), dp(12), 0, 0);
    choices.addView(note);
    holder[0] = sheet("权限", choices);
  }

  private String detailText(ChatItem item) {
    if (item.type.equals("reasoning")) return item.detail.trim().isEmpty()
        ? (item.status.equals("inProgress") ? "正在等待思考摘要…" : "远端未提供此思考项的摘要。部分思考项可能没有可显示的摘要。") : item.detail;
    return item.text + (item.detail.isEmpty() ? "" : "\n\n" + item.detail);
  }

  private void showDetails(ChatItem m) {
    LinearLayout layout = column();
    layout.setPadding(dp(20), dp(12), dp(20), dp(20));
    TextView detail = text(detailText(m), 13, fg);
    detail.setTypeface(Typeface.MONOSPACE);
    detail.setTextIsSelectable(true);
    ScrollView s = new ScrollView(this);
    s.addView(detail);
    layout.addView(s, new LinearLayout.LayoutParams(-1, dp(380)));
    layout.addView(action("复制", () -> {
      ChatItem latest = currentItems.get(m.id);
      copy(detailText(latest == null ? m : latest));
    }));
    Dialog dialog = sheet(m.type.equals("reasoning") ? "思考过程" : "执行详情", layout);
    Runnable update = new Runnable() {
      public void run() {
        if (!dialog.isShowing()) return;
        ChatItem latest = currentItems.get(m.id);
        if (latest != null) {
          String value = detailText(latest);
          if (!value.contentEquals(detail.getText())) detail.setText(value);
        }
        app.main.postDelayed(this, 150);
      }
    };
    dialog.setOnDismissListener(d -> app.main.removeCallbacks(update));
    update.run();
  }

  private Dialog sheet(String heading, View content) {
    Dialog dialog = new Dialog(this);
    LinearLayout shell = column();
    shell.setBackground(rounded(bg, 24));
    shell.setPadding(dp(8), dp(8), dp(8), dp(20));
    LinearLayout top = row();
    TextView h = text(heading, 21, fg);
    h.setTypeface(null, Typeface.BOLD);
    h.setPadding(dp(16), dp(14), dp(8), dp(14));
    top.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        icon("close", "关闭", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48)));
    shell.addView(top);
    shell.addView(content, new LinearLayout.LayoutParams(-1, -2));
    dialog.setContentView(shell);
    Window w = dialog.getWindow();
    if (w != null) {
      w.setBackgroundDrawableResource(android.R.color.transparent);
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      w.setDimAmount(.24f);
      w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      w.setGravity(Gravity.BOTTOM);
      w.setWindowAnimations(R.style.SheetAnimation);
    }
    dialog.show();
    if (w != null) w.setLayout(wide ? dp(520) : -1, -2);
    return dialog;
  }

  private void showDrawer() {
    if (browser != null && browser.isShowing()) return;
    browser =
        new RemoteBrowser(
            this,
            app,
            isDark(),
            new RemoteBrowser.Actions() {
              public void connect(Profile p) {
                connectProfile(p);
              }

              public void version() { showRemoteVersion(); }

              public void add() {
                browser.dismiss();
                editProfile(new Profile(), true);
              }

              public void directories() {
                openDirectoryPicker();
              }

              public void diagnostics() {
                showConnectionError();
              }

              public void chat() {
                startNewChat();
              }

              public void settings() {
                new AlertDialog.Builder(MainActivity.this)
                    .setItems(
                        new String[] {"编辑当前连接", "外观"},
                        (d, w) -> {
                          if (w == 0) {
                            if (app.profile != null) editProfile(app.profile, false);
                            else editProfile(new Profile(), true);
                          } else
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("外观")
                                .setSingleChoiceItems(
                                    new String[] {"跟随系统", "浅色", "深色"},
                                    getPreferences(0).getInt("theme", 0),
                                    (a, b) -> {
                                      getPreferences(0).edit().putInt("theme", b).apply();
                                      a.dismiss();
                                      recreate();
                                    })
                                .setNegativeButton("取消", null)
                                .show();
                        })
                    .show();
              }
            });
    app.leaveIdleConversation();
    browser.show();
  }

  private void openDirectoryPicker() {
    if (!app.connected) {
      toast("请先连接 SSH 主机");
      return;
    }
    if (!app.canSwitchConversation()) {
      toast("正在恢复会话或上传附件，请稍候");
      return;
    }
    directoryPicker =
        new DirectoryPicker(
            this,
            app,
            isDark(),
            path -> {
              if (app.useProject(path) && browser != null && browser.isShowing())
                browser.openProject(path);
            });
    directoryPicker.show(app.profile.cwd);
  }

  private void startNewChat() {
    if (app.connected && !app.canSwitchConversation()) {
      toast("正在连接会话或上传附件，请稍候"); return;
    }
    if (!app.connected) {
      showDrawer();
      return;
    }
    if (app.profile.cwd.isEmpty()) {
      openDirectoryPicker();
      return;
    }
    app.draftText=input.getText().toString();
    app.newChat();
  }

  private void fillNavigation(LinearLayout nav, boolean modal) {
    if (nav == null) return;
    nav.removeAllViews();
    TextView label = text("连接", 12, muted);
    label.setPadding(dp(4), dp(8), 0, dp(12));
    nav.addView(label);
    for (Profile p : app.vault.profiles) {
      LinearLayout r = row();
      TextView t =
          text(
              (app.connected && app.profile.id.equals(p.id) ? "●  " : "▱  ")
                  + p.title()
                  + "\n"
                  + p.user
                  + "@"
                  + p.host,
              14,
              fg);
      t.setPadding(dp(8), dp(12), dp(8), dp(12));
      t.setMaxLines(2);
      clickable(
          t,
          () -> {
            if (modal && drawer != null) drawer.dismiss();
            if (app.profile == p && app.connected) connectionMenu();
            else connectProfile(p);
          });
      r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
      r.addView(
          icon("chevron", "编辑 " + p.title(), () -> editProfile(p, false)),
          new LinearLayout.LayoutParams(dp(44), dp(48)));
      nav.addView(r);
    }
    nav.addView(action("＋ 添加 SSH 连接", () -> editProfile(new Profile(), true)));
    TextView history = text("最近的对话", 12, muted);
    history.setPadding(dp(4), dp(28), 0, dp(12));
    nav.addView(history);
    if (!app.connected) {
      TextView note = text("连接电脑后查看历史对话", 13, muted);
      note.setPadding(dp(4), dp(4), 0, dp(16));
      nav.addView(note);
    } else if (app.threads.length() == 0) {
      nav.addView(text("还没有对话", 13, muted));
    }
    for (int i = 0; i < app.threads.length(); i++) {
      JSONObject t = app.threads.optJSONObject(i);
      if (t == null) continue;
      String id = t.optString("id");
      String name = t.isNull("name") ? "" : t.optString("name");
      if (name.trim().isEmpty()) name = t.optString("preview", "新对话");
      TextView item = text(name.trim().isEmpty() ? "新对话" : name, 14, fg);
      item.setPadding(dp(8), dp(14), dp(8), dp(14));
      item.setMaxLines(2);
      item.setEllipsize(TextUtils.TruncateAt.END);
      if (id.equals(app.threadId)) item.setBackground(rounded(line, 12));
      clickable(
          item,
          () -> {
            if (!app.canSwitchConversation()) {
              toast("正在恢复会话或上传附件，请稍候"); return;
            }
            app.draftText = input.getText().toString();
            if (modal && drawer != null) drawer.dismiss();
            app.viewConversation(id);
          });
      nav.addView(item);
    }
    if (app.connected) {
      nav.addView(
          action(
              app.nextCursor.isEmpty() ? "刷新对话" : "加载更多",
              () -> app.refreshThreads(!app.nextCursor.isEmpty())));
    }
    TextView theme =
        action(
            "外观",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("外观")
                    .setSingleChoiceItems(
                        new String[] {"跟随系统", "浅色", "深色"},
                        getPreferences(0).getInt("theme", 0),
                        (d, w) -> {
                          getPreferences(0).edit().putInt("theme", w).apply();
                          d.dismiss();
                          recreate();
                        })
                    .setNegativeButton("取消", null)
                    .show());
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
    tp.topMargin = dp(20);
    nav.addView(theme, tp);
  }

  private void chatMenu() {
    new AlertDialog.Builder(this).setTitle("对话")
        .setItems(new String[] {"新对话", "选择模型", "连接信息", "最近一次错误", "当前项目与对话", "剩余用量", "后台连接设置", "字体大小"}, (d, w) -> {
          if (w == 0) startNewChat();
          else if (w == 1) chooseModel();
          else if (w == 2) connectionMenu();
          else if (w == 3) showConnectionError();
          else if (w == 4) conversationInfo();
          else if (w == 5) usageInfo();
          else if (w == 6) backgroundSettings();
          else chooseFontSize();
        }).show();
  }

  private void chooseFontSize() {
    float[] scales = {0, .9f, 1f, 1.15f, 1.3f};
    String[] labels = {"跟随系统", "小", "标准", "大", "更大"};
    float current = getSharedPreferences("display", MODE_PRIVATE).getFloat("fontScale", 0);
    int selected = 0;
    for (int i = 0; i < scales.length; i++) if (Math.abs(scales[i] - current) < .001f) selected = i;
    new AlertDialog.Builder(this).setTitle("字体大小")
        .setSingleChoiceItems(labels, selected, (d, which) -> {
          getSharedPreferences("display", MODE_PRIVATE).edit().putFloat("fontScale", scales[which]).apply();
          d.dismiss(); recreate();
        }).setNegativeButton("取消", null).show();
  }

  private void backgroundSettings() {
    new AlertDialog.Builder(this).setTitle("后台连接")
        .setMessage("连接期间会显示常驻通知并保持 CPU 唤醒。请在系统设置中允许 RemoteGPT 后台运行，并将电池策略设为不受限制。若使用 Tailscale，也需允许其后台联网。断开连接后会释放唤醒锁。\n\n开启远端持久会话后，杀掉 App 或断网不会主动停止远端任务；重连会补回消息。等待审批时任务会暂停，直到你重新连接并处理。远端重启或程序崩溃仍会中断。Android 11 及以上可在通知设置中允许气泡；通知权限或气泡关闭时可点通知回到对话。")
        .setPositiveButton("打开系统设置", (d, w) -> {
          try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              android.net.Uri.parse("package:" + getPackageName()))); }
          catch (ActivityNotFoundException e) { toast("请在系统应用设置中打开 Remote"); }
        }).setNeutralButton("通知与流体云", (d, w) -> liveStatusSettings()).setNegativeButton("返回", null).show();
  }

  private void liveStatusSettings() {
    LinearLayout panelView = column();
    panelView.setPadding(dp(20), dp(8), dp(20), dp(8));
    CheckBox enabled = new CheckBox(this);
    enabled.setText("显示任务状态与审批流体云");
    enabled.setTextColor(fg);
    enabled.setChecked(getSharedPreferences("background", MODE_PRIVATE).getBoolean("liveStatus", true));
    panelView.addView(enabled);
    String support;
    if (Build.VERSION.SDK_INT >= 36) {
      boolean allowed = getSystemService(NotificationManager.class).canPostPromotedNotifications();
      support = allowed ? "系统已允许实时通知；最终展示由 ColorOS 决定。" : "请在系统通知设置中允许实时通知／流体云。";
    } else support = "此系统未提供 Android 16 实时通知接口，将显示普通任务通知。";
    TextView description = text(support + "\n\n运行中显示状态，待审批时可展开查看摘要并允许一次或拒绝。任务结束后显示已完成和最后回复；点“知道了”或从流体云进入对话后消失。收起状态只隐藏本轮流体云，不会停止远端任务。\n\n原有气泡继续保留，可在系统通知设置中单独管理。", 14, fg);
    description.setPadding(0, dp(12), 0, dp(12));
    panelView.addView(description);
    enabled.setOnCheckedChangeListener((button, checked) -> {
      getSharedPreferences("background", MODE_PRIVATE).edit().putBoolean("liveStatus", checked).apply();
      app.liveStatus.update();
    });
    new AlertDialog.Builder(this).setTitle("流体云与通知").setView(panelView)
        .setPositiveButton("打开系统通知设置", (d, w) -> {
          try { startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())); }
          catch (ActivityNotFoundException e) { toast("请在系统设置中打开 RemoteGPT 的通知设置"); }
        }).setNegativeButton("返回", null).show();
  }

  private void conversationInfo() {
    String project = app.currentProject();
    String details = "项目：" + (project.isEmpty() ? "远端默认目录（未返回路径）" : project)
        + "\n\n对话：" + (app.threadTitle.isEmpty() ? "新对话" : app.threadTitle)
        + "\n\n对话 ID：" + (app.threadId.isEmpty() ? "发送首条消息后创建" : app.threadId)
        + "\n\n状态：" + (!app.connected ? "未连接" : app.readOnly ? "只读，可尝试接续" : app.busy ? "正在处理" : "可继续对话");
    new AlertDialog.Builder(this).setTitle("当前项目与对话").setMessage(details)
        .setPositiveButton("关闭", null)
        .setNeutralButton("复制", (d, w) -> {
          android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          clipboard.setPrimaryClip(android.content.ClipData.newPlainText("当前项目与对话", details));
          toast("已复制");
        }).show();
  }

  private void usageInfo() {
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle("剩余用量")
        .setMessage(app.usage.details()).setPositiveButton("关闭", null)
        .setNeutralButton("刷新", null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> app.refreshUsage()));
    Runnable update = new Runnable() {
      public void run() {
        if (!dialog.isShowing()) return;
        dialog.setMessage(app.usage.loading ? "正在读取剩余用量…" : app.usage.details());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(app.connected && !app.usage.loading);
        app.main.postDelayed(this, 300);
      }
    };
    dialog.setOnDismissListener(d -> app.main.removeCallbacks(update));
    dialog.show();
    app.refreshUsage();
    update.run();
  }

  private void connectionMenu() {
    if (!app.connected && !app.connecting) {
      showDrawer();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(app.profile.title())
        .setMessage(
            app.profile.user + "@" + app.profile.host + ":" + app.profile.port + "\n" + app.state)
        .setPositiveButton("返回", null)
        .setNeutralButton("编辑连接", (d, w) -> editProfile(app.profile, false))
        .setNegativeButton("断开连接", (d, w) -> app.disconnect())
        .show();
  }

  private void connectProfile(Profile p) {
    if ((app.connected || app.connecting) && app.profile != null && app.profile.id.equals(p.id)) return;
    beginConnect(p);
  }

  private void beginConnect(Profile p) {
    if (!p.tailscaleSsh && p.password.isEmpty() && p.privateKey.isEmpty()) {
      editProfile(p, false);
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 11);
    app.connect(p);
  }

  private EditText field(
      LinearLayout layout, String label, String value, String hint, boolean secret) {
    TextView l = text(label, 12, muted);
    l.setPadding(dp(4), dp(14), 0, dp(6));
    layout.addView(l);
    EditText e = new EditText(this);
    e.setTextSize(15);
    e.setTextColor(fg);
    e.setHintTextColor(muted);
    e.setText(value);
    e.setHint(hint);
    e.setSingleLine(true);
    e.setPadding(dp(14), dp(10), dp(14), dp(10));
    e.setBackground(rounded(panel, 12));
    e.setContentDescription(label);
    e.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | (secret
                ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                : android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
    e.setMinHeight(dp(48));
    layout.addView(e, new LinearLayout.LayoutParams(-1, -2));
    return e;
  }

  private void editProfile(Profile original, boolean fresh) {
    if (editorDialog != null && editorDialog.isShowing()) return;
    Profile draft = Profile.from(original.json());
    draft.password = original.password;
    draft.privateKey = original.privateKey;
    draft.passphrase = original.passphrase;
    LinearLayout form = column();
    form.setPadding(dp(16), 0, dp(16), dp(16));
    ScrollView sc = new ScrollView(this);
    sc.addView(form);
    EditText name = field(form, "连接名称", draft.name, "我的电脑", false),
        host = field(form, "主机", draft.host, "192.168.1.10", false),
        port = field(form, "端口", String.valueOf(draft.port), "22", false);
    port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    EditText user = field(form, "用户名", draft.user, "用户名", false);
    RadioGroup auth = new RadioGroup(this);
    auth.setOrientation(LinearLayout.VERTICAL);
    auth.setPadding(0, dp(12), 0, dp(4));
    RadioButton passwordAuth = new RadioButton(this);
    passwordAuth.setId(R.id.auth_password);
    passwordAuth.setText("密码登录");
    passwordAuth.setTextColor(fg);
    RadioButton keyAuth = new RadioButton(this);
    keyAuth.setId(R.id.auth_key);
    keyAuth.setText("私钥登录");
    keyAuth.setTextColor(fg);
    auth.addView(passwordAuth);
    auth.addView(keyAuth);
    RadioButton tailscaleAuth = new RadioButton(this);
    tailscaleAuth.setId(R.id.auth_tailscale);
    tailscaleAuth.setText("Tailscale SSH（免密码）");
    tailscaleAuth.setTextColor(fg);
    auth.addView(tailscaleAuth);
    form.addView(auth);
    TextView networkHelp = text("Tailscale：先在手机 Tailscale App 中连接同一网络，主机填 100.x 地址或完整 MagicDNS 名称。普通 SSH 仍选密码/私钥；仅启用 Tailscale SSH 的主机选免密码。", 13, muted);
    form.addView(networkHelp);
    LinearLayout passwordSection = column();
    form.addView(passwordSection);
    EditText password = field(passwordSection, "密码", draft.password, "SSH 登录密码", true);
    LinearLayout keySection = column();
    form.addView(keySection);
    EditText key = field(keySection, "私钥", draft.privateKey, "粘贴 PEM / OpenSSH 私钥", true);
    key.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setMinLines(3);
    key.setMaxLines(5);
    keyEditor = key;
    keySection.addView(
        action(
            "从文件导入私钥",
            () -> {
              Intent i =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("*/*")
                      .addCategory(Intent.CATEGORY_OPENABLE);
              startActivityForResult(i, 42);
            }));
    EditText passphrase = field(keySection, "私钥口令", draft.passphrase, "可选", true);
    auth.setOnCheckedChangeListener(
        (group, checked) -> {
          passwordSection.setVisibility(checked == R.id.auth_password ? View.VISIBLE : View.GONE);
          keySection.setVisibility(checked == R.id.auth_key ? View.VISIBLE : View.GONE);
        });
    auth.check(draft.tailscaleSsh ? R.id.auth_tailscale : draft.privateKey.isEmpty() ? R.id.auth_password : R.id.auth_key);
    TextView advanced = action("项目与启动设置 ⌄", () -> {});
    LinearLayout.LayoutParams advancedLayout = new LinearLayout.LayoutParams(-1, -2);
    advancedLayout.topMargin = dp(12);
    form.addView(advanced, advancedLayout);
    LinearLayout extra = column();
    extra.setVisibility(draft.cwd.isEmpty() ? View.GONE : View.VISIBLE);
    form.addView(extra);
    clickable(
        advanced,
        () ->
            extra.setVisibility(extra.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    EditText cwd = field(extra, "远端项目目录", draft.cwd, "/home/me/project（可选）", false),
        command = field(extra, "远端启动命令", draft.command, "codex app-server", false);
    CheckBox persistent = new CheckBox(this);
    persistent.setText("远端持久会话（需 Python 3，断线后继续运行）");
    persistent.setTextColor(fg);
    persistent.setChecked(draft.persistent);
    extra.addView(persistent);
    TextView note = text("持久模式会在远端 ~/.remote-gpt 下安装会话托管程序。启动命令需提供 Codex App Server 兼容的标准输入 / 输出协议。远端需预先完成安装和登录。", 12, muted);
    note.setPadding(dp(4), dp(10), dp(4), dp(10));
    extra.addView(note);
    CheckBox remember = new CheckBox(this);
    remember.setText("在此设备加密保存凭据");
    remember.setTextColor(fg);
    remember.setTextSize(13);
    remember.setChecked(draft.remember);
    form.addView(remember);
    TextView validation = text("", 13, Color.rgb(181, 63, 49));
    LinearLayout footer = column();
    footer.addView(validation);
    Dialog dialog = profileDialog(fresh ? "添加 SSH 连接" : "编辑 SSH 连接", sc, footer);
    java.util.function.Consumer<Boolean> persist =
        shouldConnect -> {
          try {
            draft.name = name.getText().toString().trim();
            draft.host = host.getText().toString().trim();
            draft.port = Integer.parseInt(port.getText().toString());
            draft.user = user.getText().toString().trim();
            boolean useKey = auth.getCheckedRadioButtonId() == R.id.auth_key;
            draft.tailscaleSsh = auth.getCheckedRadioButtonId() == R.id.auth_tailscale;
            draft.password = useKey || draft.tailscaleSsh ? "" : password.getText().toString();
            draft.privateKey = useKey ? key.getText().toString() : "";
            draft.passphrase = useKey ? passphrase.getText().toString() : "";
            draft.cwd = cwd.getText().toString().trim();
            draft.command = command.getText().toString().trim();
            draft.persistent = persistent.isChecked();
            draft.remember = remember.isChecked();
            draft.validate();
            if (shouldConnect && !draft.tailscaleSsh
                && (useKey ? draft.privateKey.trim().isEmpty() : draft.password.isEmpty()))
              throw new IllegalArgumentException(useKey ? "请先导入或粘贴私钥" : "请输入 SSH 登录密码");
            if (!app.storeProfile(draft)) {
              validation.setText(app.error);
              return;
            }
            dialog.dismiss();
            if (drawer != null) drawer.dismiss();
            if (shouldConnect) connectProfile(draft);
            else {
              app.changed();
              toast("连接已保存");
            }
          } catch (Exception ex) {
            validation.setText(ex instanceof NumberFormatException ? "请输入有效端口" : ex.getMessage());
          }
        };
    LinearLayout buttons = row();
    footer.addView(buttons);
    TextView save = action("保存", () -> persist.accept(false));
    buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
    TextView connect = action("保存并连接", () -> persist.accept(true));
    connect.setTextColor(bg);
    connect.setBackground(rounded(fg, 24));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
    cp.leftMargin = dp(12);
    buttons.addView(connect, cp);
    if (!fresh) {
      form.addView(
          action(
              "查看已信任的主机指纹",
              () -> {
                String target = original.host + ":" + original.port;
                String pin = app.vault.getPin(target);
                new AlertDialog.Builder(this)
                    .setTitle("主机指纹")
                    .setMessage(target + "\n\n" + (pin == null ? "尚未信任此主机" : pin))
                    .setPositiveButton("关闭", null)
                    .setNeutralButton(
                        pin == null ? null : "重置主机信任",
                        (d, w) ->
                            new AlertDialog.Builder(this)
                                .setTitle("重置此主机的信任？")
                                .setMessage("仅在确认服务器更换了 SSH 主机密钥时重置。下次连接必须重新核对指纹。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton(
                                    "重置",
                                    (confirm, which) -> {
                                      if (app.profile != null
                                          && (app.profile.host + ":" + app.profile.port)
                                              .equals(target)) app.disconnect();
                                      synchronized (app.vault) {
                                        app.vault.pins.remove(target);
                                        app.save();
                                      }
                                      toast("下次连接将重新核对指纹");
                                    })
                                .show())
                    .show();
              }));
      form.addView(
          action(
              "移除此连接",
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("移除连接？")
                      .setMessage("仅删除此设备保存的连接和凭据，远端文件不受影响。")
                      .setNegativeButton("取消", null)
                      .setPositiveButton(
                          "移除",
                          (d, w) -> {
                            if (app.profile != null && app.profile.id.equals(original.id))
                              app.disconnect();
                            app.vault.profiles.remove(original);
                            app.save();
                            dialog.dismiss();
                            render();
                          })
                      .show()));
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == ImagePicker.REQUEST) {
      boolean sameContext = pendingImageGeneration >= 0 && pendingImageGeneration == app.connectionGeneration()
          && app.profile != null && pendingImageHost.equals(app.profile.id) && pendingImageThread.equals(app.threadId)
          && pendingImageProject.equals(imageProject());
      pendingImageGeneration = -1;
      if (result == RESULT_OK && data != null) {
        List<android.net.Uri> selected = ImagePicker.selected(data);
        if (!selected.isEmpty()) {
          if (!sameContext) toast("连接或对话已改变，请在当前对话重新选择图片");
          else app.attachImages(selected);
        }
      }
      return;
    }
    if (request == 44) {
      String source = pendingDownload, host = pendingDownloadHost;
      pendingDownload = "";
      if (result == RESULT_OK && data != null && data.getData() != null && !source.isEmpty()) {
        android.net.Uri destination = data.getData();
        toast("正在保存文件…");
        mediaWorker.execute(() -> {
          try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
            if (out == null) throw new IOException("无法写入所选位置");
            if (app.profile == null || !host.equals(app.profile.id)) throw new IOException("连接已切换，请重新下载");
            app.readMedia(source, out, MediaFiles.DOWNLOAD_LIMIT);
            app.main.post(() -> toast("文件已保存"));
          } catch (Exception e) {
            try { android.provider.DocumentsContract.deleteDocument(getContentResolver(), destination); } catch (Exception ignored) { }
            app.main.post(() -> toast("保存失败：" + ConnectionFailure.reason(e)));
          }
        });
      }
    }
    if (request == 42 && result == RESULT_OK && data != null && keyEditor != null) {
      try (InputStream in = getContentResolver().openInputStream(data.getData())) {
        if (in == null) throw new IOException("无法读取文件");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
          if (bytes.size() + n > 65536) throw new IOException("私钥文件不能超过 64 KiB");
          bytes.write(buf, 0, n);
        }
        keyEditor.setText(bytes.toString(StandardCharsets.UTF_8.name()));
      } catch (Exception e) {
        toast(e.getMessage());
      }
    }
  }

  private void chooseModel() {
    if (!app.connected) { toast("连接后可选择远端模型"); return; }
    final String[] selected = {app.model, app.reasoningEffort, app.serviceTier};
    final Profile modelProfile = app.profile;
    final String modelThread = app.threadId;
    LinearLayout panelView = column();
    panelView.setPadding(dp(20), 0, dp(20), dp(8));
    TextView subtitle = text("模型、推理强度与运行速度 · 下一轮生效", 12, muted);
    subtitle.setPadding(dp(4), 0, 0, dp(16));
    panelView.addView(subtitle);
    TextView catalogStatus = text("", 12, muted);
    panelView.addView(catalogStatus);
    TextView refresh = action("刷新模型", () -> { app.refreshModels(); app.refreshModelSettings(); });
    panelView.addView(refresh);
    ScrollView modelScroll = new ModelListScrollView(this);
    int modelListBackground = bg == Color.BLACK ? 0xff161616 : 0xfff8f8f8;
    modelScroll.setContentDescription("可滚动的模型列表");
    modelScroll.setBackground(rounded(modelListBackground, 16));
    modelScroll.setClipToOutline(true);
    modelScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
    modelScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
    LinearLayout modelList = column(); modelScroll.addView(modelList);
    int modelCount = app.models.length() + 1;
    LinearLayout.LayoutParams modelScrollParams = new LinearLayout.LayoutParams(-1, dp(Math.min(240, modelCount * 52 + 8)));
    modelScrollParams.topMargin = dp(8);
    panelView.addView(modelScroll, modelScrollParams);
    LinearLayout effortHeader = row();
    effortHeader.setPadding(dp(4), dp(20), dp(4), dp(4));
    TextView effortTitle = text("推理强度", 13, muted);
    effortHeader.addView(effortTitle, new LinearLayout.LayoutParams(0, -2, 1));
    TextView resetEffort = text("默认", 12, muted);
    resetEffort.setPadding(dp(12), dp(8), dp(4), dp(8));
    resetEffort.setContentDescription("恢复默认推理强度");
    effortHeader.addView(resetEffort);
    panelView.addView(effortHeader);
    LinearLayout effortPanel = column();
    TextView effortValue = text("默认", 22, fg);
    effortValue.setTypeface(null, Typeface.BOLD);
    effortValue.setPadding(dp(4), dp(4), 0, dp(4));
    effortPanel.addView(effortValue);
    SeekBar effortSlider = new SeekBar(this);
    effortSlider.setContentDescription("推理强度滑条");
    effortSlider.setPadding(dp(12), 0, dp(12), 0);
    effortSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(fg));
    effortSlider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(line));
    effortSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(fg));
    GradientDrawable tick = new GradientDrawable();
    tick.setShape(GradientDrawable.OVAL); tick.setColor(muted); tick.setSize(dp(3), dp(3));
    effortSlider.setTickMark(tick);
    effortSlider.setKeyProgressIncrement(1);
    effortPanel.addView(effortSlider, new LinearLayout.LayoutParams(-1, dp(48)));
    LinearLayout endpoints = row(); endpoints.setPadding(dp(6), 0, dp(6), 0);
    TextView low = text("", 12, muted), high = text("", 12, muted);
    endpoints.addView(low, new LinearLayout.LayoutParams(0, -2, 1));
    high.setGravity(Gravity.END); endpoints.addView(high, new LinearLayout.LayoutParams(0, -2, 1));
    effortPanel.addView(endpoints);
    panelView.addView(effortPanel);
    TextView effortHint = text("", 12, muted);
    effortHint.setPadding(dp(4), dp(12), 0, dp(16)); panelView.addView(effortHint);
    TextView speedTitle = text("运行速度", 13, muted);
    speedTitle.setPadding(dp(4), dp(4), 0, dp(8)); panelView.addView(speedTitle);
    LinearLayout speedChoices = row(); panelView.addView(speedChoices);
    TextView speedHint = text("", 12, muted);
    speedHint.setPadding(dp(4), dp(10), dp(4), dp(12)); panelView.addView(speedHint);
    Dialog[] holder = new Dialog[1];
    Runnable[] redraw = new Runnable[1];
    redraw[0] = () -> {
      catalogStatus.setText(app.modelsStatus.isEmpty() ? "模型由远端提供；未列出新模型时请检查远端 Codex 版本和账号" : app.modelsStatus);
      refresh.setEnabled(!app.modelsLoading && app.connected);
      modelScroll.getLayoutParams().height = dp(Math.min(240, (app.models.length() + 1) * 52 + 8));
      modelScroll.requestLayout();
      modelList.removeAllViews();
      JSONObject chosen = null;
      for (int i = -1; i < app.models.length(); i++) {
        JSONObject model = i < 0 ? null : app.models.optJSONObject(i);
        if (i >= 0 && model == null) continue;
        String id = model == null ? "" : ModelOptions.id(model);
        if (selected[0].equals(id) || (selected[0].isEmpty() && model != null && model.optBoolean("isDefault"))) chosen = model;
        boolean checked = selected[0].equals(id);
        LinearLayout option = row(); option.setPadding(dp(14), 0, dp(10), 0);
        option.setBackground(rounded(checked ? panel : modelListBackground, 14));
        TextView label = text(model == null ? "远端默认" : ModelOptions.displayName(app.models, id), 15, fg);
        label.setMaxLines(1); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER_VERTICAL);
        option.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1));
        if (checked) option.addView(new IconView(this, "check", fg), new LinearLayout.LayoutParams(dp(26), dp(26)));
        option.setContentDescription("选择模型：" + id);
        clickable(option, () -> {
          selected[0] = id; selected[1] = ""; redraw[0].run();
          effortPanel.setAlpha(.3f); effortPanel.setTranslationY(dp(6));
          effortPanel.animate().alpha(1).translationY(0).setDuration(180).start();
        });
        modelList.addView(option);
      }
      JSONObject resolved = app.selectedModel(selected[0]);
      if (resolved != null) chosen = resolved;
      speedChoices.removeAllViews();
      String supported = ServiceTierOptions.supportedId(resolved, selected[2]);
      boolean revoked = !selected[2].isEmpty() && (!app.supportsServiceTier()
          || !selected[2].equals(ServiceTierOptions.STANDARD) && (!app.speedSelectionEnabled() || supported.isEmpty()));
      if (revoked) selected[2] = "";
      else if (!selected[2].isEmpty()) selected[2] = supported;
      List<JSONObject> speeds = new ArrayList<>();
      speeds.add(Json.obj("id", "", "name", "远端默认"));
      if (app.supportsServiceTier()) speeds.add(Json.obj("id", ServiceTierOptions.STANDARD, "name", "普通"));
      if (app.speedSelectionEnabled()) speeds.addAll(ServiceTierOptions.tiers(resolved));
      // Each option stays readable when a future server offers more than Fast.
      LinearLayout speedRows = column();
      speedChoices.addView(speedRows, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout speedRow = null;
      for (int i = 0; i < speeds.size(); i++) {
        if (i % 3 == 0) { speedRow = row(); speedRows.addView(speedRow); }
        String tier = speeds.get(i).optString("id");
        TextView option = text(ServiceTierOptions.label(tier, resolved), 13, fg);
        option.setGravity(Gravity.CENTER); option.setMinHeight(dp(44)); option.setPadding(dp(6), dp(6), dp(6), dp(6));
        option.setBackground(rounded(selected[2].equals(tier) ? panel : bg, 12));
        option.setTypeface(null, selected[2].equals(tier) ? Typeface.BOLD : Typeface.NORMAL);
        option.setContentDescription("选择运行速度：" + (tier.isEmpty() ? "远端默认" : tier));
        option.setSelected(selected[2].equals(tier));
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(0, -2, 1);
        speedParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        speedRow.addView(option, speedParams);
        clickable(option, () -> { selected[2] = tier; redraw[0].run(); });
      }
      String speedDetail = "";
      if (selected[2].isEmpty()) speedDetail = app.inheritedTierKnown || app.modelSettings.configKnown
          ? "沿用远端默认 · " + ServiceTierOptions.label(app.defaultServiceTier(resolved), resolved) : "沿用远端默认，不覆盖远端配置";
      else if (selected[2].equals(ServiceTierOptions.STANDARD)) speedDetail = "普通速度 · 下一轮生效";
      else for (JSONObject tier : ServiceTierOptions.tiers(resolved))
        if (ServiceTierOptions.same(tier.optString("id"), selected[2]) && !tier.isNull("description")) speedDetail = tier.optString("description");
      if (!selected[2].isEmpty() && !selected[2].equals(ServiceTierOptions.STANDARD) && speedDetail.isEmpty())
        speedDetail = "更快响应，用量可能增加 · 下一轮生效";
      if (!app.supportsServiceTier()) speedDetail = "远端未提供速度选项，可沿用远端默认";
      else if (!app.speedSelectionEnabled()) speedDetail += "\n远端已关闭速度档位选择";
      else if (resolved == null) speedDetail += app.modelSettingsLoading ? "\n正在确认远端默认模型…" : "\n请选择具体模型或刷新以确认速度选项";
      else if (ServiceTierOptions.tiers(resolved).isEmpty()) speedDetail += "\n此模型未提供快速档位";
      if (revoked) speedDetail = "所选速度已不可用，恢复远端默认\n" + speedDetail;
      speedHint.setText(speedDetail);
      List<String> efforts = ModelOptions.efforts(chosen);
      effortPanel.setVisibility(efforts.isEmpty() ? View.GONE : View.VISIBLE);
      String defaultEffort = chosen == null ? "" : chosen.optString("defaultReasoningEffort");
      int defaultIndex = Math.max(0, efforts.indexOf(defaultEffort));
      int index = efforts.indexOf(selected[1]);
      if (index < 0) index = defaultIndex;
      effortSlider.setOnSeekBarChangeListener(null);
      effortSlider.setMax(Math.max(1, efforts.size() - 1));
      effortSlider.setEnabled(efforts.size() > 1);
      effortSlider.setProgress(efforts.isEmpty() ? 0 : index);
      if (!selected[1].isEmpty() && !efforts.contains(selected[1])) selected[1] = "";
      effortValue.setText(efforts.isEmpty() ? "默认" : ModelOptions.label(efforts.get(index)));
      if (Build.VERSION.SDK_INT >= 30) effortSlider.setStateDescription(effortValue.getText());
      low.setText(efforts.isEmpty() ? "" : ModelOptions.label(efforts.get(0)));
      high.setText(efforts.size() < 2 ? "" : ModelOptions.label(efforts.get(efforts.size() - 1)));
      effortHint.setText(efforts.isEmpty() ? "此模型未提供可选强度" : "默认 · " + ModelOptions.label(efforts.get(defaultIndex)));
      resetEffort.setEnabled(!efforts.isEmpty());
      resetEffort.setAlpha(efforts.isEmpty() ? .4f : 1);
      clickable(resetEffort, () -> {
        effortSlider.setProgress(defaultIndex); selected[1] = "";
      });
      effortSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
          if (efforts.isEmpty()) return;
          selected[1] = efforts.get(Math.min(progress, efforts.size() - 1));
          effortValue.setText(ModelOptions.label(selected[1]));
          if (Build.VERSION.SDK_INT >= 30) bar.setStateDescription(effortValue.getText());
          if (fromUser) bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        public void onStartTrackingTouch(SeekBar bar) {
          effortValue.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
        }
        public void onStopTrackingTouch(SeekBar bar) {
          effortValue.animate().scaleX(1).scaleY(1).setDuration(120).start();
        }
      });
    };
    redraw[0].run();
    TextView apply = action("完成", () -> {
      if (!app.connected || app.profile != modelProfile || !app.threadId.equals(modelThread)) {
        holder[0].dismiss(); return;
      }
      app.model = selected[0]; app.reasoningEffort = selected[1];
      app.chooseServiceTier(selected[2]); holder[0].dismiss();
    });
    apply.setTextColor(bg); apply.setBackground(rounded(fg, 22));
    ScrollView pickerScroll = new ScrollView(this); pickerScroll.setFillViewport(false); pickerScroll.addView(panelView);
    LinearLayout pickerContainer = column();
    int available = Math.max(dp(180), getResources().getDisplayMetrics().heightPixels - dp(232));
    panelView.measure(View.MeasureSpec.makeMeasureSpec(wide ? dp(500) : getResources().getDisplayMetrics().widthPixels - dp(16), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    pickerContainer.addView(pickerScroll, new LinearLayout.LayoutParams(-1, Math.min(available, panelView.getMeasuredHeight())));
    LinearLayout pickerFooter = column(); pickerFooter.setPadding(dp(20), dp(10), dp(20), dp(8));
    pickerFooter.addView(apply); pickerContainer.addView(pickerFooter);
    holder[0] = sheet("模型", pickerContainer);
    final JSONArray[] rendered = {app.models};
    final ModelSettings[] renderedSettings = {app.modelSettings};
    final boolean[] settingsLoading = {app.modelSettingsLoading};
    Runnable updateModels = () -> {
      if (!app.connected || app.profile != modelProfile || !app.threadId.equals(modelThread)) {
        holder[0].dismiss(); return;
      }
      catalogStatus.setText(app.modelsStatus);
      refresh.setEnabled(!app.modelsLoading);
      if (rendered[0] != app.models || renderedSettings[0] != app.modelSettings || settingsLoading[0] != app.modelSettingsLoading) {
        rendered[0] = app.models;
        renderedSettings[0] = app.modelSettings; settingsLoading[0] = app.modelSettingsLoading;
        redraw[0].run();
      }
    };
    app.stateListeners.add(updateModels);
    holder[0].setOnDismissListener(d -> app.stateListeners.remove(updateModels));
    app.refreshModels();
    app.refreshModelSettings();
  }

  private void showRemoteVersion() {
    if (!app.connected || versionDialog != null) return;
    RemoteVersion version = app.remoteVersion;
    shownVersion = version;
    String target = version.latest;
    String detail = "当前会话版本：" + (version.running.isEmpty() ? "未知" : version.running)
        + "\n已安装版本：" + (version.installed.isEmpty() ? "未知" : version.installed)
        + "\n最新稳定版：" + (target.isEmpty() ? "尚未获取" : target);
    if (!version.error.isEmpty()) detail += "\n\n" + version.error;
    if (!version.reason.isEmpty()) detail += "\n\n" + version.reason;
    AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("远端 Codex 版本");
    if (version.canUpdate()) {
      detail += "\n\n是否更新远端 Codex？空闲且支持安全重启时自动启用新版；有任务运行时先安装，任务结束后再点此处重启。选择暂不更新可继续使用当前版本。";
      builder.setPositiveButton("更新至 " + target, (d, w) -> app.updateRemoteVersion(version, target));
      builder.setNegativeButton("暂不更新", null);
    } else if (version.updating) {
      detail += "\n\n正在更新，请保持连接。";
      builder.setPositiveButton("知道了", null);
    } else if (version.needsRestart()) {
      detail += "\n\n新版已安装，当前会话仍运行旧版。你可以自行决定何时重启。重启会停止所有正在进行的远端对话，然后短暂断开并使用新版重新连接。";
      builder.setPositiveButton("重启并启用新版", (d, w) -> confirmRemoteRestart(version));
      builder.setNegativeButton("稍后", null);
    } else builder.setPositiveButton("关闭", null);
    if (!version.checking && !version.updating)
      builder.setNeutralButton("重新检查", (d, w) -> app.checkRemoteVersion(true));
    builder.setMessage(detail);
    versionDialog = builder.create();
    versionDialog.setOnDismissListener(d -> { versionDialog = null; shownVersion = null; });
    versionDialog.show();
  }

  private void confirmRemoteRestart(RemoteVersion version) {
    if (!app.connected || version != app.remoteVersion || !version.needsRestart()) return;
    new AlertDialog.Builder(this)
        .setTitle("停止任务并重启？")
        .setMessage("重启将立即停止这台远端电脑上由 RemoteGPT 托管的所有进行中对话和待处理审批。未完成的回复会中断。\n\n重启后 App 会自动重新连接，并使用已安装的 Codex "
            + version.installed + "。")
        .setNegativeButton("取消", null)
        .setPositiveButton("停止任务并重启", (d, w) -> app.restartRemoteVersion(version))
        .show();
  }

  private void showPending() {
    if (versionDialog != null && (!app.connected || shownVersion != app.remoteVersion)) versionDialog.dismiss();
    if (app.connected && app.remoteVersion.prompt && versionDialog == null
        && trustDialog == null && approvalDialog == null && app.visibleApproval() == null) {
      app.remoteVersion.prompt = false;
      showRemoteVersion();
    }
    if (!app.trustHost.isEmpty() && (trustDialog == null || !trustDialog.isShowing())) {
      String fingerprint = app.trustFingerprint;
      trustDialog =
          new AlertDialog.Builder(this)
              .setTitle("首次连接此主机")
              .setMessage(
                  app.trustHost
                      + "\n\n主机指纹\n"
                      + fingerprint
                      + "\n\n请与服务器上的 SSH 主机指纹核对。信任后会记住此主机，密钥变化时拒绝连接。")
              .setNegativeButton("取消", (d, w) -> app.trust(false))
              .setPositiveButton("信任并连接", (d, w) -> app.trust(true))
              .setOnCancelListener(d -> app.trust(false))
              .show();
    }
    if (app.trustHost.isEmpty() && trustDialog != null) {
      trustDialog.dismiss();
      trustDialog = null;
    }
    JSONObject req = app.visibleApproval();
    String id = req == null ? "" : app.notificationApprovalToken(req);
    if (!id.equals(shownApproval) && approvalDialog != null) {
      approvalDialog.dismiss();
      approvalDialog = null;
      shownApproval = "";
      questionForm = null;
    }
    if (req == null || approvalDialog != null || app.isAnswering(req)) return;
    shownApproval = id;
    JSONObject p = Json.object(req, "params");
    if (ConversationJournal.question(req)) {
      showQuestions(req, p);
      return;
    }
    String description =
        p.optString("reason", "远端请求执行以下操作")
            + "\n\n"
            + p.optString("command", p.optString("grantRoot", "文件修改"))
            + "\n"
            + p.optString("cwd")
            + "\n会话："
            + p.optString("threadId");
    if (p.has("networkApprovalContext")) description += "\n网络访问：" + p.opt("networkApprovalContext");
    for (ChatItem item : app.items)
      if (item.id.equals(p.optString("itemId")) && !item.detail.isEmpty())
        description += "\n\n" + item.detail;
    TextView details = text(description, 14, fg);
    details.setPadding(dp(20), dp(12), dp(20), dp(12));
    details.setTextIsSelectable(true);
    ScrollView sc = new ScrollView(this);
    sc.addView(details);
    approvalDialog =
        new AlertDialog.Builder(this)
            .setTitle("需要你的确认")
            .setView(sc)
            .setCancelable(false)
            .setNegativeButton(
                "拒绝",
                (d, w) -> {
                  app.answer(req, Json.obj("decision", "decline"));
                  approvalDialog = null;
                  shownApproval = "";
                })
            .setPositiveButton(
                "允许一次",
                (d, w) -> {
                  app.answer(req, Json.obj("decision", "accept"));
                  approvalDialog = null;
                  shownApproval = "";
                })
            .show();
  }

  private void showQuestions(JSONObject req, JSONObject p) {
    QuestionForm form = new QuestionForm(this,p,fg,muted,panel);
    questionForm=form;
    if (shownApproval.equals(restoredQuestionToken)) form.restoreAnswers(restoredQuestion);
    restoredQuestion=null;restoredQuestionToken="";
    ScrollView sc = new ScrollView(this);
    sc.addView(form);
    approvalDialog =
        new AlertDialog.Builder(this)
            .setTitle("补充信息")
            .setView(sc)
            .setCancelable(false)
            .setNegativeButton(
                ConversationJournal.savedQuestion(req) ? "不回答" : "停止任务",
                (d, w) -> {
                  app.cancelQuestion(req);
                  approvalDialog = null;
                  shownApproval = "";
                })
            .setPositiveButton("发送", null)
            .show();
    AlertDialog dialog=(AlertDialog) approvalDialog;
    android.widget.Button sendAnswer=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    form.onChanged(()->sendAnswer.setEnabled(form.ready()));
    sendAnswer.setOnClickListener(v->{
      if(!form.ready() || app.isAnswering(req))return;
      app.answer(req,form.result());
      if(app.isAnswering(req)) {
        restoredQuestion=form.saveAnswers();restoredQuestionToken=shownApproval;
        dialog.dismiss();approvalDialog=null;shownApproval="";questionForm=null;
      }
    });
  }
}
