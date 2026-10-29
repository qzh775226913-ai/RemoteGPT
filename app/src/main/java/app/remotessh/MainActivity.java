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
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private RemoteApp app;
  private int bg, fg, muted, panel, line;
  private LinearLayout root, body, conversation, messages, empty, sidebar, composer;
  private ConversationScrollView scroll;
  private String renderedThread = "";
  private TextView status, title, error, busyLabel, modelButton, projectLabel, usageLabel;
  private EditText input;
  private DialogUi ui;
  private ProfileEditor profileEditor;
  private ModelPicker modelPicker;
  private PermissionPicker permissionPicker;
  private IconView send, permissionButton;
  private ProgressBar runningIndicator;
  private LinearLayout attachmentStrip, queuedBar;
  private HorizontalScrollView attachmentScroll;
  private final Map<String, LinearLayout> mediaRows = new HashMap<>();
  private final Map<String, List<String>> mediaSources = new HashMap<>();
  private static final java.util.concurrent.ExecutorService mediaWorker = java.util.concurrent.Executors.newFixedThreadPool(2);
  private static final ThumbnailLoader thumbnailWorker = new ThumbnailLoader(2);
  private String pendingDownload = "", pendingDownloadHost = "";
  private int pendingImageGeneration = -1;
  private String pendingImageHost = "", pendingImageThread = "", pendingImageProject = "";

  private View jump;
  private final Map<String, TextView> texts = new HashMap<>();
  private final Map<String, MessageBodyView> messageBodies = new HashMap<>();
  private final Map<String, ChatItem> rendered = new HashMap<>();
  private final Map<String, QuestionHistoryView> questionCards = new HashMap<>();
  private TextView historyButton;
  private int historyLimit=60;
  private boolean historyRender;
  private boolean mediaScheduled;
  private final List<MessageImageTile> mediaTiles=new ArrayList<>();
  private final Runnable refreshMedia=()->{mediaScheduled=false;updateMediaViewport();};
  private final Map<String, ChatItem> currentItems = new HashMap<>();
  private final List<String> order = new ArrayList<>();
  private Dialog trustDialog, approvalDialog, drawer;
  private Dialog versionDialog;
  private RemoteVersion shownVersion;
  private RemoteBrowser browser;
  private ImageDialog imageDialog;
  private DirectoryPicker directoryPicker;
  private LinearLayout readOnlyBar;
  private TextView readOnlyLabel,resumeButton,reconnectButton,refreshButton,newChatButton;
  private LoadingSpinner resumeSpinner,reconnectSpinner;
  private boolean visibleActivity;
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
                (app.state.startsWith("正在停止") ? UiText.get(R.string.ui_stopping) : UiText.get(R.string.ui_processing))
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
      config.fontScale *= Math.min(scale, 1.15f);
      base = base.createConfigurationContext(config);
    }
    super.attachBaseContext(AppLanguage.wrap(base));
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
    bg = Color.parseColor(dark ? "#000000" : "#F5F6F7");
    fg = Color.parseColor(dark ? "#ECECEC" : "#171717");
    muted = Color.parseColor(dark ? "#A6A6A6" : "#626262");
    panel = Color.parseColor(dark ? "#303030" : "#ECEEF1");
    line = Color.parseColor(dark ? "#424242" : "#D9DDE2");
    ui = new DialogUi(this, bg, fg, muted, panel, line);
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
    visibleActivity=true;
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
    visibleActivity=false;
    app.observer = null;
    app.setForeground(false, pendingImageGeneration >= 0);
    app.main.removeCallbacks(ticker);
    if(resumeSpinner!=null)resumeSpinner.stop();if(reconnectSpinner!=null)reconnectSpinner.stop();
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
    if(resumeSpinner!=null)resumeSpinner.stop();if(reconnectSpinner!=null)reconnectSpinner.stop();
    app.main.removeCallbacks(refreshMedia);
    for(MessageImageTile tile:mediaTiles)tile.release();
    if (scroll != null) scroll.removeCallbacks(pinLatest);
    if (trustDialog != null) trustDialog.dismiss();
    if (approvalDialog != null) approvalDialog.dismiss();
    if (drawer != null) drawer.dismiss();
    if (profileEditor != null) profileEditor.dismiss();
    if (modelPicker != null) modelPicker.dismiss();
    if (permissionPicker != null) permissionPicker.dismiss();
    if (browser != null) browser.dismiss();
    if (versionDialog != null) versionDialog.dismiss();
    if (directoryPicker != null) directoryPicker.dismiss();
    if (imageDialog != null) imageDialog.dismiss();
    super.onDestroy();
  }

  private int dp(float n) { return ui.dp(n); }
  private LinearLayout column() { return ui.column(); }
  private LinearLayout row() { return ui.row(); }
  private TextView text(String value, int size, int color) { return ui.text(value, size, color); }
  private GradientDrawable rounded(int color, int radius) { return ui.rounded(color, radius); }
  private void clickable(View view, Runnable action) { ui.clickable(view, action); }
  private IconView icon(String name, String label, Runnable action) { return ui.icon(name, label, action); }
  private TextView action(String label, Runnable action) { return ui.action(label, action); }

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
    permissionPicker = new PermissionPicker(this, app, ui, wide);
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
        icon("menu", UiText.get(R.string.ui_open_connection_and_chat_history), this::showDrawer), new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout heading = column();
    heading.setGravity(Gravity.CENTER);
    title = text(getString(R.string.app_name), 18, fg);
    title.setMaxLines(1);
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setTypeface(null, Typeface.BOLD);
    heading.addView(title);
    status = text(UiText.get(R.string.ui_connect_to_your_computer_via_ssh), 11, muted);
    status.setMaxLines(1);
    status.setEllipsize(TextUtils.TruncateAt.END);
    heading.addView(status);
    bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    clickable(heading, this::conversationInfo);
    runningIndicator = new ProgressBar(this);
    runningIndicator.setIndeterminate(true);
    runningIndicator.setContentDescription(UiText.get(R.string.ui_the_current_conversation_is_running));
    bar.addView(runningIndicator, new LinearLayout.LayoutParams(dp(20), dp(20)));
    bar.addView(
        icon(
            "more",
            UiText.get(R.string.ui_conversation_menu),
            this::chatMenu),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    projectLabel = text("", 12, muted);
    projectLabel.setPadding(dp(20), 0, dp(20), dp(2));
    projectLabel.setMaxLines(1);
    projectLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    clickable(projectLabel, this::conversationInfo);
    center.addView(projectLabel);
    usageLabel = text("", 12, muted);
    usageLabel.setPadding(dp(20), dp(2), dp(20), dp(8));
    usageLabel.setMaxLines(2);
    usageLabel.setEllipsize(TextUtils.TruncateAt.END);
    clickable(usageLabel, this::usageInfo);
    center.addView(usageLabel);
    readOnlyBar = column();
    readOnlyBar.setPadding(dp(16), dp(2), dp(16), dp(6));
    readOnlyLabel = text(UiText.get(R.string.ui_read_only_history_write_access_not_acquired), 13, muted);
    readOnlyLabel.setPadding(dp(4), dp(6), dp(4), dp(6));
    readOnlyBar.addView(readOnlyLabel);
    LinearLayout roActions = row();
    refreshButton=action(UiText.get(R.string.ui_refresh),app::refreshReadOnly);resumeButton=action(UiText.get(R.string.ui_resume),()->app.resume(app.threadId));newChatButton=action(UiText.get(R.string.ui_new_conversation),this::startNewChat);
    resumeButton.setContentDescription(UiText.get(R.string.ui_resume));resumeSpinner=new LoadingSpinner(fg,dp(16));
    TextView[] readOnlyControls = {refreshButton,resumeButton,newChatButton};
    for (int i=0;i<readOnlyControls.length;i++) {
      readOnlyControls[i].setSingleLine(true); readOnlyControls[i].setEllipsize(TextUtils.TruncateAt.END);
      readOnlyControls[i].setPadding(dp(8),dp(12),dp(8),dp(12));
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
      if (i<readOnlyControls.length-1) cp.setMarginEnd(dp(3)); roActions.addView(readOnlyControls[i],cp);
    }
    readOnlyBar.addView(roActions);
    LinearLayout.LayoutParams reconnectParams=new LinearLayout.LayoutParams(-1,-2); reconnectParams.topMargin=dp(3);
    reconnectButton=action(UiText.get(R.string.ui_reconnect_and_resume),app::reconnectAndResume);reconnectButton.setContentDescription(UiText.get(R.string.ui_reconnect_and_resume));reconnectSpinner=new LoadingSpinner(fg,dp(16));
    readOnlyBar.addView(reconnectButton,reconnectParams);
    readOnlyBar.setVisibility(View.GONE);
    center.addView(readOnlyBar);
    error = text("", 13, isDark() ? Color.rgb(255, 151, 137) : Color.rgb(165, 47, 36));
    error.setContentDescription(UiText.get(R.string.ui_view_connection_errors));
    error.setPadding(dp(20), dp(12), dp(20), dp(12));
    error.setBackground(rounded(panel, 12));
    error.setMaxLines(2);
    error.setEllipsize(TextUtils.TruncateAt.END);
    clickable(error, this::showConnectionError);
    center.addView(error);
    FrameLayout viewport = new FrameLayout(this);
    center.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
    scroll = new ConversationScrollView(this);
    scroll.setContentDescription(UiText.get(R.string.ui_conversation_messages));
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
    historyButton=action(UiText.get(R.string.ui_load_older_messages),this::loadOlderMessages);
    historyButton.setContentDescription(UiText.get(R.string.ui_load_older_messages));
    LinearLayout.LayoutParams historyParams=new LinearLayout.LayoutParams(wide?dp(Math.min(740,available)-32):-1,dp(48));historyParams.setMargins(dp(16),dp(8),dp(16),0);
    conversation.addView(historyButton,historyParams);historyButton.setVisibility(View.GONE);
    messages = column();
    messages.setPadding(dp(16), dp(8), dp(16), dp(8));
    conversation.addView(
        messages, new LinearLayout.LayoutParams(wide ? dp(Math.min(740, available)) : -1, -2));
    busyLabel = text(UiText.get(R.string.ui_processing_14), 14, muted);
    busyLabel.setPadding(dp(16), 0, dp(16), dp(8));
    conversation.addView(
        busyLabel, new LinearLayout.LayoutParams(wide ? dp(Math.min(740, available)) : -1, -2));
    jump = icon("arrow", UiText.get(R.string.ui_scroll_to_latest_message), () -> { historyLimit=60;scroll.follow(); render(); followLatest(); });
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
          scheduleMedia();
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
    input.setHint(UiText.get(R.string.ui_send_message));
    input.setBackgroundColor(Color.TRANSPARENT);
    input.setMinLines(1);
    input.setMaxLines(6);
    input.setPadding(dp(12), dp(12), dp(8), dp(8));
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setContentDescription(UiText.get(R.string.ui_message_input));
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
        icon("plus", UiText.get(R.string.ui_add_attachment), this::pickImage),
        new LinearLayout.LayoutParams(dp(40), dp(40)));
    modelButton = text(UiText.get(R.string.ui_default_model), 12, fg);
    modelButton.setPadding(dp(4), dp(6), dp(4), dp(6));
    modelButton.setMinHeight(dp(40));
    modelButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    modelButton.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(line), null, rounded(fg, 8)));
    modelButton.setContentDescription(UiText.get(R.string.ui_select_model_and_reasoning_effort));
    clickable(modelButton, this::chooseModel);
    LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(0, -2, 1);
    modelParams.setMargins(0, 0, dp(4), 0);
    controls.addView(modelButton, modelParams);
    permissionButton = icon("shield", UiText.get(R.string.ui_permissions_1_s, UiPresentation.permission(PermissionMode.INHERIT)), this::choosePermission);
    controls.addView(permissionButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
    send = icon("arrow", UiText.get(R.string.ui_send_message), this::sendMessage);
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

  private void loadOlderMessages() {
    int before=conversation.getHeight(),y=scroll.getScrollY();String thread=app.threadId;
    scroll.read();historyLimit+=60;historyRender=true;render();historyRender=false;
    conversation.post(()->{if(thread.equals(app.threadId)){scroll.scrollTo(0,y+Math.max(0,conversation.getHeight()-before));scheduleMedia();}});
  }

  private static boolean sameContent(ChatItem a,ChatItem b) {
    return b!=null && a.text.equals(b.text) && a.detail.equals(b.detail) && a.status.equals(b.status)
        && a.phase.equals(b.phase) && a.type.equals(b.type) && a.images.equals(b.images)
        && Objects.equals(a.interaction==null?null:a.interaction.toString(),b.interaction==null?null:b.interaction.toString());
  }

  private void render() {
    if(imageDialog!=null && !imageDialog.isCurrent())imageDialog.dismiss();
    if (!renderedThread.equals(app.threadId)) {
      if(imageDialog!=null)imageDialog.dismiss();
      renderedThread = app.threadId;
      if (input != null) { input.setText(app.draftText); input.setSelection(input.length()); }
      scroll.follow();
      historyLimit=60;firstRender=true;
      messageUpdatesPaused = false;
    }
    title.setText(app.threadTitle.isEmpty() ? (app.threadId.isEmpty() ? getString(R.string.app_name) : UiText.get(R.string.ui_unnamed_conversation)) : app.threadTitle);
    String project = app.currentProject();
    projectLabel.setText(UiText.get(R.string.ui_project_1_s, project.isEmpty() ? UiText.get(R.string.ui_remote_default_directory) : project));
    projectLabel.setVisibility(app.profile == null ? View.GONE : View.VISIBLE);
    usageLabel.setText(app.usage.summary());
    usageLabel.setVisibility(app.connected ? View.VISIBLE : View.GONE);
    boolean recovering=app.recoveryInProgress();
    readOnlyBar.setVisibility(app.readOnly || app.connecting || recovering ? View.VISIBLE : View.GONE);
    readOnlyLabel.setText(app.connecting || recovering ? UiPresentation.status(app.recoveryStatus()) : UiText.get(R.string.ui_read_only_history_write_access_not_acquired));
    boolean available=app.connected && !app.connecting && !recovering;
    refreshButton.setEnabled(available && !app.busy);newChatButton.setEnabled(available);
    resumeButton.setEnabled(available && app.readOnly && !app.attaching);reconnectButton.setEnabled(!app.connecting && !recovering && !app.busy);
    updateRecoveryButton(resumeButton,resumeSpinner,recovering,app.replaying?UiText.get(R.string.ui_synchronizing):UiText.get(R.string.ui_resuming),UiText.get(R.string.ui_resume));
    updateRecoveryButton(reconnectButton,reconnectSpinner,app.connecting,UiText.get(R.string.ui_connecting),UiText.get(R.string.ui_reconnect_and_resume));
    input.setEnabled(true);
    input.setHint(app.readOnly ? UiText.get(R.string.ui_prepare_a_message_resume_before_sending) : UiText.get(R.string.ui_send_message));
    status.setText(app.connected ? AppIcons.label(this,"●  " + app.profile.title() + "  ·  SSH","status",null,Color.rgb(48,148,112),dp(14)) : UiPresentation.status(app.state));
    status.setTextColor(app.connected ? Color.parseColor(isDark()?"#309470":"#247A5B") : muted);
    error.setText(app.error.isEmpty() ? "" : DiagnosticText.display(app.error, app.errorDiagnostic).split("\n", 2)[0] + UiText.get(R.string.ui_tap_for_the_cause_and_diagnostic_details));
    error.setVisibility(app.error.isEmpty() ? View.GONE : View.VISIBLE);
    renderAttachments();
    renderQueuedMessage();
    updateSend();
    PermissionMode displayedPermission=app.displayedPermission();
    boolean forced = app.forcedApprovals.active();
    permissionButton.icon = forced ? "bolt" : PermissionPicker.icon(displayedPermission);
    permissionButton.color = forced ? permissionPicker.red() : permissionPicker.color(displayedPermission);
    permissionButton.setContentDescription(UiText.get(R.string.ui_permissions_1_s, forced ? UiText.get(R.string.ui_force_allow) : UiPresentation.permission(displayedPermission)));
    permissionButton.setTooltipText(forced ? UiText.get(R.string.ui_force_allow_automatically_approve_until_new_permissions_take_effe) : UiPresentation.permission(displayedPermission) + (app.permissionMode==PermissionMode.INHERIT && displayedPermission!=PermissionMode.INHERIT ? UiText.get(R.string.ui_current_remote_status) : ""));
    permissionButton.invalidate();
    modelButton.setText(ModelOptions.displayName(app.models, app.model)
        + (app.reasoningEffort.isEmpty() ? "" : " · " + ModelOptions.label(app.reasoningEffort))
        + (app.serviceTierSummary().isEmpty() ? "" : " · " + app.serviceTierSummary()));
    modelButton.setTooltipText(modelButton.getText());
    runningIndicator.setVisibility(app.currentThreadRunning() ? View.VISIBLE : View.GONE);
    if (scroll.isFollowing() || historyRender) {
      messageUpdatesPaused = false;
      jump.setTooltipText(UiText.get(R.string.ui_scroll_to_latest_message));
      List<ChatItem> visibleItems = new ArrayList<>();
      List<ChatItem> presentation=app.displayItems();
      int start=Math.max(0,presentation.size()-historyLimit);
      historyButton.setVisibility(start>0?View.VISIBLE:View.GONE);
      historyButton.setText(UiText.quantity(R.plurals.ui_earlier_messages, start));
      for (ChatItem source : presentation.subList(start,presentation.size())) {
        ChatItem snapshot = new ChatItem(source.id, source.type, source.text);
        snapshot.detail = source.detail; snapshot.status = source.status;
        snapshot.phase=source.phase;snapshot.turnId=source.turnId;
        if(source.interaction!=null)try{snapshot.interaction=new JSONObject(source.interaction.toString());}catch(JSONException ignored){}
        snapshot.images.addAll(source.images);
        visibleItems.add(snapshot);
      }
      empty.setVisibility(visibleItems.isEmpty() && !app.busy ? View.VISIBLE : View.GONE);
      messages.setVisibility(visibleItems.isEmpty() ? View.GONE : View.VISIBLE);
      busyLabel.setVisibility(app.currentThreadRunning() ? View.VISIBLE : View.GONE);
      if (app.remoteRunning && app.readOnly) busyLabel.setText(UiText.get(R.string.ui_remote_task_running_history_refreshes_automatically));
      if (empty.getVisibility() == View.VISIBLE) renderEmpty();
      List<String> desired = new ArrayList<>();
      for (ChatItem item : visibleItems) desired.add(item.id);
      boolean styleChanged=false;
      for(ChatItem item:visibleItems){ChatItem prior=currentItems.get(item.id);if(prior!=null && (!prior.type.equals(item.type)||!prior.phase.equals(item.phase)))styleChanged=true;}
      if (!desired.equals(order) || styleChanged) {
        boolean appendOnly = desired.size() >= order.size() && desired.subList(0, order.size()).equals(order);
        if(styleChanged)appendOnly=false;
        int from = appendOnly ? order.size() : 0;
        if (!appendOnly) {
          messages.removeAllViews();
          texts.clear();
          messageBodies.clear();
          mediaRows.clear();
          mediaSources.clear();
          for(MessageImageTile tile:mediaTiles)tile.release();
          mediaTiles.clear();questionCards.clear();
          rendered.clear();
          currentItems.clear();
        }
        order.clear();
        order.addAll(desired);
        for (int i = from; i < visibleItems.size(); i++) addItem(visibleItems.get(i), appendOnly && !firstRender);
      }
      for (ChatItem item : visibleItems) {
        currentItems.put(item.id, item);
        if (!sameContent(item,rendered.get(item.id))) {
          rendered.put(item.id, item);
          QuestionHistoryView questionCard=questionCards.get(item.id);
          if(questionCard!=null && item.interaction!=null)questionCard.bind(item.interaction);
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
      updateMessageSpacing();
      if (!visibleItems.isEmpty() && !historyRender) followLatest();
      firstRender = false;
    } else {
      messageUpdatesPaused = true;
      jump.setVisibility(View.VISIBLE);
      jump.setTooltipText(UiText.get(R.string.ui_message_updates_paused_return_to_the_bottom_to_see_the_latest_con));
    }
    scheduleMedia();
    if (wide) fillNavigation(sidebar, false);
    if (browser != null && browser.isShowing()) browser.refresh();
    if (drawer != null && drawer.isShowing())
      fillNavigation((LinearLayout) drawer.findViewById(R.id.navigation), true);
    showPending();
  }

  private void updateRecoveryButton(TextView button,LoadingSpinner spinner,boolean active,String busyText,String idleText) {
    button.setText(active?busyText:idleText);button.setTooltipText(active?UiPresentation.status(app.recoveryStatus()):idleText);
    button.setCompoundDrawablePadding(dp(4));button.setCompoundDrawables(active?spinner:null,null,null,null);
    if(active && visibleActivity)spinner.start();else spinner.stop();
  }

  private CharSequence toolTitle(ChatItem m) {
    String symbol = m.status.equals("completed") ? "✓" : m.status.equals("failed") ? "!" : "›";
    String icon=m.status.equals("completed")?"check":m.status.equals("failed")?"error":"chevron";
    return AppIcons.label(this,symbol + "  " + UiPresentation.itemTitle(m).replaceAll("\\s+", " ") + "  ›",icon,"chevron",muted,dp(15));
  }

  private void renderEmpty() {
    empty.removeAllViews();
    IconView mark = new IconView(this, "brand", fg);
    mark.setBackground(rounded(panel, 28));
    empty.addView(mark, new LinearLayout.LayoutParams(dp(88), dp(88)));
    TextView h = text(app.connected ? UiText.get(R.string.ui_what_would_you_like_to_do_remotely) : UiText.get(R.string.ui_your_computer_always_within_reach), 26, fg);
    h.setTypeface(null, Typeface.BOLD);
    h.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
    hp.topMargin = dp(26);
    empty.addView(h, hp);
    TextView sub = text(app.connected ? UiText.get(R.string.ui_start_with_a_question_and_keep_your_work_moving) : UiText.get(R.string.ui_connect_to_the_ssh_host_and_continue_your_work_here), 14, muted);
    sub.setGravity(Gravity.CENTER);
    sub.setLineSpacing(dp(5), 1);
    sub.setPadding(0, dp(12), 0, dp(28));
    empty.addView(sub);
    if (app.connected) {
      for (String suggestion : new String[] {UiText.get(R.string.ui_introduce_this_project), UiText.get(R.string.ui_check_this_project_for_issues), UiText.get(R.string.ui_continue_previous_work)}) {
        TextView chip =
            action(
                suggestion + "  ↗",
                () -> {
                  input.setText(suggestion);
                  input.setSelection(suggestion.length());
                  input.requestFocus();
                });
        chip.setText(AppIcons.label(this,chip.getText().toString(),null,"arrowUpRight",fg,dp(18)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(wide ? dp(340) : -1, -2);
        cp.bottomMargin = dp(10);
        empty.addView(chip, cp);
      }
    } else {
      TextView button =
          action(
              app.connecting ? UiText.get(R.string.ui_cancel_connection) : UiText.get(R.string.ui_connect_to_computer),
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
    messages.addView(wrap, wp);
    if(item.type.equals("question")) {
      QuestionHistoryView card=new QuestionHistoryView(this,fg,muted,
          Color.parseColor(isDark()?"#1C1C1C":"#FFFFFF"),
          fg,this::copy);
      questionCards.put(item.id,card);currentItems.put(item.id,item);
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);wrap.addView(card,cp);
      return;
    }
    TextView content = text("", item.prose() ? 16 : 13, item.prose() ? fg : muted);
    content.setLineSpacing(dp(5), 1);
    content.setTextIsSelectable(true);
    content.setLinksClickable(true);
    content.setMovementMethod(LinkMovementMethod.getInstance());
    if (!item.type.equals("agentMessage") && !item.type.equals("plan")) texts.put(item.id, content);
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
    if (item.type.equals("agentMessage") || item.type.equals("plan")) {
      boolean plan=item.type.equals("plan"),progress=!plan && item.phase.equals("commentary");
      LinearLayout proseCard=column();
      proseCard.setBackground(rounded(progress?bg:Color.parseColor(isDark()?(plan?"#202020":"#161616"):(plan?"#EFF1F4":"#FFFFFF")),18));
      proseCard.setPadding(dp(progress?4:14),dp(progress?3:6),dp(progress?4:14),dp(progress?3:8));
      wrap.addView(proseCard,new LinearLayout.LayoutParams(-1,-2));
      if(!progress) {
        LinearLayout heading=row();TextView kind=text(plan?UiText.get(R.string.ui_plan):UiText.get(R.string.ui_reply),12,muted);heading.addView(kind,new LinearLayout.LayoutParams(0,-2,1));
        IconView copyButton=icon("copy",plan?UiText.get(R.string.ui_copy_plan):UiText.get(R.string.ui_copy_reply),()->{ChatItem latest=currentItems.get(item.id);if(latest!=null)copy(latest.text);});
        heading.addView(copyButton,new LinearLayout.LayoutParams(dp(48),dp(48)));proseCard.addView(heading);
      }
      MessageBodyView messageBody = new MessageBodyView(this,progress?muted:fg,muted,Color.parseColor(isDark()?"#2B2B2B":"#ECEEF1"),this::openLink,this::copy);
      messageBodies.put(item.id,messageBody); proseCard.addView(messageBody,new LinearLayout.LayoutParams(-1,-2));
    } else if(item.user()) {
      LinearLayout bubbleRow=row();bubbleRow.setGravity(Gravity.END|Gravity.BOTTOM);
      IconView copyButton=icon("copy", UiText.get(R.string.ui_copy_message), () -> {ChatItem latest=currentItems.get(item.id);if(latest!=null)copy(latest.text);});
      copyButton.alignBottomEnd=true;
      bubbleRow.addView(copyButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
      bubbleRow.addView(content,new LinearLayout.LayoutParams(-2,-2));
      wrap.addView(bubbleRow,new LinearLayout.LayoutParams(-1,-2));
    } else wrap.addView(content, new LinearLayout.LayoutParams(item.prose() ? -2 : -1, -2));
    LinearLayout media = column();
    mediaRows.put(item.id, media);
    wrap.addView(media, new LinearLayout.LayoutParams(-1, -2));
    if (animate) {
      wrap.setAlpha(0);
      wrap.setTranslationY(dp(6));
      wrap.animate().alpha(1).translationY(0).setDuration(180).start();
    }
  }

  // One gap per visible boundary, rather than stacking each message's margins.
  // Run after updates as empty reasoning items can become visible while streaming.
  private void updateMessageSpacing() {
    ChatItem previous=null;
    for(int i=0;i<messages.getChildCount();i++) {
      View wrap=messages.getChildAt(i);ChatItem item=currentItems.get(order.get(i));
      boolean visible=wrap.getVisibility()!=View.GONE;
      int gap=visible && previous!=null ? dp(messageGap(previous,item)) : 0;
      LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)wrap.getLayoutParams();
      if(params.topMargin!=gap || params.bottomMargin!=0){params.topMargin=gap;params.bottomMargin=0;wrap.setLayoutParams(params);}
      if(visible)previous=item;
    }
  }
  private static int messageGap(ChatItem before,ChatItem after) {
    boolean beforeSummary=!before.prose() && !before.type.equals("question");
    boolean afterSummary=!after.prose() && !after.type.equals("question");
    if(beforeSummary && afterSummary)return 2;
    boolean beforeProgress=before.type.equals("agentMessage") && before.phase.equals("commentary");
    boolean afterProgress=after.type.equals("agentMessage") && after.phase.equals("commentary");
    if((beforeSummary && afterProgress) || (beforeProgress && afterSummary))return 6;
    return 8;
  }

  private void updateSend() {
    if (send == null) return;
    boolean hasText = !input.getText().toString().trim().isEmpty();
    boolean hasContent=hasText || !app.draftImages.isEmpty() || !app.draftFiles.isEmpty();
    boolean stop = app.busy && !hasContent;
    send.icon = stop ? "stop" : "arrow";
    boolean enabled =
        !app.replaying && !app.readOnly
            && !app.attaching
            && (app.busy ? (stop || app.canSteer()) : (app.connected && !app.recoveryInProgress() && hasContent));
    send.setAlpha(enabled ? 1 : .28f);
    send.color = bg;
    send.setBackground(rounded(fg, 24));
    send.setContentDescription(stop ? UiText.get(R.string.ui_stop_generating) : app.busy ? UiText.get(R.string.ui_send_steering_message) : UiText.get(R.string.ui_send_message));
    send.setEnabled(enabled);
    send.invalidate();
  }

  private void sendMessage() {
    if (app.readOnly) {
      toast(UiText.get(R.string.ui_this_conversation_is_running_in_another_client_resume_it_or_creat));
      return;
    }
    if (app.attaching) { toast(UiText.get(R.string.ui_the_attachment_is_being_uploaded_please_wait)); return; }
    if (app.busy) {
      String guidance = input.getText().toString().trim();
      if (guidance.isEmpty() && app.draftImages.isEmpty() && app.draftFiles.isEmpty()) app.interrupt();
      else showRunningSendOptions(guidance);
      return;
    }
    if (!app.connected) {
      showDrawer();
      return;
    }
    String s = input.getText().toString().trim();
    if (s.isEmpty() && app.draftImages.isEmpty() && app.draftFiles.isEmpty()) return;
    app.send(s);
    input.setText("");
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(input.getWindowToken(), 0);
  }

  private void showRunningSendOptions(String text) {
    final String thread = app.threadId, turn = app.turnId;
    LinearLayout options = column();
    options.setPadding(dp(20), 0, dp(20), dp(8));
    TextView preview = text(text.isEmpty()?UiText.get(R.string.ui_send_selected_attachments):text, 14, muted);
    preview.setMaxLines(2); preview.setEllipsize(TextUtils.TruncateAt.END);
    preview.setPadding(dp(8), dp(4), dp(8), dp(16)); options.addView(preview);
    Dialog[] holder = new Dialog[1];
    options.addView(action(UiText.get(R.string.ui_steer_now), () -> {
      if (thread.equals(app.threadId) && turn.equals(app.turnId) && app.steer(text)) {
        if (input.getText().toString().trim().equals(text)) input.setText("");
      } else toast(UiText.get(R.string.ui_this_turn_s_state_has_changed_send_again));
      holder[0].dismiss();
    }));
    TextView later = action(UiText.get(R.string.ui_send_later), () -> {
      if (thread.equals(app.threadId) && turn.equals(app.turnId) && app.queueMessage(text,true)) {
        if (input.getText().toString().trim().equals(text)) input.setText("");
      } else toast(app.queuedMessage().isEmpty() ? UiText.get(R.string.ui_this_turn_s_state_has_changed_send_again) : UiText.get(R.string.ui_a_message_is_already_queued_send_or_cancel_it_first));
      holder[0].dismiss();
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
    options.addView(later, lp);
    holder[0] = sheet(UiText.get(R.string.ui_send_options), options);
  }

  private void renderQueuedMessage() {
    queuedBar.removeAllViews();
    String queued = app.queuedMessage();
    queuedBar.setVisibility(queued.isEmpty() ? View.GONE : View.VISIBLE);
    if (queued.isEmpty()) return;
    TextView preview = text(UiText.get(R.string.ui_queued) + queued, 12, muted);
    preview.setMaxLines(1); preview.setEllipsize(TextUtils.TruncateAt.END);
    preview.setPadding(dp(8), dp(8), dp(8), dp(8));
    queuedBar.addView(preview, new LinearLayout.LayoutParams(0, -2, 1));
    if (app.connected && !app.busy && !app.readOnly)
      queuedBar.addView(icon("arrow", UiText.get(R.string.ui_send_queued_message), app::sendQueuedMessage), new LinearLayout.LayoutParams(dp(40), dp(40)));
    queuedBar.addView(icon("close", UiText.get(R.string.ui_cancel_queued_message), app::cancelQueuedMessage), new LinearLayout.LayoutParams(dp(40), dp(40)));
  }

  private void pickImage() {
    if (!app.canAttach()) { toast(UiPresentation.status(app.attachmentUnavailable())); return; }
    if (pendingImageGeneration >= 0) { toast(UiText.get(R.string.ui_please_complete_the_current_attachment_selection_first)); return; }
    LinearLayout choices = column(); choices.setPadding(dp(20), 0, dp(20), dp(16));
    TextView hint = text(UiText.get(R.string.ui_select_multiple_images_or_use_a_file_manager_to_choose_documents), 13, muted);
    hint.setPadding(dp(4), 0, dp(4), dp(12)); choices.addView(hint);
    Dialog[] holder = new Dialog[1];
    String[] labels = {UiText.get(R.string.ui_phone_gallery), UiText.get(R.string.ui_other_gallery_image_app), UiText.get(R.string.ui_system_picker)};
    ImagePicker.Source[] sources = ImagePicker.Source.values();
    for (int i = 0; i < labels.length; i++) {
      ImagePicker.Source source = sources[i];
      TextView option = action(labels[i], () -> { holder[0].dismiss(); launchImagePicker(source); });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8);
      choices.addView(option, params);
    }
    choices.addView(action(UiText.get(R.string.ui_select_file),()->{holder[0].dismiss();launchFilePicker();}));
    holder[0] = sheet(UiText.get(R.string.ui_add_attachment), choices);
  }

  private String imageProject() {
    return app.threadCwd + "\n" + (app.profile == null ? "" : app.profile.cwd);
  }

  private void launchImagePicker(ImagePicker.Source source) {
    if (!app.canAttach() || app.profile == null) {toast(UiPresentation.status(app.attachmentUnavailable())); return;}
    int remaining = app.remainingImages();
    if (remaining <= 0) { toast(UiText.quantity(R.plurals.ui_image_limit, MediaFiles.MAX_ATTACHMENTS)); return; }
    pendingImageGeneration = app.connectionGeneration(); pendingImageHost = app.profile.id;
    pendingImageThread = app.threadId; pendingImageProject = imageProject();
    if (!ImagePicker.launch(this, source, remaining)) {
      pendingImageGeneration = -1; toast(UiText.get(R.string.ui_no_image_picker_available_install_or_enable_a_gallery_or_file_app));
    }
  }

  private void launchFilePicker() {
    if(!app.canAttach() || app.profile==null){toast(UiPresentation.status(app.attachmentUnavailable()));return;}
    int remaining=app.remainingFiles();
    if(remaining<=0){toast(UiText.quantity(R.plurals.ui_file_limit, FileAttachment.MAX_FILES));return;}
    pendingImageGeneration=app.connectionGeneration();pendingImageHost=app.profile.id;
    pendingImageThread=app.threadId;pendingImageProject=imageProject();
    if(!FilePicker.launch(this,remaining)){pendingImageGeneration=-1;toast(UiText.get(R.string.ui_no_file_manager_available_install_or_enable_a_file_app));}
  }

  private void addFileAttachment(FileAttachment file) {
    LinearLayout tile=row();tile.setGravity(Gravity.CENTER_VERTICAL);tile.setBackground(rounded(bg,8));
    tile.setPadding(dp(10),dp(6),0,dp(6));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(210),dp(88));params.setMarginEnd(dp(8));
    attachmentStrip.addView(tile,params);
    TextView label=text(file.name+"\n"+android.text.format.Formatter.formatShortFileSize(this,file.size),13,fg);
    label.setMaxLines(3);label.setEllipsize(TextUtils.TruncateAt.END);label.setContentDescription(UiText.get(R.string.ui_pending_file_1_s, file.name));
    tile.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    IconView remove=icon("close",UiText.get(R.string.ui_remove_file_1_s, file.name),()->{
      if(app.steering || app.attaching || (app.busy && app.turnId.isEmpty()))return;
      app.draftFiles.remove(file);app.changed();
    });
    remove.setEnabled(!app.steering && !app.attaching && (!app.busy || !app.turnId.isEmpty()));
    tile.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));
  }

  private void renderAttachments() {
    attachmentStrip.removeAllViews();
    attachmentScroll.setVisibility(app.attaching || !app.draftImages.isEmpty() || !app.draftFiles.isEmpty() ? View.VISIBLE : View.GONE);
    int index = 0;
    for (String path : new ArrayList<>(app.draftImages)) {
      addAttachmentPreview(app.draftImagePreviews.get(path), path, ++index);
    }
    for(FileAttachment file:new ArrayList<>(app.draftFiles))addFileAttachment(file);
    if (app.attaching) {
      if(!app.attachingFiles)addAttachmentPreview(app.pendingImagePreview, null, index + 1);
      TextView uploading = text((app.attachingFiles?UiText.get(R.string.ui_uploading_files):UiText.get(R.string.ui_uploading_images)) + (app.imageUploadCount > 1 ? "（" + app.imageUploadIndex + " / " + app.imageUploadCount + "）" : ""), 13, muted);
      uploading.setContentDescription(app.attachingFiles?UiText.get(R.string.ui_uploading_files):UiText.get(R.string.ui_uploading_images)); attachmentStrip.addView(uploading);
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
      preview.setContentDescription(UiText.get(R.string.ui_pending_image_1_d, index));
      preview.setOnClickListener(v->{if(path!=null)openImage(path);else openImagePreview(bitmap);});PressFeedback.apply(preview,isDark());
      tile.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView label = text(UiText.get(R.string.ui_image_1_d, index), 12, muted);
      label.setGravity(Gravity.CENTER);
      label.setContentDescription(UiText.get(R.string.ui_pending_image_1_d, index));
      if(path!=null){label.setOnClickListener(v->openImage(path));PressFeedback.apply(label,isDark());}
      tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
    }
    if (path != null) {
      IconView remove = icon("close", UiText.get(R.string.ui_remove_image_1_d, index), () -> {
        if (app.steering || app.attaching || (app.busy && app.turnId.isEmpty())) return;
        app.draftImages.remove(path);
        app.draftImagePreviews.remove(path);
        app.changed();
      });
      remove.setBackground(rounded(panel, 8));
      remove.setEnabled(!app.steering && !app.attaching && (!app.busy || !app.turnId.isEmpty()));
      tile.addView(remove, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END));
    }
  }

  private void scheduleMedia() {
    if(mediaScheduled)return;mediaScheduled=true;app.main.postDelayed(refreshMedia,16);
  }
  private void updateMediaViewport() {
    if(isDestroyed() || scroll==null)return;
    android.graphics.Rect rect=new android.graphics.Rect();
    for(MessageImageTile tile:mediaTiles){tile.refreshConnection();tile.visible=tile.valid() && tile.tile.isShown() && tile.tile.getGlobalVisibleRect(rect) && rect.height()>0;if(!tile.visible)tile.release();}
    for(MessageImageTile tile:mediaTiles)if(tile.visible)tile.load();
  }

  private void openImagePreview(android.graphics.Bitmap preview) {
    showImage(null,preview,Collections.emptyList());
  }

  private void openImage(String source) {
    showImage(source,imagePreview(source),Collections.emptyList());
  }

  private void openConversationImage(String source) {
    showImage(source,imagePreview(source),MediaFiles.imageGallery(app.items,app.currentProject()));
  }

  private android.graphics.Bitmap imagePreview(String source) {
    android.graphics.Bitmap preview=app.draftImagePreviews.get(source);
    if(preview==null)for(MessageImageTile tile:mediaTiles)if(tile.source.equals(source)) {
      preview=tile.cachedPreview();
      if(preview==null && tile.loaded && tile.preview.getDrawable() instanceof BitmapDrawable)preview=((BitmapDrawable)tile.preview.getDrawable()).getBitmap();
      if(preview!=null)break;
    }
    return preview;
  }

  private void showImage(String source,android.graphics.Bitmap preview,List<String> gallery) {
    if(imageDialog!=null)imageDialog.dismiss();
    int generation=app.connectionGeneration();String host=app.profile==null?"":app.profile.id,thread=app.threadId;
    imageDialog=new ImageDialog(this,isDark(),source,preview,gallery,this::imagePreview,
        (path,out,active)->app.readMedia(path,out,MediaFiles.IMAGE_LIMIT,active),
        ()->!isDestroyed() && generation==app.connectionGeneration() && host.equals(app.profile==null?"":app.profile.id)
            && (Looper.myLooper()!=Looper.getMainLooper() || thread.equals(app.threadId)),
        this::saveMedia);
  }

  private void renderMedia(ChatItem item) {
    LinearLayout row = mediaRows.get(item.id);
    List<String> sources = MediaFiles.images(item);
    if (row == null) return;
    if (sources.equals(mediaSources.get(item.id)) && mediaTiles.stream().noneMatch(tile->tile.itemId.equals(item.id) && !tile.sameContext())) return;
    mediaSources.put(item.id, sources);
    mediaTiles.removeIf(tile->{if(!tile.itemId.equals(item.id))return false;tile.release();return true;});
    row.removeAllViews();
    LinearLayout grid=null;int index=0;
    for(String original:sources) {
      String source=original;
      try{if(!source.startsWith("data:") && !source.startsWith("https://"))source=MediaFiles.remotePath(source,app.currentProject());}catch(Exception ignored){}
      if(index%2==0 || sources.size()==1){grid=row();row.addView(grid,new LinearLayout.LayoutParams(-1,-2));}
      MessageImageTile tile=new MessageImageTile(this,app,ui,thumbnailWorker,item.id,source,row,sources.size()>1,()->mediaRows.get(item.id)==row,this::openConversationImage);mediaTiles.add(tile);grid.addView(tile.tile);index++;
    }
    scheduleMedia();
  }

  private void openLink(String source) {
    if (source.startsWith("https://") || source.startsWith("http://")) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(source))); }
      catch (ActivityNotFoundException e) { toast(UiText.get(R.string.ui_there_is_no_browser_that_can_open_the_link)); }
    } else saveMedia(source);
  }

  private void saveMedia(String source) {
    try {
      String resolved = source.startsWith("data:") || source.startsWith("https://") ? source
          : MediaFiles.remotePath(source, app.profile == null ? "" : app.profile.cwd);
      if (pendingDownload.length() > 0) { toast(UiText.get(R.string.ui_please_complete_the_current_save_operation_first)); return; }
      pendingDownload = resolved;
      pendingDownloadHost = app.profile == null ? "" : app.profile.id;
      startActivityForResult(MediaFiles.saveIntent(resolved), 44);
    } catch (Exception e) { pendingDownload = ""; toast(ConnectionFailure.reason(e)); }
  }

  private void copy(String s) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), s));
    toast(UiText.get(R.string.ui_copied));
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  private void showConnectionError() {
    String rawDiagnostic = app.error.isEmpty() ? app.lastError : app.error;
    String diagnostic = DiagnosticText.display(rawDiagnostic, app.error.isEmpty() ? app.lastErrorDiagnostic : app.errorDiagnostic);
    if (diagnostic.isEmpty()) {
      toast(UiText.get(R.string.ui_no_error_record_yet));
      return;
    }
    TextView detail = text(diagnostic, 14, fg);
    detail.setTextIsSelectable(true);
    detail.setPadding(dp(20), dp(12), dp(20), dp(12));
    ScrollView sc = new ScrollView(this);
    sc.addView(detail);
    new AlertDialog.Builder(this)
        .setTitle(UiText.get(R.string.ui_connection_information))
        .setView(sc)
        .setNegativeButton(UiText.get(R.string.ui_copy_diagnostics), (d, w) -> copy(diagnostic))
        .setNeutralButton(
            ConnectionSetupUi.canContinue(app, rawDiagnostic) ? UiText.get(R.string.ui_disable_persistent_mode) : app.unsentText.isEmpty() ? null : UiText.get(R.string.ui_restore_messages),
            (d, w) -> {
              if (ConnectionSetupUi.canContinue(app, rawDiagnostic)) {
                ConnectionSetupUi.continueWithoutPersistence(this, app); return;
              }
              input.setText(app.unsentText);
              input.setSelection(input.length());
              app.clearError();
            })
        .setPositiveButton(UiText.get(R.string.ui_got_it), (d, w) -> app.clearError())
        .show();
  }

  private void choosePermission() {
    permissionPicker.show();
  }

  private String detailText(ChatItem item) {
    if (item.type.equals("reasoning")) return item.detail.trim().isEmpty()
        ? (item.status.equals("inProgress") ? UiText.get(R.string.ui_waiting_for_reasoning_summary) : UiText.get(R.string.ui_the_server_provided_no_summary_for_this_reasoning_item_some_reaso)) : item.detail;
    return UiPresentation.itemTitle(item) + (item.detail.isEmpty() ? "" : "\n\n" + item.detail);
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
    layout.addView(action(UiText.get(R.string.ui_copy), () -> {
      ChatItem latest = currentItems.get(m.id);
      ChatItem source = latest == null ? m : latest;
      copy(source.type.equals("reasoning") ? detailText(source)
          : source.text + (source.detail.isEmpty() ? "" : "\n\n" + source.detail));
    }));
    Dialog dialog = sheet(m.type.equals("reasoning") ? UiText.get(R.string.ui_reasoning) : UiText.get(R.string.ui_execution_details), layout);
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

  private Dialog sheet(String heading, View content) { return ui.sheet(heading, content, wide); }

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

              public void edit() {
                if(app.profile!=null)editProfile(app.profile,false);else editProfile(new Profile(),true);
              }

              public void settings() {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(UiText.get(R.string.ui_settings))
                    .setItems(
                        new String[] {UiText.get(R.string.ui_appearance), UiText.get(R.string.ui_background_connection_settings), UiText.get(R.string.ui_font_size), UiText.get(R.string.ui_language)},
                        (d, w) -> {
                          if(w==1) backgroundSettings();
                          else if(w==2) chooseFontSize();
                          else if(w==3) LanguagePicker.show(MainActivity.this, app);
                          else
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle(UiText.get(R.string.ui_appearance))
                                .setSingleChoiceItems(
                                    new String[] {UiText.get(R.string.ui_follow_system), UiText.get(R.string.ui_light), UiText.get(R.string.ui_dark)},
                                    getPreferences(0).getInt("theme", 0),
                                    (a, b) -> {
                                      getPreferences(0).edit().putInt("theme", b).apply();
                                      a.dismiss();
                                      recreate();
                                    })
                                .setNegativeButton(UiText.get(R.string.ui_cancel), null)
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
      toast(UiText.get(R.string.ui_please_connect_to_the_ssh_host_first));
      return;
    }
    if (!app.canSwitchConversation()) {
      toast(UiText.get(R.string.ui_resuming_session_or_uploading_attachment_please_wait));
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
      toast(UiText.get(R.string.ui_connecting_session_or_uploading_attachment_please_wait)); return;
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
    TextView label = text(UiText.get(R.string.ui_connect), 12, muted);
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
      t.setText(AppIcons.label(this,t.getText().toString(),app.connected && app.profile.id.equals(p.id)?"status":"computer",null,fg,dp(18)));
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
          icon("chevron", UiText.get(R.string.ui_edit) + p.title(), () -> editProfile(p, false)),
          new LinearLayout.LayoutParams(dp(44), dp(48)));
      nav.addView(r);
    }
    TextView addConnection=action(UiText.get(R.string.ui_add_ssh_connection), () -> editProfile(new Profile(), true));
    addConnection.setText(AppIcons.label(this,addConnection.getText().toString(),"plus",null,fg,dp(18)));nav.addView(addConnection);
    TextView history = text(UiText.get(R.string.ui_recent_conversations), 12, muted);
    history.setPadding(dp(4), dp(28), 0, dp(12));
    nav.addView(history);
    if (!app.connected) {
      TextView note = text(UiText.get(R.string.ui_connect_to_a_computer_to_view_conversation_history), 13, muted);
      note.setPadding(dp(4), dp(4), 0, dp(16));
      nav.addView(note);
    } else if (app.threads.length() == 0) {
      nav.addView(text(UiText.get(R.string.ui_no_conversations_yet), 13, muted));
    }
    for (int i = 0; i < app.threads.length(); i++) {
      JSONObject t = app.threads.optJSONObject(i);
      if (t == null) continue;
      String id = t.optString("id");
      String name = t.isNull("name") ? "" : t.optString("name");
      if (name.trim().isEmpty()) name = t.optString("preview", UiText.get(R.string.ui_new_conversation));
      TextView item = text(name.trim().isEmpty() ? UiText.get(R.string.ui_new_conversation) : name, 14, fg);
      item.setPadding(dp(8), dp(14), dp(8), dp(14));
      item.setMaxLines(2);
      item.setEllipsize(TextUtils.TruncateAt.END);
      if (id.equals(app.threadId)) item.setBackground(rounded(line, 12));
      clickable(
          item,
          () -> {
            if (!app.canSwitchConversation()) {
              toast(UiText.get(R.string.ui_resuming_session_or_uploading_attachment_please_wait)); return;
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
              app.nextCursor.isEmpty() ? UiText.get(R.string.ui_refresh_conversation) : UiText.get(R.string.ui_load_more),
              () -> app.refreshThreads(!app.nextCursor.isEmpty())));
    }
    TextView theme =
        action(
            UiText.get(R.string.ui_appearance),
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(UiText.get(R.string.ui_appearance))
                    .setSingleChoiceItems(
                        new String[] {UiText.get(R.string.ui_follow_system), UiText.get(R.string.ui_light), UiText.get(R.string.ui_dark)},
                        getPreferences(0).getInt("theme", 0),
                        (d, w) -> {
                          getPreferences(0).edit().putInt("theme", w).apply();
                          d.dismiss();
                          recreate();
                        })
                    .setNegativeButton(UiText.get(R.string.ui_cancel), null)
                    .show());
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
    tp.topMargin = dp(20);
    nav.addView(theme, tp);
  }

  private void chatMenu() {
    new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_conversation))
        .setItems(new String[] {UiText.get(R.string.ui_new_conversation), UiText.get(R.string.ui_last_error), UiText.get(R.string.ui_current_project_and_conversation)}, (d,w)->{
          if(w==0)startNewChat();else if(w==1)showConnectionError();else conversationInfo();
        }).show();
  }

  private void chooseFontSize() {
    float[] scales = {0, .75f, .9f, 1f, 1.15f};
    String[] labels = {UiText.get(R.string.ui_follow_system), UiText.get(R.string.ui_small), UiText.get(R.string.ui_medium), UiText.get(R.string.ui_large), UiText.get(R.string.ui_extra_large)};
    float current = Math.min(getSharedPreferences("display", MODE_PRIVATE).getFloat("fontScale", 0), 1.15f);
    int selected = 0;
    for (int i = 0; i < scales.length; i++) if (Math.abs(scales[i] - current) < .001f) selected = i;
    new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_font_size))
        .setSingleChoiceItems(labels, selected, (d, which) -> {
          getSharedPreferences("display", MODE_PRIVATE).edit().putFloat("fontScale", scales[which]).apply();
          d.dismiss(); recreate();
        }).setNegativeButton(UiText.get(R.string.ui_cancel), null).show();
  }

  private void backgroundSettings() {
    new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_background_connection))
        .setMessage(UiText.get(R.string.ui_a_persistent_notification_is_shown_and_the_cpu_stays_awake_while))
        .setPositiveButton(UiText.get(R.string.ui_open_system_settings), (d, w) -> {
          try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              android.net.Uri.parse("package:" + getPackageName()))); }
          catch (ActivityNotFoundException e) { toast(UiText.get(R.string.ui_open_remotegpt_in_system_app_settings)); }
        }).setNeutralButton(UiText.get(R.string.ui_notifications_and_live_updates), (d, w) -> liveStatusSettings()).setNegativeButton(UiText.get(R.string.ui_back), null).show();
  }

  private void liveStatusSettings() {
    LinearLayout panelView = column();
    panelView.setPadding(dp(20), dp(8), dp(20), dp(8));
    CheckBox enabled = new CheckBox(this);
    AppIcons.selection(enabled,fg,muted,false);
    enabled.setText(UiText.get(R.string.ui_show_task_status_and_approval_live_updates));
    enabled.setTextColor(fg);
    enabled.setChecked(getSharedPreferences("background", MODE_PRIVATE).getBoolean("liveStatus", true));
    panelView.addView(enabled);
    String support;
    if (Build.VERSION.SDK_INT >= 36) {
      boolean allowed = getSystemService(NotificationManager.class).canPostPromotedNotifications();
      support = allowed ? UiText.get(R.string.ui_live_updates_are_allowed_their_appearance_is_determined_by_coloro) : UiText.get(R.string.ui_allow_live_updates_in_system_notification_settings);
    } else support = UiText.get(R.string.ui_android_16_live_updates_are_unavailable_on_this_system_standard_t);
    TextView description = text(support + UiText.get(R.string.ui_status_is_shown_while_running_when_approval_is_needed_expand_the), 14, fg);
    description.setPadding(0, dp(12), 0, dp(12));
    panelView.addView(description);
    enabled.setOnCheckedChangeListener((button, checked) -> {
      getSharedPreferences("background", MODE_PRIVATE).edit().putBoolean("liveStatus", checked).apply();
      app.liveStatus.update();
    });
    new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_live_updates_and_notifications)).setView(panelView)
        .setPositiveButton(UiText.get(R.string.ui_open_system_notification_settings), (d, w) -> {
          try { startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())); }
          catch (ActivityNotFoundException e) { toast(UiText.get(R.string.ui_open_remotegpt_s_notification_settings_in_system_settings)); }
        }).setNegativeButton(UiText.get(R.string.ui_back), null).show();
  }

  private void conversationInfo() {
    String project = app.currentProject();
    String details = UiText.get(R.string.ui_project_137) + (project.isEmpty() ? UiText.get(R.string.ui_remote_default_directory_no_path_returned) : project)
        + UiText.get(R.string.ui_conversation_139) + (app.threadTitle.isEmpty() ? UiText.get(R.string.ui_new_conversation) : app.threadTitle)
        + UiText.get(R.string.ui_conversation_id) + (app.threadId.isEmpty() ? UiText.get(R.string.ui_created_after_sending_first_message) : app.threadId)
        + UiText.get(R.string.ui_status) + (!app.connected ? UiText.get(R.string.ui_not_connected) : app.readOnly ? UiText.get(R.string.ui_read_only_try_resuming) : app.busy ? UiText.get(R.string.ui_processing) : UiText.get(R.string.ui_ready_to_continue));
    new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_current_project_and_conversation)).setMessage(details)
        .setPositiveButton(UiText.get(R.string.ui_close), null)
        .setNeutralButton(UiText.get(R.string.ui_copy), (d, w) -> {
          android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          clipboard.setPrimaryClip(android.content.ClipData.newPlainText(UiText.get(R.string.ui_current_project_and_conversation), details));
          toast(UiText.get(R.string.ui_copied));
        }).show();
  }

  private void usageInfo() {
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_remaining_usage))
        .setMessage(app.usage.details()).setPositiveButton(UiText.get(R.string.ui_close), null)
        .setNeutralButton(UiText.get(R.string.ui_refresh), null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> app.refreshUsage()));
    Runnable update = new Runnable() {
      public void run() {
        if (!dialog.isShowing()) return;
        dialog.setMessage(app.usage.loading ? UiText.get(R.string.ui_loading_remaining_usage) : app.usage.details());
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
            app.profile.user + "@" + app.profile.host + ":" + app.profile.port + "\n" + UiPresentation.status(app.state))
        .setPositiveButton(UiText.get(R.string.ui_back), null)
        .setNeutralButton(UiText.get(R.string.ui_edit_connection), (d, w) -> editProfile(app.profile, false))
        .setNegativeButton(UiText.get(R.string.ui_disconnect), (d, w) -> app.disconnect())
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

  private void editProfile(Profile original, boolean fresh) {
    if (profileEditor != null && profileEditor.isShowing()) return;
    if (drawer != null) drawer.dismiss();
    profileEditor = new ProfileEditor(this, app, ui, wide, new ProfileEditor.Actions() {
      public void connect(Profile profile) { connectProfile(profile); }
      public void removed() { render(); }
    });
    profileEditor.show(original, fresh);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == ImagePicker.REQUEST || request == FilePicker.REQUEST) {
      boolean sameContext = pendingImageGeneration >= 0 && pendingImageGeneration == app.connectionGeneration()
          && app.profile != null && pendingImageHost.equals(app.profile.id) && pendingImageThread.equals(app.threadId)
          && pendingImageProject.equals(imageProject());
      pendingImageGeneration = -1;
      if (result == RESULT_OK && data != null) {
        List<android.net.Uri> selected = ImagePicker.selected(data);
        if (!selected.isEmpty()) {
          if (!sameContext) toast(UiText.get(R.string.ui_the_connection_or_conversation_has_changed_select_attachments_aga));
          else if(request==FilePicker.REQUEST)app.attachFiles(selected);
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
        toast(UiText.get(R.string.ui_saving_file));
        mediaWorker.execute(() -> {
          try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
            if (out == null) throw new IOException("无法写入所选位置");
            if (app.profile == null || !host.equals(app.profile.id)) throw new IOException("连接已切换，请重新下载");
            app.readMedia(source, out, MediaFiles.DOWNLOAD_LIMIT);
            app.main.post(() -> toast(UiText.get(R.string.ui_file_saved)));
          } catch (Exception e) {
            try { android.provider.DocumentsContract.deleteDocument(getContentResolver(), destination); } catch (Exception ignored) { }
            app.main.post(() -> toast(UiText.get(R.string.ui_save_failed) + ConnectionFailure.reason(e)));
          }
        });
      }
    }
    if (request == ProfileEditor.REQUEST_KEY && result == RESULT_OK && data != null && profileEditor != null)
      profileEditor.importKey(data.getData());
  }


  private void chooseModel() {
    if (modelPicker != null) modelPicker.dismiss();
    modelPicker = new ModelPicker(this, app, ui, wide);
    modelPicker.show();
  }

  private void showRemoteVersion() {
    if (!app.connected || versionDialog != null) return;
    RemoteVersion version = app.remoteVersion;
    shownVersion = version;
    String target = version.latest;
    String detail = UiText.get(R.string.ui_current_session_version) + (version.running.isEmpty() ? UiText.get(R.string.ui_unknown) : version.running)
        + UiText.get(R.string.ui_installed_version) + (version.installed.isEmpty() ? UiText.get(R.string.ui_unknown) : version.installed)
        + UiText.get(R.string.ui_latest_stable_version) + (target.isEmpty() ? UiText.get(R.string.ui_not_retrieved) : target);
    if (!version.error.isEmpty()) detail += "\n\n" + version.error;
    if (!version.reason.isEmpty()) detail += "\n\n" + version.reason;
    AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_remote_codex_version));
    if (version.canUpdate()) {
      detail += UiText.get(R.string.ui_update_remote_codex_the_new_version_is_activated_automatically_wh);
      builder.setPositiveButton(UiText.get(R.string.ui_update_to_1_s, target), (d, w) -> app.updateRemoteVersion(version, target));
      builder.setNegativeButton(UiText.get(R.string.ui_not_now), null);
    } else if (version.updating) {
      detail += UiText.get(R.string.ui_updating_keep_the_connection_open);
      builder.setPositiveButton(UiText.get(R.string.ui_got_it), null);
    } else if (version.needsRestart()) {
      detail += UiText.get(R.string.ui_the_new_version_is_installed_but_this_session_still_uses_the_old);
      builder.setPositiveButton(UiText.get(R.string.ui_restart_with_new_version), (d, w) -> confirmRemoteRestart(version));
      builder.setNegativeButton(UiText.get(R.string.ui_later), null);
    } else builder.setPositiveButton(UiText.get(R.string.ui_close), null);
    if (!version.checking && !version.updating)
      builder.setNeutralButton(UiText.get(R.string.ui_check_again), (d, w) -> app.checkRemoteVersion(true));
    builder.setMessage(detail);
    versionDialog = builder.create();
    versionDialog.setOnDismissListener(d -> { versionDialog = null; shownVersion = null; });
    versionDialog.show();
  }

  private void confirmRemoteRestart(RemoteVersion version) {
    if (!app.connected || version != app.remoteVersion || !version.needsRestart()) return;
    new AlertDialog.Builder(this)
        .setTitle(UiText.get(R.string.ui_stop_tasks_and_restart))
        .setMessage(UiText.get(R.string.ui_restarting_immediately_stops_all_active_conversations_and_pending)
            + version.installed + "。")
        .setNegativeButton(UiText.get(R.string.ui_cancel), null)
        .setPositiveButton(UiText.get(R.string.ui_stop_tasks_and_restart_171), (d, w) -> app.restartRemoteVersion(version))
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
              .setTitle(UiText.get(R.string.ui_first_connection_to_this_host))
              .setMessage(
                  app.trustHost
                      + UiText.get(R.string.ui_host_fingerprint)
                      + fingerprint
                      + UiText.get(R.string.ui_compare_this_with_the_server_s_ssh_host_fingerprint_once_trusted))
              .setNegativeButton(UiText.get(R.string.ui_cancel), (d, w) -> app.trust(false))
              .setPositiveButton(UiText.get(R.string.ui_trust_and_connect), (d, w) -> app.trust(true))
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
        p.optString("reason", UiText.get(R.string.ui_the_server_requests_the_following_action))
            + "\n\n"
            + p.optString("command", p.optString("grantRoot", UiText.get(R.string.ui_file_changes)))
            + "\n"
            + p.optString("cwd")
            + UiText.get(R.string.ui_session)
            + p.optString("threadId");
    if (p.has("networkApprovalContext")) description += UiText.get(R.string.ui_network_access) + p.opt("networkApprovalContext");
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
            .setTitle(UiText.get(R.string.ui_your_confirmation_is_required))
            .setView(sc)
            .setCancelable(false)
            .setNeutralButton(app.forcedApprovals.canOffer(req) ? UiText.get(R.string.ui_allow_all_forced) : null, null)
            .setNegativeButton(
                UiText.get(R.string.ui_deny),
                (d, w) -> {
                  app.answer(req, Json.obj("decision", "decline"));
                  approvalDialog = null;
                  shownApproval = "";
                })
            .setPositiveButton(
                UiText.get(R.string.ui_allow_once),
                (d, w) -> {
                  app.answer(req, Json.obj("decision", "accept"));
                  approvalDialog = null;
                  shownApproval = "";
                })
            .show();
    AlertDialog approvals = (AlertDialog) approvalDialog;
    if (app.forcedApprovals.canOffer(req)) {
      approvals.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(permissionPicker.red());
      approvals.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> permissionPicker.confirm(req));
    }
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
            .setTitle(UiText.get(R.string.ui_additional_information))
            .setView(sc)
            .setCancelable(false)
            .setNegativeButton(
                ConversationJournal.savedQuestion(req) ? UiText.get(R.string.ui_skip_answer) : UiText.get(R.string.ui_stop_task),
                (d, w) -> {
                  app.cancelQuestion(req);
                  approvalDialog = null;
                  shownApproval = "";
                })
            .setPositiveButton(UiText.get(R.string.ui_send), null)
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
