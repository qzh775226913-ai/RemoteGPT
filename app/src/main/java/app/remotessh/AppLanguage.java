package app.remotessh;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.LocaleList;
import java.util.*;

/** App-wide display preference. Never changes protocol, stored messages or JVM locale. */
final class AppLanguage implements Application.ActivityLifecycleCallbacks {
  static final String[] TAGS = {"", "en", "zh-Hans", "zh-Hant", "ja", "ko", "es", "pt-BR", "de", "fr", "ru"};
  static final String[] NAMES = {"", "English", "简体中文", "繁體中文", "日本語", "한국어", "Español", "Português (Brasil)", "Deutsch", "Français", "Русский"};
  private final RemoteApp app;
  private final Set<Activity> activities = Collections.newSetFromMap(new WeakHashMap<>());

  AppLanguage(RemoteApp app) {
    this.app = app;
    refresh(app);
    app.registerActivityLifecycleCallbacks(this);
  }

  static String preference(Context context) {
    String tag = context.getSharedPreferences("language", Context.MODE_PRIVATE).getString("tag", "");
    return Arrays.asList(TAGS).contains(tag) ? tag : "";
  }

  static Locale supported(Locale locale) {
    String language = locale.getLanguage();
    if (language.equals("zh")) {
      String script = locale.getScript(), country = locale.getCountry();
      boolean traditional = script.equals("Hant") || (script.isEmpty() &&
          (country.equals("TW") || country.equals("HK") || country.equals("MO")));
      return Locale.forLanguageTag(traditional ? "zh-Hant" : "zh-Hans");
    }
    if (language.equals("pt")) return Locale.forLanguageTag("pt-BR");
    for (String tag : TAGS) if (!tag.isEmpty() && tag.equals(language)) return Locale.forLanguageTag(tag);
    return Locale.ENGLISH;
  }

  static Locale locale(Context context) {
    String tag = preference(context);
    return tag.isEmpty() ? supported(context.getResources().getConfiguration().getLocales().get(0))
        : Locale.forLanguageTag(tag);
  }

  static Context wrap(Context base) {
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocales(new LocaleList(locale(base)));
    return base.createConfigurationContext(config);
  }

  static void refresh(Context app) { UiText.initialize(wrap(app)); }

  void select(String tag) {
    if (!Arrays.asList(TAGS).contains(tag) || tag.equals(preference(app))) return;
    app.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("tag", tag).apply();
    refresh(app);
    notifyPresentation();
    for (Activity activity : new ArrayList<>(activities))
      if (!activity.isFinishing() && !activity.isDestroyed()) activity.recreate();
  }

  void notifyPresentation() {
    // A locale change is not a conversation event or an authorization event.
    if (app.observer != null) app.observer.run();
    for (Runnable listener : new ArrayList<>(app.stateListeners)) listener.run();
  }

  @Override public void onActivityCreated(Activity activity, Bundle state) { activities.add(activity); }
  @Override public void onActivityDestroyed(Activity activity) { activities.remove(activity); }
  @Override public void onActivityStarted(Activity activity) {}
  @Override public void onActivityResumed(Activity activity) {}
  @Override public void onActivityPaused(Activity activity) {}
  @Override public void onActivityStopped(Activity activity) {}
  @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
