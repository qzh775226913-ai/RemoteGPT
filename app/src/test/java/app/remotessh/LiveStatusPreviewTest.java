package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Content-layout preview built from the actual notification payload; not an OEM screenshot. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h1200dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveStatusPreviewTest {
  private void label(RemoteApp app, LinearLayout root, String title, int size) {
    TextView t = new TextView(app); t.setText(title); t.setTextSize(size);
    t.setTextColor(Color.rgb(32, 36, 44)); t.setPadding(24, 28, 24, 16); root.addView(t);
  }
  private void card(RemoteApp app, LinearLayout root) {
    app.liveStatus.update();
    Notification n = Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveStatusNotifications.ID);
    assertNotNull(n);
    LinearLayout card = new LinearLayout(app); card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(24, 12, 24, 24);
    android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
    background.setColor(Color.WHITE); background.setCornerRadius(28); card.setBackground(background);
    TextView title = new TextView(app); title.setText(n.extras.getCharSequence(Notification.EXTRA_TITLE));
    title.setTextSize(18); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setTextColor(Color.rgb(32,36,44)); title.setPadding(24,8,24,8); card.addView(title);
    LiveProgressTextView body = new LiveProgressTextView(app);
    body.setProgress(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString());
    body.setTextColor(Color.rgb(48, 53, 63)); body.setLineSpacing(6, 1);
    body.setPadding(24, 4, 24, 16); card.addView(body);
    if (n.actions != null && n.actions.length > 0) {
      LinearLayout actions = new LinearLayout(app);
      for (Notification.Action action : n.actions) {
        TextView button = new TextView(app); button.setText(action.title); button.setTextSize(15);
        button.setTextColor(Color.rgb(42, 79, 142)); button.setGravity(Gravity.CENTER);
        button.setPadding(8, 20, 8, 4);
        actions.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
      }
      card.addView(actions);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(16, 0, 16, 8); root.addView(card, lp);
  }
  @Test public void renderExpandedProgressApprovalAndQuestion() throws Exception {
    RemoteApp app = (RemoteApp) RuntimeEnvironment.getApplication();
    app.profile = new Profile(); app.connected = true; app.busy = true;
    app.threadId = "preview"; app.turnId = "turn";
    LinearLayout root = new LinearLayout(app); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(240, 242, 246)); root.setPadding(16, 8, 16, 32);
    label(app, root, "RemoteGPT · 流体云内容排版", 22);
    label(app, root, "基于实际通知内容制作的排版示例\n非系统截图，系统外壳与高度以真机为准", 13);
    label(app, root, "01 运行中 · 不显示按钮", 16);
    ChatItem work = new ChatItem("c", "commandExecution", "./gradlew :app:testDebugUnitTest");
    work.detail = "已完成消息分发检查\n正在验证历史阅读位置\n90 项测试通过，继续检查通知更新…";
    app.items.add(work); card(app, root);
    label(app, root, "02 需要审批 · 才显示决策按钮", 16);
    app.approvals.add(Json.obj("id", 1, "method", "item/commandExecution/requestApproval", "params",
        Json.obj("command", "./gradlew :app:assembleDebug", "reason", "需要构建安装包，验证本次修改。", "cwd", "/project/RemoteGPT")));
    card(app, root);
    label(app, root, "03 需要回答 · 显示问题与入口", 16);
    app.approvals.clear();
    app.approvals.add(Json.obj("id", 2, "method", "item/tool/requestUserInput", "params",
        Json.obj("questions", new org.json.JSONArray().put(Json.obj("question", "这次需要构建调试版还是发布版？")))));
    card(app, root);
    int width = 822;
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    root.layout(0, 0, width, root.getMeasuredHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File folder = new File("build/screenshots"); assertTrue(folder.isDirectory() || folder.mkdirs());
    try (FileOutputStream out = new FileOutputStream(new File(folder, "live-layout-1.0.17.png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
    bitmap.recycle();
  }
}
