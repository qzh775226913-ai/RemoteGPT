package app.remotessh;

import org.json.JSONObject;

public enum PermissionMode {
  INHERIT("沿用远端权限", "", "", ""),
  DEFAULT("按需审批", "on-request", "workspace-write", "workspaceWrite"),
  AUTO_REVIEW("自动审查", "on-request", "workspace-write", "workspaceWrite"),
  READ_ONLY("只读", "on-request", "read-only", "readOnly"),
  FULL("完全访问", "never", "danger-full-access", "dangerFullAccess");

  public final String label, approval, sandbox, sandboxType;
  PermissionMode(String label, String approval, String sandbox, String sandboxType) {
    this.label = label; this.approval = approval; this.sandbox = sandbox; this.sandboxType = sandboxType;
  }
  public JSONObject startParams() {
    return this == INHERIT ? new JSONObject() : Json.obj("approvalPolicy", approval, "sandbox", sandbox,
        "approvalsReviewer", this == AUTO_REVIEW ? "auto_review" : "user");
  }
  public void applyTurn(JSONObject params) throws org.json.JSONException {
    if (this != INHERIT) {
      params.put("approvalPolicy", approval);
      params.put("sandboxPolicy", Json.obj("type", sandboxType));
      params.put("approvalsReviewer", this == AUTO_REVIEW ? "auto_review" : "user");
    }
  }

  static PermissionMode fromRemote(Object approval, JSONObject sandbox) {
    return fromRemote(approval,sandbox,"");
  }
  static PermissionMode fromRemote(Object approval, JSONObject sandbox,String reviewer) {
    String policy=approval instanceof String ? (String)approval : "";
    String type=sandbox==null ? "" : sandbox.optString("type").replace("-","").toLowerCase(java.util.Locale.ROOT);
    if(policy.equals("never") && type.equals("dangerfullaccess"))return FULL;
    if((policy.equals("on-request") || policy.equals("never")) && type.equals("readonly"))return READ_ONLY;
    if(policy.equals("on-request") && type.equals("workspacewrite"))
      return reviewer.equals("auto_review")?AUTO_REVIEW:DEFAULT;
    return null;
  }
}
