package app.remotessh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;
public class PermissionModeTest {
  @Test public void mapsStartAndTurnPoliciesWithoutChangingInheritedPolicy() throws Exception {
    JSONObject inherited = new JSONObject(); PermissionMode.INHERIT.applyTurn(inherited);
    assertEquals(0, inherited.length());
    assertEquals("workspace-write", PermissionMode.DEFAULT.startParams().getString("sandbox"));
    JSONObject auto = new JSONObject(); PermissionMode.AUTO_REVIEW.applyTurn(auto);
    assertEquals("on-request", auto.getString("approvalPolicy"));
    assertEquals("workspaceWrite", auto.getJSONObject("sandboxPolicy").getString("type"));
    assertEquals("auto_review", auto.getString("approvalsReviewer"));
    assertEquals("user", PermissionMode.DEFAULT.startParams().getString("approvalsReviewer"));
    assertNotEquals(PermissionPicker.icon(PermissionMode.DEFAULT),PermissionPicker.icon(PermissionMode.AUTO_REVIEW));
    assertNotEquals(PermissionPicker.icon(PermissionMode.READ_ONLY),PermissionPicker.icon(PermissionMode.FULL));
    JSONObject read = new JSONObject(); PermissionMode.READ_ONLY.applyTurn(read);
    assertEquals("on-request", read.getString("approvalPolicy"));
    assertEquals("readOnly", read.getJSONObject("sandboxPolicy").getString("type"));
    JSONObject full = new JSONObject(); PermissionMode.FULL.applyTurn(full);
    assertEquals("dangerFullAccess", full.getJSONObject("sandboxPolicy").getString("type"));
  }
}
