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
    captureLaidOutView(v, name);
  }

  private void captureLaidOutView(View v, String name) throws Exception {
    Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
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

  private void documentImagePickerAvailable() {
    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
        .setType("image/*").addCategory(android.content.Intent.CATEGORY_OPENABLE);
    android.content.pm.ResolveInfo info = new android.content.pm.ResolveInfo();
    info.activityInfo = new android.content.pm.ActivityInfo(); info.activityInfo.packageName = "test.files";
    info.activityInfo.name = "test.files.Picker"; info.activityInfo.exported = true;
    shadowOf(activity.getPackageManager()).addResolveInfoForIntent(intent, info);
  }

  @Test public void imageSourcesOfferGalleryAppsAndMultiSelectFallback() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    documentImagePickerAvailable();
    find(content(), "添加图片").performClick();
    View sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
    assertNotNull(find(sheet, "手机相册")); assertNotNull(find(sheet, "其他相册 / 图片 App"));
    assertNotNull(find(sheet, "系统选择器")); captureView(sheet, "image-source-picker");
    find(sheet, "手机相册").performClick();
    org.robolectric.shadows.ShadowActivity.IntentForResult started = shadowOf(activity).getNextStartedActivityForResult();
    assertEquals(ImagePicker.REQUEST, started.requestCode);
    assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, started.intent.getAction());
    assertTrue(started.intent.getBooleanExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false));
    activity.onActivityResult(ImagePicker.REQUEST, android.app.Activity.RESULT_CANCELED, null);
    assertTrue(app.draftImages.isEmpty()); assertFalse(app.attaching);
    find(content(), "添加图片").performClick();
    assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
  }

  @Test public void multiImageResultSurvivesRotationAndRejectsChangedConversation() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile(); app.threadId = "original";
    app.draftImages.addAll(java.util.Arrays.asList("/1.png", "/2.png", "/3.png"));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); documentImagePickerAvailable();
    find(content(), "添加图片").performClick();
    find(org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(), "系统选择器").performClick();
    controller.recreate(); activity = controller.get();
    android.content.ClipData clip = android.content.ClipData.newUri(app.getContentResolver(), "images", android.net.Uri.parse("content://album/1"));
    clip.addItem(new android.content.ClipData.Item(android.net.Uri.parse("content://album/2")));
    android.content.Intent multiple = new android.content.Intent(); multiple.setClipData(clip);
    activity.onActivityResult(ImagePicker.REQUEST, android.app.Activity.RESULT_OK, multiple);
    assertTrue("Unexpected result after rotation: " + app.error + ", thread=" + app.threadId + ", images=" + app.draftImages.size(), app.error.contains("还能添加 1 张"));
    assertEquals(3, app.draftImages.size()); assertFalse(app.attaching);
    app.error = ""; app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    documentImagePickerAvailable(); find(content(), "添加图片").performClick();
    find(org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(), "系统选择器").performClick();
    app.threadId = "another-conversation";
    activity.onActivityResult(ImagePicker.REQUEST, android.app.Activity.RESULT_OK,
        new android.content.Intent().setData(android.net.Uri.parse("content://album/single")));
    assertEquals("", app.error); assertEquals(3, app.draftImages.size()); assertFalse(app.attaching);
  }

  @Test public void browserShowsOnlySelectedHostAndConnectsOnChipTap() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.vault.profiles.clear();
    for (int i=0;i<9;i++) {
      Profile p=new Profile(); p.name="电脑 " + i; p.projects.add("/projects/host-"+i);
      app.vault.profiles.add(p);
    }
    app.profile=app.vault.profiles.get(5); app.connected=true;
    final Profile[] clicked={null};
    RemoteBrowser browser=new RemoteBrowser(activity,app,false,new RemoteBrowser.Actions(){
      public void connect(Profile p){clicked[0]=p;}
      public void add(){} public void settings(){} public void directories(){} public void diagnostics(){} public void chat(){}
    });
    browser.show();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View panel=dialog.getWindow().getDecorView();
    captureView(panel,"selected-host-browser");
    shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNull(find(panel,"全部"));
    assertNotNull(find(panel,"host-5")); assertNull(find(panel,"host-0"));
    View selected=find(panel,"● 电脑 5"); assertNotNull(selected);
    HorizontalScrollView hostScroll=(HorizontalScrollView) selected.getParent().getParent();
    assertTrue(hostScroll.getScrollX()>0);
    captureView(panel,"selected-host-browser");
    find(panel,"○ 电脑 2").performClick();
    assertSame(app.vault.profiles.get(2),clicked[0]);
    browser.dismiss();
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
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("messageBodies"); field.setAccessible(true);
    @SuppressWarnings("unchecked") java.util.Map<String, MessageBodyView> bodies = (java.util.Map<String, MessageBodyView>) field.get(activity);
    TextView original = (TextView)bodies.get("freeze0").getChildAt(0);
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
    assertSame(original, bodies.get("freeze0").getChildAt(0));
    assertEquals(originalText, original.getText().toString());
    assertFalse(bodies.containsKey("freeze-new"));
    assertEquals(26, app.items.size()); // Transport continues receiving while the UI is frozen.
    sc.scrollTo(0, conversation.getHeight());
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    capture("freeze-resumed"); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertTrue(sc.isFollowing()); assertTrue(bodies.containsKey("freeze-new"));
    assertTrue(original.getText().toString().contains("上方内容流式变长"));
    assertEquals(conversation.getHeight() - sc.getHeight(), sc.getScrollY());
  }

  @Test public void browserStatusShowsVersionsAndOffersManualUpdateEntry() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.profile.name = "开发电脑"; app.vault.profiles.add(app.profile);
    app.remoteVersion.running = "0.156.1";
    app.remoteVersion.accept(Json.obj("installed", "0.156.1", "latest", "0.160.0", "manager", "npm", "path", "/codex"));
    final boolean[] clicked = {false};
    RemoteBrowser browser = new RemoteBrowser(activity, app, false, new RemoteBrowser.Actions() {
      public void connect(Profile p) {} public void add() {} public void settings() {}
      public void directories() {} public void diagnostics() {} public void chat() {}
      public void version() { clicked[0] = true; }
    });
    browser.show();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View shell = dialog.getWindow().getDecorView();
    TextView status = (TextView) find(shell, "远端版本与更新");
    assertTrue(status.getText().toString().contains("已连接 · Codex 0.156.1"));
    assertTrue(status.getText().toString().contains("待更新（现版本 0.156.1 → 最新版 0.160.0）"));
    status.performClick(); assertTrue(clicked[0]);
    captureView(shell, "remote-version-browser");
    app.remoteVersion.updating = true; browser.refresh();
    assertTrue(status.getText().toString().contains("正在更新至 0.160.0"));
    browser.dismiss();
  }

  @Test public void outdatedRemotePromptsOnceAndDecliningKeepsConnection() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.remoteVersion.running = "0.100.0";
    app.remoteVersion.accept(Json.obj("installed", "0.100.0", "latest", "0.101.0", "manager", "npm", "path", "/codex"));
    app.remoteVersion.prompt = true; app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(dialog.isShowing());
    assertTrue(shadowOf(dialog).getMessage().toString().contains("0.101.0"));
    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
    assertTrue(app.connected); assertFalse(app.remoteVersion.updating); assertFalse(app.remoteVersion.prompt);
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(dialog.isShowing());
    java.lang.reflect.Method show = MainActivity.class.getDeclaredMethod("showRemoteVersion"); show.setAccessible(true); show.invoke(activity);
    android.app.AlertDialog reopened = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(reopened.isShowing());
    captureView(reopened.getWindow().getDecorView(), "remote-version-confirmation");
  }

  @Test public void versionDialogCannotApplyToAnotherConnection() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.remoteVersion.running = "0.100.0";
    app.remoteVersion.accept(Json.obj("installed", "0.100.0", "latest", "0.101.0", "manager", "npm", "path", "/codex"));
    java.lang.reflect.Method show = MainActivity.class.getDeclaredMethod("showRemoteVersion"); show.setAccessible(true); show.invoke(activity);
    android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    RemoteVersion previous = app.remoteVersion;
    app.remoteVersion = new RemoteVersion(); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(dialog.isShowing());
    app.updateRemoteVersion(previous, "0.101.0");
    assertFalse(app.remoteVersion.updating);
  }

  @Test public void installedUpdateAlwaysOffersAppRestartWithDestructiveConfirmation() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.profile.persistent = true;
    app.remoteVersion.running = "0.156.1";
    app.remoteVersion.accept(Json.obj("installed", "0.160.0", "latest", "0.160.0",
        "manager", "npm", "path", "/codex"));
    app.remoteVersion.restartSupported = false;
    java.lang.reflect.Method show = MainActivity.class.getDeclaredMethod("showRemoteVersion");
    show.setAccessible(true); show.invoke(activity);
    android.app.AlertDialog version = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull(version.getButton(android.content.DialogInterface.BUTTON_POSITIVE));
    assertEquals("重启并启用新版", version.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getText().toString());
    assertFalse(shadowOf(version).getMessage().toString().contains("于远端重启"));
    version.dismiss();
    java.lang.reflect.Method confirmMethod = MainActivity.class.getDeclaredMethod("confirmRemoteRestart", RemoteVersion.class);
    confirmMethod.setAccessible(true); confirmMethod.invoke(activity, app.remoteVersion);
    android.app.AlertDialog confirm = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertEquals("停止任务并重启？", shadowOf(confirm).getTitle().toString());
    String warning = shadowOf(confirm).getMessage().toString();
    assertTrue(warning.contains("所有进行中对话"));
    assertTrue(warning.contains("待处理审批"));
    assertEquals("停止任务并重启", confirm.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getText().toString());
    captureView(confirm.getWindow().getDecorView(), "remote-restart-confirmation");
    confirm.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
    assertTrue(app.connected);
  }

  @Test public void modelPanelUpdatesWhileOpenAndKeepsRemoteDefault() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.models = new org.json.JSONArray(); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(), "选择模型与推理强度").performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View shell = dialog.getWindow().getDecorView();
    app.models = new org.json.JSONArray().put(Json.obj("model", "gpt-6.1-sol", "displayName", "GPT-6.1 Sol",
        "isDefault", true, "defaultReasoningEffort", "medium", "supportedReasoningEfforts",
        new org.json.JSONArray().put("low").put("medium").put("future")));
    app.changed();
    assertNotNull(find(shell, "选择模型：gpt-6.1-sol"));
    assertNotNull(find(shell, "刷新模型"));
    find(shell, "完成").performClick();
    assertEquals("", app.model); assertEquals("", app.reasoningEffort);
  }

  @Test public void speedPickerUsesRemoteDetailsAndRevokesUnsupportedModelChoice() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.models = new org.json.JSONArray().put(Json.obj("model", "fast-model", "displayName", "Fast model", "serviceTiers",
        new org.json.JSONArray().put(Json.obj("id", "priority", "name", "Fast", "description", "Server speed and usage details"))))
        .put(Json.obj("model", "ordinary-model", "displayName", "Ordinary model", "serviceTiers", new org.json.JSONArray()));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(), "选择模型与推理强度").performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog(); View shell = dialog.getWindow().getDecorView();
    find(shell, "选择模型：fast-model").performClick();
    find(shell, "选择运行速度：priority").performClick();
    assertNotNull(find(shell, "Server speed and usage details"));
    captureView(shell, "model-speed-picker");
    find(shell, "完成").performClick();
    assertEquals("priority", app.serviceTier);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertTrue(((TextView) find(content(), "选择模型与推理强度")).getText().toString().contains("快速"));
    find(content(), "选择模型与推理强度").performClick();
    shell = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
    find(shell, "选择模型：ordinary-model").performClick();
    assertNull(find(shell, "选择运行速度：priority"));
    find(shell, "完成").performClick(); assertEquals("", app.serviceTier);
  }

  @Test @Config(qualifiers="w360dp-h640dp-xhdpi")
  public void speedPickerScrollsOnSmallPhoneAndHonorsRemoteFeatureGate() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.model = "gated"; app.modelSettings.fastEnabled = false;
    app.models = new org.json.JSONArray().put(Json.obj("model", "gated", "serviceTiers",
        new org.json.JSONArray().put(Json.obj("id", "priority", "name", "Fast"))));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(), "选择模型与推理强度").performClick();
    View shell = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
    assertNull(find(shell, "选择运行速度：priority"));
    assertNotNull(find(shell, "选择运行速度：default"));
    assertNotNull(find(shell, "完成")); captureView(shell, "model-speed-small-phone");
    android.graphics.Rect visible = new android.graphics.Rect();
    find(shell, "完成").getDrawingRect(visible);
    ((ViewGroup) shell).offsetDescendantRectToMyCoords(find(shell, "完成"), visible);
    assertTrue(visible.height() > 0); assertTrue(visible.top >= 0);
    assertTrue(visible.bottom <= activity.getResources().getDisplayMetrics().heightPixels);
  }

  @Test @Config(qualifiers="w360dp-h640dp-night-xhdpi")
  public void modelListTouchScrollReachesLastModelWithoutMovingSettings() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication(); app.connected = true; app.profile = new Profile();
    app.model = "model-0"; app.modelsStatus = "已同步远端模型";
    app.models = new org.json.JSONArray();
    for (int i = 0; i < 16; i++) app.models.put(Json.obj("model", "model-" + i, "displayName", "Model " + i,
        "defaultReasoningEffort", "high", "supportedReasoningEfforts", new org.json.JSONArray().put("low").put("medium").put("high")));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(), "选择模型与推理强度").performClick();
    View shell = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
    captureView(shell, "model-scroll-before");
    ScrollView models = (ScrollView) find(shell, "选择模型：model-0").getParent().getParent();
    ViewParent parent = models.getParent();
    while (!(parent instanceof ScrollView)) parent = parent.getParent();
    ScrollView settings = (ScrollView) parent;
    int originalSettingsY = settings.getScrollY();
    int[] settingsLocation = new int[2], modelsLocation = new int[2];
    settings.getLocationOnScreen(settingsLocation); models.getLocationOnScreen(modelsLocation);
    float x = modelsLocation[0] - settingsLocation[0] + models.getWidth() / 2f;
    float startY = modelsLocation[1] - settingsLocation[1] + models.getHeight() - 24;
    float endY = modelsLocation[1] - settingsLocation[1] + 24;
    for (int swipe = 0; swipe < 12 && models.canScrollVertically(1); swipe++) {
      long start = android.os.SystemClock.uptimeMillis();
      dispatchTouch(settings, start, start, MotionEvent.ACTION_DOWN, x, startY);
      for (int step = 1; step <= 10; step++)
        dispatchTouch(settings, start, start + step * 20, MotionEvent.ACTION_MOVE, x, startY + (endY - startY) * step / 10);
      dispatchTouch(settings, start, start + 220, MotionEvent.ACTION_UP, x, endY);
      shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    }
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    models.computeScroll();
    assertTrue("Touch swipes must scroll the model list", models.getScrollY() > 0);
    assertFalse("The last model must be reachable", models.canScrollVertically(1));
    assertEquals("Model gestures must not scroll the settings container", originalSettingsY, settings.getScrollY());
    View last = find(shell, "选择模型：model-15");
    Rect lastBounds = new Rect(); last.getDrawingRect(lastBounds);
    models.offsetDescendantRectToMyCoords(last, lastBounds);
    lastBounds.offset(0, -models.getScrollY());
    assertTrue(lastBounds.top >= 0); assertTrue(lastBounds.bottom <= models.getHeight());
    captureLaidOutView(shell, "model-scroll-last");
    long tap = android.os.SystemClock.uptimeMillis();
    int[] lastLocation = new int[2]; last.getLocationOnScreen(lastLocation);
    settings.getLocationOnScreen(settingsLocation);
    float lastY = lastLocation[1] - settingsLocation[1] + last.getHeight() / 2f;
    dispatchTouch(settings, tap, tap, MotionEvent.ACTION_DOWN, x, lastY);
    dispatchTouch(settings, tap, tap + 40, MotionEvent.ACTION_UP, x, lastY);
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    // A new gesture outside the model list must still scroll the remaining settings.
    long start = android.os.SystemClock.uptimeMillis();
    dispatchTouch(settings, start, start, MotionEvent.ACTION_DOWN, x, settings.getHeight() - 12);
    for (int step = 1; step <= 8; step++)
      dispatchTouch(settings, start, start + step * 20, MotionEvent.ACTION_MOVE, x, settings.getHeight() - 12 - step * 20);
    dispatchTouch(settings, start, start + 180, MotionEvent.ACTION_UP, x, settings.getHeight() - 172);
    assertTrue("A gesture outside the list must scroll the settings", settings.getScrollY() > originalSettingsY);
    View apply = find(shell, "完成");
    Rect applyBounds = new Rect(); apply.getDrawingRect(applyBounds);
    ((ViewGroup) shell).offsetDescendantRectToMyCoords(apply, applyBounds);
    assertTrue("The fixed footer must keep its height: " + applyBounds, applyBounds.height() > 0);
    assertTrue("The fixed footer must stay visible: " + applyBounds, applyBounds.top >= 0 && applyBounds.bottom <= shell.getHeight());
    captureLaidOutView(shell, "model-settings-scrolled");
    find(shell, "完成").performClick(); assertEquals("model-15", app.model);
  }

  private void dispatchTouch(View target, long downTime, long time, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(downTime, time, action, x, y, 0);
    try { target.dispatchTouchEvent(event); } finally { event.recycle(); }
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
    assertTrue(model.getForeground() instanceof android.graphics.drawable.RippleDrawable);
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

  @Test public void emptyReasoningFinishesWithoutAnEmptyCardOrStaleWaitingDialog() throws Exception {
    RemoteApp app = (RemoteApp) activity.getApplication();
    app.profile = new Profile(); app.connected = true; app.threadId = "chat";
    ChatItem reasoning = new ChatItem("r", "reasoning", "思考过程");
    reasoning.status = "inProgress"; app.items.add(reasoning); app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    View card = find(content(), "›  思考过程  ›");
    assertEquals(View.VISIBLE, ((View) card.getParent()).getVisibility());
    card.performClick();
    android.app.Dialog details = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull(find(details.getWindow().getDecorView(), "正在等待思考摘要…"));
    reasoning.status = "completed"; app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    assertEquals(View.GONE, ((View) card.getParent()).getVisibility());
    assertNull(find(details.getWindow().getDecorView(), "正在等待思考摘要…"));
    assertNotNull(find(details.getWindow().getDecorView(), "远端未提供此思考项的摘要。部分思考项可能没有可显示的摘要。"));
    reasoning.detail = "远端随后返回摘要"; app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    assertEquals(View.VISIBLE, ((View) card.getParent()).getVisibility());
    assertNotNull(find(details.getWindow().getDecorView(), reasoning.detail));
    details.dismiss();
  }

  @Test public void copyableBlocksAndReadOnlyButtons() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.readOnly=true;app.threadId="chat";
    app.items.add(new ChatItem("a","agentMessage","运行以下命令：\n```sh\ngit status\necho ready\n```\n然后查看结果。"));app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(),"复制内容块 1").performClick();
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    assertEquals("git status\necho ready\n",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    View refresh=find(content(),"刷新"), resume=find(content(),"接续"), reconnect=find(content(),"重连并接续");
    assertTrue(((LinearLayout.LayoutParams)refresh.getLayoutParams()).getMarginEnd()>0);
    assertTrue(((LinearLayout.LayoutParams)resume.getLayoutParams()).getMarginEnd()>0);
    assertTrue(((LinearLayout.LayoutParams)reconnect.getLayoutParams()).topMargin>0);
    assertTrue(((View)refresh.getParent().getParent()).getPaddingLeft()>0);
    capture("copy-blocks-readonly-spaced");
  }

  @Test public void questionsRequireExplicitAnswerAndSurviveRotation() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.busy=true;app.threadId="chat";
    org.json.JSONObject req=Json.obj("id",17,"method","item/tool/requestUserInput","params",Json.obj("threadId","chat","isBlocking",true,"questions",new org.json.JSONArray().put(Json.obj("id","q","question","选择方案","options",new org.json.JSONArray().put(Json.obj("label","方案 A","description","优先兼容"))))));
    app.approvals.add(req);app.changed();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertFalse(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());assertSame(req,app.visibleApproval());
    find(dialog.getWindow().getDecorView(),"方案 A").performClick();assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());assertSame(req,app.visibleApproval());
    captureView(dialog.getWindow().getDecorView(),"question-choices-waiting");
    controller.recreate();activity=controller.get();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(((RadioButton)find(dialog.getWindow().getDecorView(),"方案 A")).isChecked());
    assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());assertSame(req,app.visibleApproval());
  }

  @Test public void savedAsyncQuestionShowsChoicesAndKeepsPermission() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.threadId="chat";
    app.journal.apply(ConversationJournalTest.fixture("call"),"chat");app.syncJournalQuestions();app.effectivePermissionMode=PermissionMode.FULL;app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(dialog.isShowing());assertNotNull(find(dialog.getWindow().getDecorView(),"A"));assertNotNull(find(dialog.getWindow().getDecorView(),"B"));
    assertFalse(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());
    assertNotNull(find(content(),"权限：完全访问"));assertEquals(PermissionMode.INHERIT,app.permissionMode);
    captureView(dialog.getWindow().getDecorView(),"async-question-recovered");
    app.cancelQuestion(app.visibleApproval());app.changed();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertFalse(dialog.isShowing());assertNull(app.visibleApproval());assertEquals(PermissionMode.FULL,app.displayedPermission());
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
  @Config(qualifiers = "w320dp-h720dp-night-xhdpi")
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
    View create = find(directory, "新建项目"), choose = find(directory, "使用此文件夹");
    View up = find(directory, "上一级"), enter = find(directory, "输入路径");
    assertSame(create.getParent(), choose.getParent());
    assertSame(up.getParent(), enter.getParent());
    assertNotSame(up.getParent(), create.getParent());
    assertEquals(2, ((ViewGroup) up.getParent()).getChildCount());
    assertEquals(2, ((ViewGroup) create.getParent()).getChildCount());
    assertEquals(create.getTop(), choose.getTop());
    for (View button : new View[] {create, choose, up, enter}) {
      assertEquals(1, ((TextView) button).getLineCount());
      assertTrue(button.getWidth() > 0);
    }
    create.performClick();
    android.app.AlertDialog prompt = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertEquals("新建项目", shadowOf(prompt).getTitle().toString());
    prompt.dismiss();
    dialog.dismiss();
  }

  @Test public void streamingUpdatesDoNotRecreateBrowserConversationRows() {
    RemoteApp app = (RemoteApp) activity.getApplication();
    Profile profile = new Profile(); profile.name = "工作站";
    app.profile = profile; app.vault.profiles.add(profile); app.connected = true;
    app.threadId = "running"; app.busy = true; app.remoteRunning = true; app.turnId = "turn";
    app.threads = new org.json.JSONArray().put(Json.obj("id", "running", "name", "正在回复的对话",
        "status", Json.obj("type", "active")));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    find(content(), "打开连接和聊天记录").performClick();
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View root = find(dialog.getWindow().getDecorView(), "远程控制项目列表");
    View original = (View) find(root, "正在回复的对话").getParent();
    app.items.add(new ChatItem("a", "agentMessage", "第一个 token"));
    app.changed(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertSame(original, (View) find(root, "正在回复的对话").getParent());
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
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));capture("image-placeholder");
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

  @Test public void conversationShowsTypedQuestionHistoryAndCopyActions() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.threadId="chat";
    org.json.JSONObject req=InteractionHistoryTest.question("fixture","t1");app.interactions.question(req);
    app.interactions.answered(req,Json.obj("answers",Json.obj("q0",Json.obj("answers",new org.json.JSONArray().put("B")))));
    ChatItem progress=InteractionHistoryTest.message("progress","agentMessage","t1","正在检查项目结构。"),result=InteractionHistoryTest.message("result","agentMessage","t1","输出结果：\n```text\n版本 1.0.35\n```" );
    progress.phase="commentary";result.phase="final_answer";
    app.items.add(InteractionHistoryTest.message("u","userMessage","t1","请检查项目"));app.items.add(progress);
    ChatItem reasoning=InteractionHistoryTest.message("reason","reasoning","t1","思考过程");reasoning.detail="优先检查关键流程";reasoning.status="completed";app.items.add(reasoning);
    app.items.add(result);app.items.add(InteractionHistoryTest.message("a","userMessage","t2","回答上一轮的问题：\n选择方案\nB\n\n"));app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(150));capture("typed-conversation-history-"+((activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"dark":"light"));
    assertNotNull(find(content(),"进展"));assertNotNull(find(content(),"回复"));assertNotNull(find(content(),"交互提问 · 已回答"));assertNotNull(find(content(),"你的回答"));
    assertNotNull(find(content(),"✓  B"));assertNotNull(find(content(),"✓  思考过程  ›"));
    find(content(),"复制问答回答").performClick();
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
    assertEquals("选择方案\n回答：B",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    find(content(),"复制内容块 1").performClick();assertEquals("版本 1.0.35\n",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    controller.recreate();activity=controller.get();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(150));assertNotNull(find(content(),"你的回答"));
  }

  @Test @Config(qualifiers="w411dp-h891dp-night-xhdpi")
  public void typedQuestionHistoryAlsoRendersInDarkTheme() throws Exception {conversationShowsTypedQuestionHistoryAndCopyActions();}

  @Test public void longImageHistoryRendersInPagesAndLoadsOnlyVisibleThumbnails() throws Exception {
    RemoteApp app=(RemoteApp)activity.getApplication();app.profile=new Profile();app.connected=true;app.threadId="photos";
    Bitmap sample=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();sample.compress(Bitmap.CompressFormat.PNG,100,bytes);sample.recycle();
    String source="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
    for(int i=0;i<400;i++){ChatItem m=new ChatItem("photo"+i,"userMessage","图片消息 "+i);m.images.add(source);app.items.add(m);}app.changed();
    shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));capture("paged-photo-history");
    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("currentItems");field.setAccessible(true);
    assertEquals(60,((java.util.Map<?,?>)field.get(activity)).size());assertEquals(400,app.items.size());
    field=MainActivity.class.getDeclaredField("mediaTiles");field.setAccessible(true);java.util.List<?> tiles=(java.util.List<?>)field.get(activity);
    int loaded=0;for(int i=0;i<100 && loaded==0;i++){Thread.sleep(10);shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(30));loaded=0;for(Object tile:tiles){java.lang.reflect.Field f=tile.getClass().getDeclaredField("loaded");f.setAccessible(true);if(f.getBoolean(tile))loaded++;}}
    assertTrue("Visible thumbnails must load",loaded>0);assertTrue("Offscreen history must not decode eagerly",loaded<6);
    ConversationScrollView scroll=(ConversationScrollView)find(content(),"对话消息");scroll.scrollTo(0,200);
    int before=scroll.getScrollY();assertFalse(scroll.isFollowing());find(content(),"加载更早的消息").performClick();
    capture("paged-photo-history-older");shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    field=MainActivity.class.getDeclaredField("currentItems");field.setAccessible(true);assertEquals(120,((java.util.Map<?,?>)field.get(activity)).size());
    assertFalse("Loading history must keep reading mode",scroll.isFollowing());assertTrue("Prepending keeps the old content on screen",scroll.getScrollY()>before);
    assertEquals(400,app.items.size());
  }
}
