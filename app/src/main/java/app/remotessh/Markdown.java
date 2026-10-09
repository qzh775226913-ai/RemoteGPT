package app.remotessh;

import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import java.util.regex.*;

/** Local text rendering only: never executes HTML, scripts, or remote URLs. */
public final class Markdown {
  public static CharSequence render(String source, int muted, int panel) {
    return render(source, muted, panel, null);
  }

  public static CharSequence render(String source, int muted, int panel, java.util.function.Consumer<String> open) {
    return render(source, muted, panel, open, null);
  }

  public static CharSequence render(String source, int muted, int panel, java.util.function.Consumer<String> open, java.util.function.Consumer<String> copy) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    boolean code = false;
    String[] lines = source.split("\n", -1);
    for (int n = 0; n < lines.length; n++) {
      String line = lines[n];
      if (line.startsWith("```")) {
        code = !code;
        if (n > 0 && out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        continue;
      }
      int start = out.length();
      if (code) {
        out.append(line);
        out.setSpan(
            new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(
            new BackgroundColorSpan(panel), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else if (line.matches("^#{1,6} .*")) {
        int level = line.indexOf(' ');
        out.append(line.substring(level + 1));
        out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), 33);
        out.setSpan(new RelativeSizeSpan(level == 1 ? 1.35f : 1.14f), start, out.length(), 33);
      } else {
        if (line.startsWith("- ") || line.startsWith("* ")) line = "•  " + line.substring(2);
        if (line.startsWith("> ")) {
          line = "│ " + line.substring(2);
          out.append(line);
          out.setSpan(new ForegroundColorSpan(muted), start, out.length(), 33);
        } else inline(out, line, open, copy);
      }
      if (n < lines.length - 1) out.append('\n');
    }
    return out;
  }

  private static void inline(SpannableStringBuilder out, String line, java.util.function.Consumer<String> open, java.util.function.Consumer<String> copy) {
    Matcher m =
        Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)\\]\\(([^\\s)]+)\\)")
            .matcher(line);
    int last = 0;
    while (m.find()) {
      out.append(line, last, m.start());
      int s = out.length();
      if (m.group(1) != null) {
        out.append(m.group(1));
        out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), 33);
      } else if (m.group(2) != null) {
        String value = m.group(2);
        out.append(value);
        out.setSpan(new TypefaceSpan("monospace"), s, out.length(), 33);
        if (copy != null) out.setSpan(new ClickableSpan() {
          @Override public void onClick(android.view.View widget) { copy.accept(value); }
        }, s, out.length(), 33);
      } else {
        out.append(m.group(3));
        String url = m.group(4);
        out.setSpan(open == null ? new URLSpan(url) : new ClickableSpan() {
          @Override public void onClick(android.view.View widget) { open.accept(url); }
        }, s, out.length(), 33);
      }
      last = m.end();
    }
    out.append(line, last, line.length());
  }
}
