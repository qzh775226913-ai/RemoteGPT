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

  static PermissionMode fromRemote(Object approval, JSONObject sandbox) {
    String policy=approval instanceof String ? (String)approval : "";
    String type=sandbox==null ? "" : sandbox.optString("type").replace("-","").toLowerCase(java.util.Locale.ROOT);
    if(policy.equals("never") && type.equals("dangerfullaccess"))return FULL;
    if(policy.equals("never") && type.equals("readonly"))return READ_ONLY;
    if(policy.equals("on-request") && type.equals("workspacewrite"))return DEFAULT;
    return null;
  }
}
