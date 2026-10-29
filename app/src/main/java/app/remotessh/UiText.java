package app.remotessh;

import android.content.Context;
import java.util.Locale;

/** Resource access for app-owned presentation only; never accepts message/error text. */
final class UiText {
  private static volatile android.content.res.Resources localized;
  private UiText() {}
  static void initialize(Context context) { localized = context.getResources(); }
  static String get(int id, Object... arguments) {
    android.content.res.Resources resources = localized;
    if (resources == null) throw new IllegalStateException("UI resources are not initialized");
    return arguments.length == 0 ? resources.getString(id) : resources.getString(id, arguments);
  }
  static String quantity(int id, int count) {
    return localized.getQuantityString(id, count, count);
  }
  static Locale locale() { return localized.getConfiguration().getLocales().get(0); }
}
