package app.remotessh;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;

/** A compact system-bubble surface; all decisions use the live application's request. */
public final class BubbleActivity extends Activity {
  private RemoteApp app;
  private String target = "";
  private LinearLayout body;
  private String shown = "";
  private int foreground;
  private final Runnable update = this::render;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (RemoteApp) getApplication();
    target = app.ownsIntent(getIntent()) ? getIntent().getStringExtra("threadId") : app.threadId;
    boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    int mode = getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("theme", 0);
    if (mode != 0) dark = mode == 2;
    foreground = android.graphics.Color.parseColor(dark ? "#ECECEC" : "#171717");
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(android.graphics.Color.parseColor(dark ? "#212121" : "#FFFFFF"));
    body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (20 * getResources().getDisplayMetrics().density);
    body.setPadding(pad, pad, pad, pad); scroll.addView(body); setContentView(scroll);
  }
  @Override protected void onStart() { super.onStart(); app.stateListeners.add(update); render(); }
  @Override protected void onStop() { app.stateListeners.remove(update); super.onStop(); }
  private void text(String value, int size) {
    TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(foreground);
    view.setTextIsSelectable(true); view.setPadding(0, 8, 0, 12); body.addView(view);
  }
  private void button(String label, Runnable action) {
    Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run()); body.addView(button);
  }
  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent); setIntent(intent);
    if (app.ownsIntent(intent)) { target=intent.getStringExtra("threadId"); shown=""; render(); }
  }
  private void render() {
    if (!app.connected || (getIntent().hasExtra("profileId") && !app.ownsIntent(getIntent()))) {
      body.removeAllViews(); text("连接已断开，请打开应用重新连接。", 16); return;
    }
    app.inConversation(target, this::renderConversation);
  }
  private void renderConversation() {
    JSONObject req = app.approvals.isEmpty() ? null : app.approvals.get(0);
    String key = app.connected + "|" + app.state + "|" + app.busy + "|" + app.threadTitle + "|" + req + "|" + app.error + "|" + app.replaying;
    if (key.equals(shown)) return;
    shown = key; body.removeAllViews();
    text(app.threadTitle.isEmpty() ? "RemoteGPT" : app.threadTitle, 20);
    text(ConnectionService.summary(app), 16);
    if (!app.error.isEmpty()) text(app.error, 14);
    if (req != null && !app.replaying) {
      JSONObject params = Json.object(req, "params");
      text(params.optString("reason", "远端请求你的确认") + "\n\n"
          + params.optString("command", params.optString("grantRoot", "文件修改 / 补充信息"))
          + "\n" + params.optString("cwd") + "\n会话：" + params.optString("threadId"), 14);
      if (params.has("networkApprovalContext")) text("网络访问：" + params.opt("networkApprovalContext"), 14);
      for (ChatItem item : app.items)
        if (item.id.equals(params.optString("itemId")) && !item.detail.isEmpty()) text(item.detail, 14);
      String method = req.optString("method");
      if (method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval")) {
        button("允许一次", () -> answer(req, "accept"));
        button("拒绝", () -> answer(req, "decline"));
      } else text("请打开对话填写补充信息。", 14);
    }
    button("打开对话", () -> {
      startActivity(app.conversationIntent(MainActivity.class, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
      finish();
    });
  }
  private void answer(JSONObject req, String decision) {
    app.inConversation(target, () -> answerInConversation(req, decision));
  }
  private void answerInConversation(JSONObject req, String decision) {
    if (app.replaying || !app.connected || !app.approvals.contains(req)) { shown = ""; render(); return; }
    for (int n = 0; n < body.getChildCount(); n++) if (body.getChildAt(n) instanceof Button) body.getChildAt(n).setEnabled(false);
    app.answer(req, Json.obj("decision", decision));
  }
}
