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
    int pad = dp(12);
    body.setPadding(pad, dp(6), pad, dp(6)); scroll.addView(body); setContentView(scroll);
  }
  @Override protected void onStart() { super.onStart(); app.stateListeners.add(update); render(); }
  @Override protected void onStop() { app.stateListeners.remove(update); super.onStop(); }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private void openConversation() {
    startActivity(app.conversationIntent(MainActivity.class, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }
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
    String progress = LiveStatusNotifications.running(app) ? LiveStatusNotifications.preview(app) : "任务已结束";
    String key = app.connected + "|" + progress + "|" + app.threadTitle + "|" + req + "|" + app.error + "|" + app.replaying;
    if (key.equals(shown)) return;
    shown = key; body.removeAllViews();
    LinearLayout header = new LinearLayout(this);
    header.setGravity(android.view.Gravity.CENTER_VERTICAL);
    TextView title = new TextView(this);
    title.setText(app.threadTitle.isEmpty() ? "RemoteGPT" : app.threadTitle.replaceAll("\\s+", " "));
    title.setTextSize(18); title.setTextColor(foreground);
    title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setContentDescription("气泡标题");
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView open = new TextView(this); open.setText("打开 ›"); open.setTextSize(12); open.setTextColor(foreground);
    open.setPadding(dp(12), 0, 0, 0); header.addView(open);
    header.setContentDescription("打开对话"); header.setOnClickListener(v -> openConversation());
    body.addView(header, new LinearLayout.LayoutParams(-1, dp(32)));
    LiveProgressTextView content = new LiveProgressTextView(this);
    content.setTextColor(foreground); content.setProgress(progress);
    body.addView(content, new LinearLayout.LayoutParams(-1, -2));
    if (!app.error.isEmpty()) text(app.error, 14);
    if (req != null && !app.replaying) {
      String method = req.optString("method");
      if (method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval")) {
        LinearLayout actions = new LinearLayout(this);
        for (String choice : new String[]{"accept", "decline"}) {
          Button action = new Button(this); action.setText(choice.equals("accept") ? "允许一次" : "拒绝");
          action.setOnClickListener(v -> answer(req, choice));
          actions.addView(action, new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(actions);
      } else button("填写信息", this::openConversation);
    }
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
