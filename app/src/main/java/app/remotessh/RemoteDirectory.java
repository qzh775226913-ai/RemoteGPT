package app.remotessh;

import java.util.*;

public final class RemoteDirectory {
  public final String path;
  public final List<String> directories;
  public final boolean truncated;

  public RemoteDirectory(String path, List<String> directories, boolean truncated) {
    this.path = path;
    this.directories = directories;
    this.truncated = truncated;
  }

  public static String child(String parent, String name) {
    if (name == null
        || name.trim().isEmpty()
        || name.equals(".")
        || name.equals("..")
        || name.contains("/")
        || name.indexOf('\0') >= 0) throw new IllegalArgumentException("项目名称不能为空，且不能包含 / 或使用 .、..");
    return (parent.equals("/") ? "/" : parent + "/") + name;
  }

  public static String parent(String path) {
    int last = path.lastIndexOf('/');
    return last <= 0 ? "/" : path.substring(0, last);
  }

  public static String name(String path) {
    if (path == null || path.isEmpty()) return "新对话";
    if (path.equals("/")) return "/";
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
