package app.remotessh;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Model/effort/speed picker; owns and releases its catalog listener with its window. */
final class ModelPicker {
  private final Activity activity;
  private final RemoteApp app;
  private final DialogUi ui;
  private final boolean wide;
  private Dialog dialog;
  private Runnable catalogListener;

  ModelPicker(Activity activity, RemoteApp app, DialogUi ui, boolean wide) {
    this.activity = activity; this.app = app; this.ui = ui; this.wide = wide;
  }

  private void releaseListener() {
    if (catalogListener != null) {
      app.stateListeners.remove(catalogListener);
      catalogListener = null;
    }
  }

  void dismiss() {
    releaseListener();
    if (dialog != null) dialog.dismiss();
  }

  void show() {
    if (!app.connected) { ui.toast(UiText.get(R.string.ui_connect_to_select_a_remote_model)); return; }
    final String[] selected = {app.model, app.reasoningEffort, app.serviceTier};
    final Profile modelProfile = app.profile;
    final String modelThread = app.threadId;
    LinearLayout panelView = ui.column();
    panelView.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(8));
    TextView subtitle = ui.text(UiText.get(R.string.ui_send_a_message_to_update_the_model), 12, ui.muted);
    subtitle.setPadding(ui.dp(4), 0, 0, ui.dp(16));
    panelView.addView(subtitle);
    TextView catalogStatus = ui.text("", 12, ui.muted);
    panelView.addView(catalogStatus);
    ScrollView modelScroll = new ModelListScrollView(activity);
    int modelListBackground = ui.bg == Color.BLACK ? 0xff161616 : 0xfff8f8f8;
    modelScroll.setContentDescription(UiText.get(R.string.ui_scrollable_model_list));
    modelScroll.setBackground(ui.rounded(modelListBackground, 16));
    modelScroll.setClipToOutline(true);
    modelScroll.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
    modelScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
    LinearLayout modelList = ui.column(); modelScroll.addView(modelList);
    int modelCount = app.models.length() + 1;
    LinearLayout.LayoutParams modelScrollParams = new LinearLayout.LayoutParams(-1, ui.dp(Math.min(240, modelCount * 52 + 8)));
    modelScrollParams.topMargin = ui.dp(8);
    panelView.addView(modelScroll, modelScrollParams);
    LinearLayout effortHeader = ui.row();
    effortHeader.setPadding(ui.dp(4), ui.dp(20), ui.dp(4), ui.dp(4));
    TextView effortTitle = ui.text(UiText.get(R.string.ui_reasoning_effort), 13, ui.muted);
    effortHeader.addView(effortTitle, new LinearLayout.LayoutParams(0, -2, 1));
    TextView resetEffort = ui.text(UiText.get(R.string.ui_default), 12, ui.muted);
    resetEffort.setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8));
    resetEffort.setContentDescription(UiText.get(R.string.ui_reset_reasoning_effort_to_default));
    effortHeader.addView(resetEffort);
    panelView.addView(effortHeader);
    LinearLayout effortPanel = ui.column();
    TextView effortValue = ui.text(UiText.get(R.string.ui_default), 22, ui.fg);
    effortValue.setTypeface(null, Typeface.BOLD);
    effortValue.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(4));
    effortPanel.addView(effortValue);
    SeekBar effortSlider = new SeekBar(activity);
    effortSlider.setContentDescription(UiText.get(R.string.ui_reasoning_effort_slider));
    effortSlider.setPadding(ui.dp(12), 0, ui.dp(12), 0);
    effortSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.fg));
    effortSlider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.line));
    effortSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.fg));
    GradientDrawable tick = new GradientDrawable();
    tick.setShape(GradientDrawable.OVAL); tick.setColor(ui.muted); tick.setSize(ui.dp(3), ui.dp(3));
    effortSlider.setTickMark(tick);
    effortSlider.setKeyProgressIncrement(1);
    effortPanel.addView(effortSlider, new LinearLayout.LayoutParams(-1, ui.dp(48)));
    LinearLayout endpoints = ui.row(); endpoints.setPadding(ui.dp(6), 0, ui.dp(6), 0);
    TextView low = ui.text("", 12, ui.muted), high = ui.text("", 12, ui.muted);
    endpoints.addView(low, new LinearLayout.LayoutParams(0, -2, 1));
    high.setGravity(Gravity.END); endpoints.addView(high, new LinearLayout.LayoutParams(0, -2, 1));
    effortPanel.addView(endpoints);
    panelView.addView(effortPanel);
    TextView effortHint = ui.text("", 12, ui.muted);
    effortHint.setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(16)); panelView.addView(effortHint);
    TextView speedTitle = ui.text(UiText.get(R.string.ui_speed), 13, ui.muted);
    speedTitle.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(8)); panelView.addView(speedTitle);
    LinearLayout speedChoices = ui.row(); panelView.addView(speedChoices);
    TextView speedHint = ui.text("", 12, ui.muted);
    speedHint.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(12)); panelView.addView(speedHint);
    Dialog[] holder = new Dialog[1];
    Runnable[] redraw = new Runnable[1];
    Runnable renderCatalogStatus = () -> catalogStatus.setText(app.modelsStatus.isEmpty() ? UiText.get(R.string.ui_models_are_provided_by_the_server_if_a_new_model_is_missing_check) : UiPresentation.modelStatus(app.modelsStatus));
    redraw[0] = () -> {
      renderCatalogStatus.run();
      modelScroll.getLayoutParams().height = ui.dp(Math.min(240, (app.models.length() + 1) * 52 + 8));
      modelScroll.requestLayout();
      modelList.removeAllViews();
      for (int i = -1; i < app.models.length(); i++) {
        JSONObject model = i < 0 ? null : app.models.optJSONObject(i);
        if (i >= 0 && model == null) continue;
        String id = model == null ? "" : ModelOptions.id(model);
        boolean checked = selected[0].equals(id);
        LinearLayout option = ui.row(); option.setPadding(ui.dp(14), 0, ui.dp(10), 0);
        option.setBackground(ui.rounded(checked ? ui.panel : modelListBackground, 14));
        TextView label = ui.text(model == null ? UiText.get(R.string.ui_remote_default) : ModelOptions.displayName(app.models, id), 15, ui.fg);
        label.setMaxLines(1); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER_VERTICAL);
        option.addView(label, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        if (checked) option.addView(new IconView(activity, "check", ui.fg), new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
        option.setContentDescription(UiText.get(R.string.ui_select_model_1_s, id));
        ui.clickable(option, () -> {
          selected[0] = id; selected[1] = ""; redraw[0].run();
          effortPanel.setAlpha(.3f); effortPanel.setTranslationY(ui.dp(6));
          effortPanel.animate().alpha(1).translationY(0).setDuration(180).start();
        });
        modelList.addView(option);
      }
      JSONObject resolved = app.selectedModel(selected[0]);
      JSONObject chosen = resolved;
      speedChoices.removeAllViews();
      String supported = ServiceTierOptions.supportedId(resolved, selected[2]);
      boolean revoked = !selected[2].isEmpty() && (!app.supportsServiceTier()
          || !selected[2].equals(ServiceTierOptions.STANDARD) && (!app.speedSelectionEnabled() || supported.isEmpty()));
      if (revoked) selected[2] = "";
      else if (!selected[2].isEmpty()) selected[2] = supported;
      List<JSONObject> speeds = new ArrayList<>();
      speeds.add(Json.obj("id", "", "name", UiText.get(R.string.ui_remote_default)));
      if (app.supportsServiceTier()) speeds.add(Json.obj("id", ServiceTierOptions.STANDARD, "name", UiText.get(R.string.ui_standard)));
      if (app.speedSelectionEnabled()) speeds.addAll(ServiceTierOptions.tiers(resolved));
      // Each option stays readable when a future server offers more than Fast.
      LinearLayout speedRows = ui.column();
      speedChoices.addView(speedRows, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout speedRow = null;
      for (int i = 0; i < speeds.size(); i++) {
        if (i % 3 == 0) { speedRow = ui.row(); speedRows.addView(speedRow); }
        String tier = speeds.get(i).optString("id");
        TextView option = ui.text(ServiceTierOptions.label(tier, resolved), 13, ui.fg);
        option.setGravity(Gravity.CENTER); option.setMinHeight(ui.dp(44)); option.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        option.setBackground(ui.rounded(selected[2].equals(tier) ? ui.panel : ui.bg, 12));
        option.setTypeface(null, selected[2].equals(tier) ? Typeface.BOLD : Typeface.NORMAL);
        option.setContentDescription(UiText.get(R.string.ui_select_speed_1_s, tier.isEmpty() ? UiText.get(R.string.ui_remote_default) : tier));
        option.setSelected(selected[2].equals(tier));
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(0, -2, 1);
        speedParams.setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
        speedRow.addView(option, speedParams);
        ui.clickable(option, () -> { selected[2] = tier; redraw[0].run(); });
      }
      String speedDetail = "";
      if (selected[2].isEmpty()) speedDetail = app.inheritedTierKnown || app.modelSettings.configKnown
          ? UiText.get(R.string.ui_use_remote_default) + ServiceTierOptions.label(app.defaultServiceTier(resolved), resolved) : UiText.get(R.string.ui_use_remote_defaults_without_overriding_the_remote_configuration);
      else if (selected[2].equals(ServiceTierOptions.STANDARD)) speedDetail = UiText.get(R.string.ui_standard_speed_applies_next_turn);
      else speedDetail = ServiceTierOptions.description(selected[2], resolved);
      if (!selected[2].isEmpty() && !selected[2].equals(ServiceTierOptions.STANDARD) && speedDetail.isEmpty())
        speedDetail = UiText.get(R.string.ui_remote_tier) + selected[2] + UiText.get(R.string.ui_applies_next_turn);
      if (!app.supportsServiceTier()) speedDetail = UiText.get(R.string.ui_no_speed_options_were_provided_you_can_use_the_remote_default);
      else if (!app.speedSelectionEnabled()) speedDetail += UiText.get(R.string.ui_speed_selection_is_disabled_on_the_server);
      else if (resolved == null) speedDetail += app.modelSettingsLoading ? UiText.get(R.string.ui_checking_the_remote_default_model) : UiText.get(R.string.ui_select_a_model_or_wait_for_automatic_synchronization_to_confirm_s);
      else if (ServiceTierOptions.tiers(resolved).isEmpty()) speedDetail += UiText.get(R.string.ui_this_model_offers_no_additional_speed_tiers);
      if (revoked) speedDetail = UiText.get(R.string.ui_the_selected_speed_is_no_longer_available_using_the_remote_defaul) + speedDetail;
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
      effortValue.setText(efforts.isEmpty() ? UiText.get(R.string.ui_default) : ModelOptions.label(efforts.get(index)));
      if (Build.VERSION.SDK_INT >= 30) effortSlider.setStateDescription(effortValue.getText());
      low.setText(efforts.isEmpty() ? "" : ModelOptions.label(efforts.get(0)));
      high.setText(efforts.size() < 2 ? "" : ModelOptions.label(efforts.get(efforts.size() - 1)));
      effortHint.setText(efforts.isEmpty() ? UiText.get(R.string.ui_this_model_offers_no_reasoning_effort_options) : UiText.get(R.string.ui_default_256) + ModelOptions.label(efforts.get(defaultIndex)));
      resetEffort.setEnabled(!efforts.isEmpty());
      resetEffort.setAlpha(efforts.isEmpty() ? .4f : 1);
      ui.clickable(resetEffort, () -> {
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
    TextView apply = ui.action(UiText.get(R.string.ui_done), () -> {
      if (!app.connected || app.profile != modelProfile || !app.threadId.equals(modelThread)) {
        dismiss(); return;
      }
      app.model = selected[0]; app.reasoningEffort = selected[1];
      app.chooseServiceTier(selected[2]); dismiss();
    });
    apply.setTextColor(ui.bg); apply.setBackground(ui.rounded(ui.fg, 22));
    ScrollView pickerScroll = new ScrollView(activity); pickerScroll.setFillViewport(false); pickerScroll.addView(panelView);
    LinearLayout pickerContainer = ui.column();
    int available = Math.max(ui.dp(180), activity.getResources().getDisplayMetrics().heightPixels - ui.dp(232));
    panelView.measure(View.MeasureSpec.makeMeasureSpec(wide ? ui.dp(500) : activity.getResources().getDisplayMetrics().widthPixels - ui.dp(16), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    pickerContainer.addView(pickerScroll, new LinearLayout.LayoutParams(-1, Math.min(available, panelView.getMeasuredHeight())));
    LinearLayout pickerFooter = ui.column(); pickerFooter.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(8));
    pickerFooter.addView(apply); pickerContainer.addView(pickerFooter);
    holder[0] = ui.sheet(UiText.get(R.string.ui_model), pickerContainer, wide);
    dialog = holder[0];
    final JSONArray[] rendered = {app.models};
    final ModelSettings[] renderedSettings = {app.modelSettings};
    final boolean[] settingsLoading = {app.modelSettingsLoading};
    Runnable updateModels = () -> {
      if (!dialog.isShowing()) return;
      if (!app.connected || app.profile != modelProfile || !app.threadId.equals(modelThread)) {
        dismiss(); return;
      }
      renderCatalogStatus.run();
      if (rendered[0] != app.models || renderedSettings[0] != app.modelSettings || settingsLoading[0] != app.modelSettingsLoading) {
        rendered[0] = app.models;
        renderedSettings[0] = app.modelSettings; settingsLoading[0] = app.modelSettingsLoading;
        redraw[0].run();
      }
    };
    catalogListener = updateModels;
    app.stateListeners.add(updateModels);
    holder[0].setOnDismissListener(d -> releaseListener());
    app.refreshModels();
    app.refreshModelSettings();
  }

}
