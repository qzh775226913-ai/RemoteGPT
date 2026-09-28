package app.remotessh;

import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Profile {
  public String id = UUID.randomUUID().toString();
  public String name = "", host = "", user = "", password = "", privateKey = "", passphrase = "";
  public String cwd = "", command = "codex app-server", lastThread = "";
  public int port = 22;
  public boolean remember = false;
  public boolean tailscaleSsh = false;
  public boolean persistent = true;
  public final java.util.LinkedHashSet<String> projects = new java.util.LinkedHashSet<>();

  public void validate() {
    if (host.trim().isEmpty() || host.matches(".*[\\s/].*"))
      throw new IllegalArgumentException("请输入有效的 SSH 主机地址，不包含 ssh://");
    if (user.trim().isEmpty()) throw new IllegalArgumentException("请输入 SSH 用户名");
    if (port < 1 || port > 65535) throw new IllegalArgumentException("端口必须在 1–65535 之间");
    if (command.trim().isEmpty() || command.indexOf('\0') >= 0)
      throw new IllegalArgumentException("请输入远端启动命令");
    if (!cwd.isEmpty() && !cwd.startsWith("/"))
      throw new IllegalArgumentException("项目目录需要填写远端绝对路径，例如 /home/me/project");
  }

  public String title() {
    return name.isEmpty() ? host : name;
  }

  public JSONObject json() {
    return Json.obj(
        "id",
        id,
        "name",
        name,
        "host",
        host,
        "port",
        port,
        "user",
        user,
        "cwd",
        cwd,
        "command",
        command,
        "lastThread",
        lastThread,
        "projects",
        new JSONArray(projects),
        "tailscaleSsh",
        tailscaleSsh,
        "persistent",
        persistent,
        "remember",
        remember,
        "password",
        remember ? password : "",
        "privateKey",
        remember ? privateKey : "",
        "passphrase",
        remember ? passphrase : "");
  }

  public static Profile from(JSONObject j) {
    Profile p = new Profile();
    p.id = j.optString("id", p.id);
    p.name = j.optString("name");
    p.host = j.optString("host");
    p.port = j.optInt("port", 22);
    p.user = j.optString("user");
    p.cwd = j.optString("cwd");
    p.command = j.optString("command", "codex app-server");
    p.lastThread = j.optString("lastThread");
    JSONArray projects = j.optJSONArray("projects");
    if (projects != null)
      for (int i = 0; i < projects.length(); i++) {
        String path = projects.optString(i);
        if (path.startsWith("/")) p.projects.add(path);
      }
    if (!p.cwd.isEmpty()) p.projects.add(p.cwd);
    p.remember = j.optBoolean("remember");
    p.tailscaleSsh = j.optBoolean("tailscaleSsh");
    p.persistent = j.optBoolean("persistent", true);
    p.password = j.optString("password");
    p.privateKey = j.optString("privateKey");
    p.passphrase = j.optString("passphrase");
    return p;
  }
}
