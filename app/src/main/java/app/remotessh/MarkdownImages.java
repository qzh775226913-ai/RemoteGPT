package app.remotessh;

import java.util.regex.*;

/** Mask literal Markdown examples before media discovery; canonical text is untouched. */
final class MarkdownImages {
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})([^\\r]*)\\r?$");

  static String prose(String source) {
    StringBuilder prose = new StringBuilder();
    String fence = "";
    for (String line : source.split("\n", -1)) {
      Matcher mark = FENCE.matcher(line);
      if (!fence.isEmpty()) {
        if (line.matches(" {0,3}" + Pattern.quote(fence.substring(0, 1))
            + "{" + fence.length() + ",}[ \\t\\r]*")) fence = "";
        prose.append('\n');
      } else if (mark.matches()) {
        fence = mark.group(1);
        prose.append('\n');
      } else prose.append(line).append('\n');
    }
    String text = prose.toString();
    for (int i = 0; i < text.length();) {
      if (text.charAt(i) == '\\') {
        // Escaped image openers remain literal; an escaped backslash allows the next token.
        prose.setCharAt(i, ' ');
        if (i + 1 < text.length()) prose.setCharAt(i + 1, ' ');
        i += 2;
      } else if (text.charAt(i) == '`') {
        int length = 1;
        while (i + length < text.length() && text.charAt(i + length) == '`') length++;
        int end = i + length;
        while (end < text.length()) {
          if (text.charAt(end) != '`') { end++; continue; }
          int run = 1;
          while (end + run < text.length() && text.charAt(end + run) == '`') run++;
          end += run;
          if (run == length) break;
        }
        while (i < end) prose.setCharAt(i++, ' ');
      } else i++;
    }
    return prose.toString();
  }
}
