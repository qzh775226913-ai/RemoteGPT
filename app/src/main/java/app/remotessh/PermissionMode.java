package app.remotessh;

import org.json.JSONObject;

public enum PermissionMode {
  INHERIT("沿用远端权限", "", "", ""),
  DEFAULT("按需审批", "on-request", "workspace-write", "workspaceWrite"),
  READ_ONLY("只读", "never", "read-only", "readOnly"),
  FULL("完全访问", "never", "danger-full-access", "dangerFullAccess");

  public final String label, approval, sandbox, sandboxType;
  PermissionMode(String label, String approval, String sandbox, String sandboxType) {
    this.label = label; this.approval = approval; this.sandbox = sandbox; this.sandboxType = sandboxType;
  }
  public JSONObject startParams() {
    return this == INHERIT ? new JSONObject() : Json.obj("approvalPolicy", approval, "sandbox", sandbox);
  }
  public void applyTurn(JSONObject params) throws org.json.JSONException {
    if (this != INHERIT) {
      params.put("approvalPolicy", approval);
      params.put("sandboxPolicy", Json.obj("type", sandboxType));
    }
  }
}
