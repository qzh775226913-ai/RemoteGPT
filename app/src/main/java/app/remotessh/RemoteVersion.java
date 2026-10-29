package app.remotessh;

import java.math.BigInteger;
import java.util.regex.*;
import org.json.*;

/** Installed CLI and live app-server are deliberately separate versions. */
final class RemoteVersion {
  String running = "", installed = "", latest = "", path = "", manager = "", reason = "", error = "";
  boolean checking, updating, prompt, restartSupported;

  static String fromHandshake(JSONObject result) {
    Matcher m = Pattern.compile("^[^\\s/]+/(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)")
        .matcher(result.optString("userAgent"));
    return m.find() ? m.group(1) : "";
  }

  static int compare(String a, String b) {
    String[] aa = a.split("\\+", 2)[0].split("-", 2), bb = b.split("\\+", 2)[0].split("-", 2);
    String[] av = aa[0].split("\\."), bv = bb[0].split("\\.");
    if (av.length != 3 || bv.length != 3) throw new IllegalArgumentException("Invalid version");
    for (int i = 0; i < 3; i++) {
      int c = new BigInteger(av[i]).compareTo(new BigInteger(bv[i]));
      if (c != 0) return c;
    }
    if (aa.length != bb.length) return aa.length == 1 ? 1 : -1;
    if (aa.length == 1) return 0;
    String[] ap = aa[1].split("\\."), bp = bb[1].split("\\.");
    for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
      boolean an = ap[i].matches("[0-9]+"), bn = bp[i].matches("[0-9]+");
      int c = an && bn ? new BigInteger(ap[i]).compareTo(new BigInteger(bp[i]))
          : an != bn ? (an ? -1 : 1) : ap[i].compareTo(bp[i]);
      if (c != 0) return c;
    }
    return Integer.compare(ap.length, bp.length);
  }

  static boolean newer(String candidate, String current) {
    if (candidate.isEmpty() || current.isEmpty()) return false;
    try { return compare(candidate, current) > 0; } catch (RuntimeException e) { return false; }
  }

  void accept(JSONObject result) {
    if (result.has("error")) { error = result.optString("error"); return; }
    installed = result.optString("installed"); latest = result.optString("latest");
    path = result.optString("path"); manager = result.optString("manager");
    reason = result.optString("reason"); error = result.optString("checkError");
  }

  boolean updateAvailable() { return newer(latest, installed.isEmpty() ? running : installed); }
  boolean canUpdate() { return updateAvailable() && !manager.isEmpty() && !path.isEmpty() && !checking && !updating; }
  boolean needsRestart() { return newer(installed, running); }
  String label() {
    String value = "Codex " + (running.isEmpty() ? UiText.get(R.string.ui_running_version_unknown) : running);
    if (updating) return UiText.get(R.string.ui_1_s_updating_to_2_s, value, latest);
    if (checking) return value + UiText.get(R.string.ui_checking_for_updates);
    if (updateAvailable()) return UiText.get(R.string.ui_1_s_update_available_current_2_s_latest_3_s, value, installed.isEmpty() ? running : installed, latest) + (error.isEmpty() ? "" : UiText.get(R.string.ui_operation_failed));
    if (needsRestart()) return UiText.get(R.string.ui_1_s_restart_required_installed_2_s, value, installed);
    if (!error.isEmpty()) return value + UiText.get(R.string.ui_check_failed_tap_to_retry);
    if (!latest.isEmpty()) return value + (newer(installed, latest) ? UiText.get(R.string.ui_latest_stable_version_402) : UiText.get(R.string.ui_up_to_date)) + latest;
    return value + UiText.get(R.string.ui_tap_to_check_for_updates);
  }
}
