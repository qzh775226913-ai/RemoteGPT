package app.remotessh;

import android.app.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Searchable, grouped names with a separate detail sheet. */
final class CapabilityDirectoryDialog {
  static final class Entry {
    final String name, group, detail;
    final Runnable open;
    Entry(String name, String group, String detail) {
      this(name, group, detail, null);
    }
    Entry(String name, String group, String detail, Runnable open) {
      this.name = name; this.group = group; this.detail = detail; this.open = open;
    }
  }

  private final Activity activity;
  private final LinearLayout rows;
  private final ScrollView scroll;
  private final EditText search;
  private final AlertDialog dialog;
  private final List<Entry> entries = new ArrayList<>();
  private final TextWatcher watcher;
  private String emptyText;
  private boolean failed;

  CapabilityDirectoryDialog(Activity activity, String title, String emptyText) {
    this.activity = activity;
    this.emptyText = emptyText;
    float density = activity.getResources().getDisplayMetrics().density;
    int pad = Math.round(16 * density);
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, 0, pad, 0);
    search = new EditText(activity);
    search.setSingleLine(true);
    search.setHint(UiText.get(R.string.ui_search_names));
    search.setContentDescription(UiText.get(R.string.ui_search_names));
    root.addView(search, new LinearLayout.LayoutParams(-1, -2));
    scroll = new ScrollView(activity);
    rows = new LinearLayout(activity);
    rows.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(rows);
    int height = Math.min(Math.round(420 * density),
        activity.getResources().getDisplayMetrics().heightPixels * 55 / 100);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, height));
    dialog = new AlertDialog.Builder(activity).setTitle(title).setView(root)
        .setPositiveButton(UiText.get(R.string.ui_close), null).create();
    watcher = new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
      public void afterTextChanged(Editable s) {}
    };
    search.addTextChangedListener(watcher);
    dialog.setOnDismissListener(d -> { search.removeTextChangedListener(watcher); rows.removeAllViews(); });
    renderMessage(UiText.get(R.string.ui_loading));
    dialog.show();
  }

  boolean isShowing() { return dialog.isShowing() && !activity.isDestroyed(); }
  void dismiss() { dialog.dismiss(); }

  void showEntries(List<Entry> loaded) {
    if (!isShowing()) return;
    failed = false;
    entries.clear(); entries.addAll(loaded);
    render();
  }

  void showError() {
    if (!isShowing()) return;
    entries.clear(); failed = true; render();
  }

  private void render() {
    if (!isShowing()) return;
    rows.removeAllViews();
    String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
    Map<String,Integer> counts = new LinkedHashMap<>();
    for (Entry entry : entries)
      if (entry.name.toLowerCase(Locale.ROOT).contains(query))
        counts.put(entry.group, counts.getOrDefault(entry.group, 0) + 1);
    String group = null;
    int shown = 0;
    for (Entry entry : entries) {
      if (!entry.name.toLowerCase(Locale.ROOT).contains(query)) continue;
      if (!entry.group.equals(group)) {
        group = entry.group;
        TextView heading = new TextView(activity);
        heading.setText(group + " (" + counts.get(group) + ")");
        heading.setTextSize(13);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, dp(5));
        rows.addView(heading);
      }
      TextView name = new TextView(activity);
      name.setText(entry.name);
      name.setTextSize(16);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setMinHeight(dp(48));
      name.setPadding(dp(8), dp(5), dp(8), dp(5));
      TypedValue feedback = new TypedValue();
      if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, feedback, true)
          && feedback.resourceId != 0) name.setBackgroundResource(feedback.resourceId);
      name.setFocusable(true);
      name.setOnClickListener(v -> {
        if (entry.open != null) entry.open.run();
        else new AlertDialog.Builder(activity).setTitle(entry.name)
            .setMessage(entry.detail).setPositiveButton(UiText.get(R.string.ui_close), null).show();
      });
      rows.addView(name, new LinearLayout.LayoutParams(-1, -2));
      shown++;
    }
    if (shown == 0) renderMessage(failed ? UiText.get(R.string.ui_remote_list_failed) :
        query.isEmpty() ? emptyText : UiText.get(R.string.ui_no_matching_entries));
    scroll.post(() -> scroll.scrollTo(0, 0));
  }

  private void renderMessage(String text) {
    rows.removeAllViews();
    TextView message = new TextView(activity);
    message.setText(text);
    message.setTextSize(14);
    message.setPadding(0, dp(18), 0, dp(18));
    rows.addView(message);
  }

  private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
