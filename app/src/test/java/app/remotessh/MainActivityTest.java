package app.remotessh;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;

  @Before
  public void open() {
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    activity = controller.get();
  }

  @After
  public void close() {
    controller.pause().stop().destroy();
  }

  private View find(View root, String label) {
    if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return root;
    if (label.contentEquals(
        root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        View v = find(((ViewGroup) root).getChildAt(i), label);
        if (v != null) return v;
      }
    return null;
  }

  private View content() {
    return activity.findViewById(android.R.id.content);
  }

  private void capture(String name) throws Exception {
    captureView(content(), name);
  }

  private void captureView(View v, String name) throws Exception {
    int width = activity.getResources().getDisplayMetrics().widthPixels;
    int height = activity.getResources().getDisplayMetrics().heightPixels;
    v.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, width, height);
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(b));
    File directory = new File("build/screenshots");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
      assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
  }

  private EditText inputField(View root, String label) {
    if (root instanceof EditText && label.contentEquals(root.getContentDescription()))
      return (EditText) root;
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        EditText found = inputField(((ViewGroup) root).getChildAt(i), label);
        if (found != null) return found;
      }
    return null;
  }

  @Test
  public void runningSendOpensChoiceAndShowsCancelableQueue() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.connected = true; app.busy = true; app.threadId = "t"; app.turnId = "run";
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    EditText input = (EditText) find(content(), "消息输入框"); input.setText("下一轮补充测试");
    find(content(), "发送引导消息").performClick();
    android.app.Dialog choices = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View panel = choices.getWindow().getDecorView();
    assertNotNull(find(panel, "立即引导")); assertNotNull(find(panel, "稍后发送"));
    assertEquals("", app.queuedMessage()); assertFalse(app.steering);
    captureView(panel, "running-send-options");
    find(panel, "稍后发送").performClick();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals("下一轮补充测试", app.queuedMessage()); assertEquals("", input.getText().toString());
    assertNotNull(find(content(), "待发送 · 下一轮补充测试"));
    capture("queued-message");
    find(content(), "取消待发消息").performClick();
    assertEquals("", app.queuedMessage()); assertTrue(app.busy);
    input.setText("保留这条草稿"); find(content(), "发送引导消息").performClick();
    choices = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    app.turnId = "different-turn";
    find(choices.getWindow().getDecorView(), "立即引导").performClick();
    assertFalse(app.steering); assertEquals("保留这条草稿", input.getText().toString());
  }

  @Test
  public void conversationPreservesReadingAndResumesFollowingAfterJump() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.connected = true;
    for (int i = 0; i < 25; i++) app.items.add(new ChatItem("m" + i, "agentMessage", "消息 " + i + "\n这是流式内容"));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("scroll-initial"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    ScrollView sc = (ScrollView) find(content(), "对话消息");
    ViewGroup conversation = (ViewGroup) sc.getChildAt(0);
    assertTrue(sc.getScrollY() > 0);
    assertEquals(conversation.getHeight() - sc.getHeight(), sc.getScrollY());
    sc.scrollTo(0, 0);
    ChatItem latest = new ChatItem("latest", "agentMessage", "最新回复\n".repeat(50));
    app.items.add(latest); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("scroll-new-item"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(0, sc.getScrollY());
    latest.text += "继续输出\n".repeat(40); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("scroll-streaming"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(0, sc.getScrollY());
    View delayedMedia = new View(activity);
    conversation.addView(delayedMedia, new LinearLayout.LayoutParams(200, 400));
    capture("scroll-late-layout"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(0, sc.getScrollY());
    sc.getLayoutParams().height = 300; sc.requestLayout();
    capture("scroll-viewport-resize"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(0, sc.getScrollY());
    find(content(), "滚动到最新消息").performClick();
    capture("scroll-jump"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(conversation.getHeight() - sc.getHeight(), sc.getScrollY());
    latest.text += "跟随输出\n".repeat(20); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("scroll-follow-stream"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(conversation.getHeight() - sc.getHeight(), sc.getScrollY());
    sc.scrollTo(0, 200);
    app.items.add(new ChatItem("last2", "agentMessage", "另一个工具结果\n".repeat(10))); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("scroll-reading-offset"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals(200, sc.getScrollY());
    capture("scroll-follow-latest");
  }

  @Test
  public void historyContentAndViewsStayFrozenUntilManualReturnToBottom() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.connected = true;
    for (int i = 0; i < 25; i++) app.items.add(new ChatItem("freeze" + i, "agentMessage", "历史消息 " + i + "\n原始内容"));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("freeze-initial"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    ConversationScrollView sc = (ConversationScrollView) find(content(), "对话消息");
    ViewGroup conversation = (ViewGroup) sc.getChildAt(0);
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("texts"); field.setAccessible(true);
    @SuppressWarnings("unchecked") java.util.Map<String, TextView> texts = (java.util.Map<String, TextView>) field.get(activity);
    TextView original = texts.get("freeze0");
    String originalText = original.getText().toString();
    int originalHeight = conversation.getHeight();
    sc.scrollTo(0, 200);
    app.items.get(0).text += "上方内容流式变长\n".repeat(60);
    app.items.get(24).text += "末尾更新\n".repeat(40);
    app.items.add(new ChatItem("freeze-new", "agentMessage", "离线接收的新消息"));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    capture("freeze-reading"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(sc.isFollowing()); assertEquals(200, sc.getScrollY());
    assertEquals(originalHeight, conversation.getHeight());
    assertSame(original, texts.get("freeze0"));
    assertEquals(originalText, original.getText().toString());
    assertFalse(texts.containsKey("freeze-new"));
    assertEquals(26, app.items.size()); // Transport continues receiving while the UI is frozen.
    sc.scrollTo(0, conversation.getHeight());
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("freeze-resumed"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertTrue(sc.isFollowing()); assertTrue(texts.containsKey("freeze-new"));
    assertTrue(original.getText().toString().contains("上方内容流式变长"));
    assertEquals(conversation.getHeight() - sc.getHeight(), sc.getScrollY());
  }

  @Test
  public void modelPanelSelectsSupportedEffortAndPermissionIconSharesRow() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.models = new org.json.JSONArray().put(Json.obj("model", "model-a", "displayName", "Model A",
        "defaultReasoningEffort", "medium", "supportedReasoningEfforts", new org.json.JSONArray()
        .put(Json.obj("reasoningEffort", "low")).put(Json.obj("reasoningEffort", "medium"))
        .put(Json.obj("reasoningEffort", "high"))));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    View model = find(content(), "选择模型与推理强度");
    View permission = find(content(), "权限：沿用远端权限");
    assertSame(model.getParent(), permission.getParent());
    assertNull(find(content(), "SSH 加密连接 · 在你的电脑上运行"));
    model.performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View shell = dialog.getWindow().getDecorView();
    find(shell, "选择模型：model-a").performClick();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    SeekBar slider = (SeekBar) find(shell, "推理强度滑条");
    assertEquals(2, slider.getMax());
    assertEquals(1, slider.getProgress());
    slider.setProgress(2);
    assertNotNull(find(shell, "High"));
    captureView(shell, "model-effort-picker");
    assertEquals(R.style.SheetAnimation, dialog.getWindow().getAttributes().windowAnimations);
    find(shell, "完成").performClick();
    assertEquals("model-a", app.model); assertEquals("high", app.reasoningEffort);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals("Model A · High", ((TextView) model).getText().toString());
    assertNotNull(model.getBackground());
    permission.performClick();
    dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    captureView(dialog.getWindow().getDecorView(), "permission-icons");
    find(dialog.getWindow().getDecorView(), "权限选项：完全访问").performClick();
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertEquals("bolt", ((IconView) permission).icon);
    capture("inline-model-permission");
  }

  @Test
  @Config(qualifiers = "w320dp-h720dp-xhdpi")
  public void compactComposerKeepsFullModelAndImagePreviews() throws Exception {
    verifyComposer("composer-narrow");
  }

  @Test
  @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
  public void darkComposerKeepsFullModelAndImagePreviews() throws Exception {
    verifyComposer("composer-dark");
  }

  @Test
  @Config(qualifiers = "w1280dp-h800dp-mdpi")
  public void tabletComposerKeepsFullModelAndImagePreviews() throws Exception {
    verifyComposer("composer-tablet");
  }

  private void verifyComposer(String screenshot) throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.connected = true;
    app.model = "long-model"; app.reasoningEffort = "xhigh";
    app.models = new org.json.JSONArray().put(Json.obj("model", app.model,
        "displayName", "GPT-5.3-Codex-Spark"));
    Bitmap bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.rgb(120, 180, 210));
    Paint paint = new Paint(); paint.setColor(Color.rgb(35, 110, 65));
    canvas.drawRect(0, 100, 240, 160, paint);
    paint.setColor(Color.YELLOW); canvas.drawCircle(180, 40, 22, paint);
    for (int i = 0; i < 4; i++) {
      String path = "/tmp/image-" + i + ".png";
      app.draftImages.add(path); app.draftImagePreviews.put(path, bitmap);
    }
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture(screenshot);
    TextView model = (TextView) find(content(), "选择模型与推理强度");
    View plus = find(content(), "添加图片");
    View permission = find(content(), "权限：沿用远端权限");
    View send = find(content(), "发送消息");
    assertEquals(0, plus.getLeft());
    assertTrue(plus.getRight() <= model.getLeft());
    assertTrue(model.getRight() <= permission.getLeft());
    assertTrue(permission.getRight() <= send.getLeft());
    assertTrue(send.getRight() <= ((View) send.getParent()).getWidth());
    assertEquals(0f, model.getElevation(), 0f);
    android.text.Layout layout = model.getLayout();
    assertEquals(model.length(), layout.getLineEnd(layout.getLineCount() - 1));
    for (int i = 0; i < layout.getLineCount(); i++) assertEquals(0, layout.getEllipsisCount(i));
    assertTrue(layout.getHeight() <= model.getHeight() - model.getTotalPaddingTop() - model.getTotalPaddingBottom());
    ImageView preview = (ImageView) find(content(), "待发送图片 1");
    assertNotNull(preview.getDrawable());
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertNotNull(((ImageView) find(content(), "待发送图片 1")).getDrawable());
    controller.recreate(); activity = controller.get();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertNotNull(((ImageView) find(content(), "待发送图片 1")).getDrawable());
    find(content(), "移除图片 1").performClick();
    assertEquals(3, app.draftImages.size()); assertEquals(3, app.draftImagePreviews.size());
    app.newChat();
    assertTrue(app.draftImages.isEmpty()); assertTrue(app.draftImagePreviews.isEmpty());
    app.attaching = true; app.pendingImagePreview = bitmap; app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertNotNull(((ImageView) find(content(), "待发送图片 1")).getDrawable());
    assertNotNull(find(content(), "图片上传中…"));
    assertNull(find(content(), "移除图片 1"));
    app.disconnect(); assertNull(app.pendingImagePreview);
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp-xhdpi")
  public void fontSizeChoicePersistsAndScalesNewActivity() throws Exception {
    float originalScale = activity.getResources().getConfiguration().fontScale;
    find(content(), "对话菜单").performClick();
    android.app.AlertDialog menu = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    menu.getListView().performItemClick(null, 7, 7);
    android.app.AlertDialog sizes = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    sizes.getListView().performItemClick(null, 4, 4);
    assertEquals(1.3f, activity.getSharedPreferences("display", 0).getFloat("fontScale", 0), .001f);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    activity = controller.get();
    assertEquals(originalScale * 1.3f, activity.getResources().getConfiguration().fontScale, .001f);
    capture("font-large-phone");
  }

  @Test
  public void compactToolsLiveReasoningAndSteeringControls() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.profile.host = "test-host";
    app.connected = true; app.busy = true; app.turnId = "running"; app.threadId = "chat";
    app.threadTitle = "运行中引导"; app.threadCwd = "/projects/demo";
    ChatItem reasoning = new ChatItem("r", "reasoning", "思考过程");
    reasoning.detail = "正在检查服务器返回的状态";
    app.items.add(reasoning);
    ChatItem command = new ChatItem("cmd", "commandExecution", "./gradlew test\n第二行不应撑开卡片");
    command.detail = "测试输出"; command.status = "inProgress";
    app.items.add(command); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    TextView tool = (TextView) find(content(), "›  ./gradlew test 第二行不应撑开卡片  ›");
    assertNotNull(tool); assertEquals(1, tool.getMaxLines());
    EditText input = (EditText) find(content(), "消息输入框");
    input.setText("先检查失败的测试");
    assertNotNull(find(content(), "发送引导消息"));
    assertTrue(find(content(), "发送引导消息").isEnabled());
    capture("steering-compact-permissions");
    input.setText(""); assertNotNull(find(content(), "停止生成"));
    find(content(), "›  思考过程  ›").performClick();
    android.app.Dialog details = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull(find(details.getWindow().getDecorView(), "正在检查服务器返回的状态"));
    reasoning.detail += "\n继续验证";
    app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    assertNotNull(find(details.getWindow().getDecorView(), reasoning.detail));
    captureView(details.getWindow().getDecorView(), "live-reasoning");
    details.dismiss();
    find(content(), "权限：沿用远端权限").performClick();
    android.app.Dialog permissions = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    find(permissions.getWindow().getDecorView(), "权限选项：只读").performClick();
    assertEquals(PermissionMode.READ_ONLY, app.permissionMode);
  }

  @Test
  public void conversationIdentityAndUsageAreVisible() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile();
    app.connected = true;
    app.threadId = "conversation-123";
    app.threadTitle = "接续远端对话";
    app.threadCwd = "/home/me/projects/remotesshapp";
    app.usage.update(Json.obj("rateLimits", Json.obj("primary", Json.obj("usedPercent", 25, "windowDurationMins", 300),
        "secondary", Json.obj("usedPercent", 10, "windowDurationMins", 10080))), true);
    app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertNotNull(find(content(), "项目 · /home/me/projects/remotesshapp"));
    assertNotNull(find(content(), "剩余用量 · 5小时 75% · 7天 90%"));
    capture("conversation-context-usage");
    ((View) find(content(), "接续远端对话").getParent()).performClick();
    android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(org.robolectric.Shadows.shadowOf(dialog).getMessage().toString().contains("conversation-123"));
    dialog.dismiss();
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp-night-xhdpi")
  public void connectionEditorKeepsActionsVisibleAboveKeyboard() throws Exception {
    find(content(), "连接电脑").performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    ViewGroup editor = dialog.findViewById(R.id.connection_editor);
    captureView(editor, "connection-password");
    assertEquals(
        WindowManager.LayoutParams.MATCH_PARENT, dialog.getWindow().getAttributes().height);
    find(editor, "私钥登录").performClick();
    assertFalse(inputField(editor, "密码").isShown());
    assertTrue(inputField(editor, "私钥").isShown());
    find(editor, "项目与启动设置 ⌄").performClick();
    WindowInsets insets =
        new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 48, 0, 32))
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 560))
            .setVisible(WindowInsets.Type.ime(), true)
            .build();
    editor.dispatchApplyWindowInsets(insets);
    captureView(editor, "connection-keyboard");
    View save = find(editor, "保存并连接");
    // Measure inside the editor, independent of Robolectric's synthetic WindowManager.
    android.graphics.Rect visible =
        new android.graphics.Rect(0, 0, save.getWidth(), save.getHeight());
    editor.offsetDescendantRectToMyCoords(save, visible);
    assertTrue(save.getHeight() > 0);
    assertTrue(visible.top >= 0);
    assertTrue("Button must remain above IME", visible.bottom <= editor.getHeight() - 560);
    ScrollView form = (ScrollView) find(editor, "SSH 连接表单");
    form.fullScroll(View.FOCUS_DOWN);
    assertTrue("Long form must scroll", form.getScrollY() > 0);
    inputField(editor, "主机").setText("127.0.0.1");
    inputField(editor, "用户名").setText("fixture");
    inputField(editor, "端口").setText("65536");
    save.performClick();
    assertTrue(dialog.isShowing());
    assertNotNull(find(editor, "端口必须在 1–65535 之间"));
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp-night-xhdpi")
  public void projectBrowserAndDirectoryPickerRender() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    Profile profile = new Profile();
    profile.name = "开发工作站";
    profile.cwd = "/projects/remotesshapp";
    profile.projects.add(profile.cwd);
    profile.projects.add("/projects/talk");
    app.profile = profile;
    app.vault.profiles.add(profile);
    app.connected = true;
    app.threads =
        new org.json.JSONArray()
            .put(Json.obj("id", "demo", "name", "开发极简 SSH 远程对话", "cwd", profile.cwd));
    app.changed();
    find(content(), "打开连接和聊天记录").performClick();
    android.app.Dialog browser = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View root = find(browser.getWindow().getDecorView(), "远程控制项目列表");
    assertNotNull(find(root, "remotesshapp"));
    captureView(root, "remote-projects");
    ((View) find(root, "remotesshapp").getParent()).performClick();
    assertNotNull(find(root, "聊天"));
    captureView(root, "remote-project-threads");
    browser.dismiss();
    DirectoryPicker picker = new DirectoryPicker(activity, app, true, path -> {});
    java.lang.reflect.Method display =
        DirectoryPicker.class.getDeclaredMethod("display", RemoteDirectory.class);
    display.setAccessible(true);
    java.lang.reflect.Field field = DirectoryPicker.class.getDeclaredField("dialog");
    field.setAccessible(true);
    android.app.Dialog dialog = (android.app.Dialog) field.get(picker);
    dialog.show();
    display.invoke(
        picker,
        new RemoteDirectory(
            "/projects", java.util.Arrays.asList("remotesshapp", "talk", "中文项目"), false));
    View directory = find(dialog.getWindow().getDecorView(), "远端目录选择器");
    assertNotNull(find(directory, "使用此文件夹"));
    assertNotNull(find(directory, "新建项目"));
    captureView(directory, "remote-directories");
    dialog.dismiss();
  }

  @Test
  public void errorBannerDoesNotDumpRemoteWarningIntoHome() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.error = "连接失败 · 远端握手\n\n原因：EOFException\n远端诊断输出：[UNDICI-EHPA] Warning";
    app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    TextView banner = (TextView) find(content(), "查看连接错误");
    assertFalse(banner.getText().toString().contains("UNDICI"));
    banner.performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull(find(dialog.getWindow().getDecorView(), app.error));
    assertNotNull(find(dialog.getWindow().getDecorView(), "复制诊断"));
  }

  @Test
  public void disconnectedHomeAndConnectionFormRender() throws Exception {
    assertNotNull(find(content(), "你的电脑，随时连接"));
    capture("phone-home");
    find(content(), "连接电脑").performClick();
    assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
    assertNotNull(
        find(
            org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),
            "主机"));
  }

  @Test
  public void conversationRendersStructuredItemsAndSurvivesRecreation() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    Profile p = new Profile();
    p.name = "开发电脑";
    p.host = "dev.example";
    p.user = "dev";
    app.profile = p;
    app.connected = true;
    app.threadId = "fixture-thread";
    app.items.add(new ChatItem("u1", "userMessage", "帮我检查一下这个项目，并介绍它的结构。"));
    app.items.add(new ChatItem("a1", "agentMessage", "我会先查看项目目录，再检查构建配置。"));
    ChatItem command = new ChatItem("c1", "commandExecution", "ls -la /workspace");
    command.status = "completed";
    command.detail = "app/\nbuild.gradle\nREADME.md";
    app.items.add(command);
    app.items.add(
        new ChatItem(
            "a2",
            "agentMessage",
            "## 项目概览\n"
                + "这是一个原生 Android 应用。\n\n"
                + "- **界面**：极简对话布局\n"
                + "- **连接**：SSH 加密通道\n\n"
                + "可以用以下命令构建：\n"
                + "```sh\n"
                + "./gradlew assembleDebug\n"
                + "```\n"
                + "接下来，你想检查哪一部分？"));
    app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    capture("phone-chat");
    assertNotNull(find(content(), "消息输入框"));
    EditText input = (EditText) find(content(), "消息输入框");
    input.setText("尚未发送的草稿");
    controller.recreate();
    activity = controller.get();
    assertEquals("尚未发送的草稿", ((EditText) find(content(), "消息输入框")).getText().toString());
    assertEquals(4, app.items.size());
  }

  @Test
  @Config(qualifiers = "w1024dp-h768dp-xhdpi")
  public void tabletLayoutRenders() throws Exception {
    assertNotNull(find(content(), "最近的对话"));
    capture("tablet-home");
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp-night-xhdpi")
  public void darkLayoutRenders() throws Exception {
    capture("phone-dark");
  }
  @Test public void conversationMenuDoesNotOpenNavigation() throws Exception {
    find(content(), "对话菜单").performClick();
    android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals("新对话", dialog.getListView().getAdapter().getItem(0));
    assertEquals("选择模型", dialog.getListView().getAdapter().getItem(1));
    captureView(dialog.getWindow().getDecorView(), "conversation-menu");
  }

  @Test public void projectMenuDiffersFromHostMenu() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile();
    RemoteBrowser browser = new RemoteBrowser(activity, app, false, new RemoteBrowser.Actions() {
      public void connect(Profile p) {} public void add() {} public void settings() {}
      public void directories() {} public void diagnostics() {} public void chat() {}
    });
    assertEquals("添加连接", browser.menuLabels()[0]);
    browser.openProject("/work/project");
    assertEquals("在此项目新建对话", browser.menuLabels()[0]);
    assertEquals(3, browser.menuLabels().length);
  }

  @Test public void tailscaleModePassesCredentialValidation() {
    find(content(), "连接电脑").performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View editor = dialog.findViewById(R.id.connection_editor);
    find(editor, "Tailscale SSH（免密码）").performClick();
    inputField(editor, "主机").setText("100.109.221.115");
    inputField(editor, "用户名").setText("fixture");
    assertFalse(inputField(editor, "密码").isShown());
    assertFalse(inputField(editor, "私钥").isShown());
    find(editor, "保存并连接").performClick();
    RemoteApp app = (RemoteApp) activity.getApplication();
    // Robolectric has no AndroidKeyStore; reaching storage confirms no password gate.
    assertTrue(app.error, app.error.startsWith("保存失败"));
  }
  @Test public void generatedImageRendersAndOpensSavePicker() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.profile.name = "测试主机"; app.connected = true;
    Bitmap sample = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(sample); canvas.drawColor(Color.rgb(44, 100, 165));
    Paint paint = new Paint(); paint.setColor(Color.WHITE); paint.setTextSize(35);
    canvas.drawText("Image preview fixture", 45, 160, paint);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); sample.compress(Bitmap.CompressFormat.PNG, 100, bytes); sample.recycle();
    ChatItem image = new ChatItem("image", "imageGeneration", "生成图片");
    image.images.add("data:image/png;base64," + android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP));
    app.items.add(image);
    app.items.add(new ChatItem("reply", "agentMessage", "[下载报告](sandbox:/mnt/data/report.pdf)"));
    app.changed();
    for (int i = 0; i < 150 && find(content(), "保存图片") == null; i++) {
      Thread.sleep(10); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
    }
    assertNotNull(find(content(), "保存图片"));
    ImageView preview = (ImageView) find(content(), "消息图片，点击查看大图");
    assertNotNull(preview.getDrawable());
    capture("image-and-download");
    find(content(), "保存图片").performClick();
    org.robolectric.shadows.ShadowActivity.IntentForResult request = shadowOf(activity).getNextStartedActivityForResult();
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, request.intent.getAction());
    assertEquals(44, request.requestCode);
  }
}
