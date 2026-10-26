package app.remotessh;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Native chat/editor UI primitives. Kept separate from RemoteScreens' navigation palette. */
final class DialogUi {
  final Activity activity;
  final int bg, fg, muted, panel, line;

  DialogUi(Activity activity, int bg, int fg, int muted, int panel, int line) {
    this.activity = activity;
    this.bg = bg; this.fg = fg; this.muted = muted; this.panel = panel; this.line = line;
  }

  void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_SHORT).show(); }

  int dp(float n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(activity);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(activity);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  TextView text(String s, int size, int color) {
    TextView v = new TextView(activity);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setFontFeatureSettings("kern");
    return v;
  }

  GradientDrawable rounded(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  void clickable(View v, Runnable r) {
    PressFeedback.apply(v, bg == Color.BLACK);
    v.setOnClickListener(
        w -> {
          w.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          r.run();
        });
  }

  IconView icon(String name, String label, Runnable action) {
    IconView i = new IconView(activity, name, fg);
    i.setContentDescription(label);
    i.setBackground(
        new RippleDrawable(
            android.content.res.ColorStateList.valueOf(line), null, rounded(fg, 30)));
    clickable(i, action);
    return i;
  }

  TextView action(String label, Runnable r) {
    TextView t = text(label, 15, fg);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(12), dp(16), dp(12));
    t.setMinHeight(dp(48));
    t.setBackground(rounded(panel, 24));
    clickable(t, r);
    return t;
  }

  EditText field(
      LinearLayout layout, String label, String value, String hint, boolean secret) {
    TextView l = text(label, 12, muted);
    l.setPadding(dp(4), dp(14), 0, dp(6));
    layout.addView(l);
    EditText e = new EditText(activity);
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

  Dialog sheet(String heading, View content, boolean wide) {
    Dialog dialog = new Dialog(activity);
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

  /** Full-height editor: only the form scrolls; actions remain above the keyboard. */
  Dialog profileWindow(String heading, ScrollView form, LinearLayout footer, boolean wide) {
    Dialog dialog = new Dialog(activity);
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
    dialog.show();
    if (window != null) window.setLayout(wide ? dp(560) : -1, -1);
    shell.requestFocus();
    shell.requestApplyInsets();
    return dialog;
  }

}
