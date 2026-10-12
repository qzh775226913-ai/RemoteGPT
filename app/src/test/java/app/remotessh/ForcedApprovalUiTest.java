package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.Duration;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ForcedApprovalUiTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private RemoteApp app;
  private RpcClient rpc;
  private ByteArrayOutputStream sent;

  @Before public void setup() throws Exception {
    controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get();
    app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.profile.name = "测试主机";
    app.connected = true; app.threadId = "A"; app.threadTitle = "测试对话";
    app.busy = true; app.turnId = "turn-A";
    sent = new ByteArrayOutputStream();
    rpc = new RpcClient(new ByteArrayInputStream(new byte[0]), sent, new RpcClient.Events() {
      public void onEvent(JSONObject value) {}
      public void onClosed(Exception error) {}
    });
    Field field = RemoteApp.class.getDeclaredField("rpc"); field.setAccessible(true); field.set(app, rpc);
  }

  @After public void close() {
    if (!activity.isDestroyed()) controller.pause().stop().destroy();
    app.disconnect(); rpc.close();
  }

  private View find(View root, String name) {
    if (root instanceof TextView && name.contentEquals(((TextView) root).getText())) return root;
    if (name.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
    if (root instanceof ViewGroup) for (int n = 0; n < ((ViewGroup) root).getChildCount(); n++) {
      View result = find(((ViewGroup) root).getChildAt(n), name); if (result != null) return result;
    }
    return null;
  }
  private void invoke(String name) throws Exception {
    Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
  }
  private AlertDialog approval() throws Exception {
    app.approvals.add(Json.obj("id", "permission", "method", "item/commandExecution/requestApproval",
        "params", Json.obj("threadId", "A", "turnId", "turn-A", "command", "git status", "cwd", "/fixture/project")));
    app.changed(); invoke("showPending");
    return (AlertDialog) ShadowDialog.getLatestDialog();
  }
  private AlertDialog warning(AlertDialog approval) {
    approval.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
    return (AlertDialog) ShadowDialog.getLatestDialog();
  }
  private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150)); }

  private void capture(View view, String name) throws Exception {
    int width = activity.getResources().getDisplayMetrics().widthPixels;
    int height = activity.getResources().getDisplayMetrics().heightPixels;
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.WHITE); view.draw(new Canvas(bitmap));
    File folder = new File("build/screenshots"); assertTrue(folder.isDirectory() || folder.mkdirs());
    try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
    bitmap.recycle();
  }

  @Test public void forceButtonIsRedAndCancellationKeepsManualApproval() throws Exception {
    AlertDialog approval = approval();
    Button force = approval.getButton(AlertDialog.BUTTON_NEUTRAL);
    assertEquals("全部允许(强制)", force.getText().toString());
    int red = force.getCurrentTextColor(); assertTrue(Color.red(red) > Color.green(red));
    capture(approval.getWindow().getDecorView(), "force-approval-prompt");
    AlertDialog warning = warning(approval);
    assertNotNull(find(warning.getWindow().getDecorView(), PermissionPicker.WARNING));
    assertFalse(app.forcedApprovals.active()); assertEquals(0, sent.size());
    capture(warning.getWindow().getDecorView(), "force-approval-warning");
    warning.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
    assertTrue(approval.isShowing()); assertFalse(app.forcedApprovals.active());
    assertEquals(0, sent.size());
  }

  @Test public void confirmationHidesPermissionPromptsAndShowsMatchingRedBoltInMenu() throws Exception {
    AlertDialog approval = approval(); AlertDialog warning = warning(approval);
    warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
    assertTrue(app.forcedApprovals.active()); assertFalse(approval.isShowing());
    IconView indicator = (IconView) find(activity.findViewById(android.R.id.content), "权限：强制允许");
    assertNotNull(indicator); assertEquals("bolt", indicator.icon);
    assertTrue(Color.red(indicator.color) > Color.green(indicator.color));
    indicator.performClick();
    Dialog menu = ShadowDialog.getLatestDialog();
    View row = find(menu.getWindow().getDecorView(), "权限选项：强制允许");
    assertNotNull(row);
    IconView bolt = (IconView) ((ViewGroup) row).getChildAt(0);
    TextView label = (TextView) ((ViewGroup) row).getChildAt(1);
    assertEquals("bolt", bolt.icon); assertEquals(indicator.color, bolt.color);
    assertEquals(indicator.color, label.getCurrentTextColor());
    capture(menu.getWindow().getDecorView(), "force-permission-menu");
    row.performClick(); idle();
    assertFalse(app.forcedApprovals.active());
    assertNotNull(find(activity.findViewById(android.R.id.content), "权限：完全访问"));
  }

  @Test public void structuredQuestionsStillOpenNormallyWithoutForceButton() throws Exception {
    assertTrue(app.forcedApprovals.enable("A"));
    JSONObject question = Json.obj("id", "question", "method", "item/tool/requestUserInput", "params",
        Json.obj("threadId", "A", "questions", new JSONArray().put(Json.obj("id", "plan", "header", "方案", "question", "选择哪种方案？",
            "options", new JSONArray().put(Json.obj("label", "方案 A", "description", "第一个方案")).put(Json.obj("label", "方案 B", "description", "第二个方案"))))));
    app.approvals.add(question); app.changed(); invoke("showPending");
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    assertNotNull(find(dialog.getWindow().getDecorView(), "选择哪种方案？"));
    assertNull(find(dialog.getWindow().getDecorView(), "全部允许(强制)"));
    assertSame(question, app.visibleApproval()); assertEquals(0, sent.size());
    assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
  }

  @Test public void staleConfirmationCannotAuthorizeAfterDisconnect() throws Exception {
    AlertDialog warning = warning(approval());
    app.disconnect(); idle();
    assertFalse(warning.isShowing());
    warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
    assertFalse(app.forcedApprovalState.enabled);
    assertEquals(0, sent.size());
  }

  @Test public void activityDestructionReleasesPermissionAndWarningListeners() throws Exception {
    int before = app.stateListeners.size();
    invoke("choosePermission"); assertEquals(before + 1, app.stateListeners.size());
    Dialog menu = ShadowDialog.getLatestDialog();
    find(menu.getWindow().getDecorView(), "权限选项：强制允许").performClick();
    AlertDialog warning = (AlertDialog) ShadowDialog.getLatestDialog();
    assertTrue(warning.isShowing()); assertEquals(before + 1, app.stateListeners.size());
    controller.pause().stop().destroy();
    assertFalse(warning.isShowing()); assertEquals(before, app.stateListeners.size());
  }

  @Test @Config(sdk = 35, qualifiers = "w320dp-h640dp-xhdpi")
  public void narrowScreenKeepsAllApprovalButtonsReadable() throws Exception {
    AlertDialog approval = approval();
    capture(approval.getWindow().getDecorView(), "force-approval-narrow");
    for (int button : new int[] {AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
      Button view = approval.getButton(button);
      assertTrue(view.getWidth() > 0);
      for (int line = 0; line < view.getLayout().getLineCount(); line++) assertEquals(0, view.getLayout().getEllipsisCount(line));
    }
  }

  @Test @Config(sdk = 35, qualifiers = "w411dp-h891dp-night-xhdpi")
  public void darkThemeUsesRedBoltAndPreservesQuestions() throws Exception {
    AlertDialog warning = warning(approval());
    warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
    IconView indicator = (IconView) find(activity.findViewById(android.R.id.content), "权限：强制允许");
    assertEquals("bolt", indicator.icon);
    assertTrue(Color.red(indicator.color) > Color.green(indicator.color));
    indicator.performClick();
    capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(), "force-permission-menu-dark");
  }
}
