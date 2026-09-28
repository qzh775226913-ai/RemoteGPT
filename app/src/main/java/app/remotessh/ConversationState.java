package app.remotessh;

import java.util.*;
import org.json.JSONObject;

/** Per-thread state. RemoteApp exposes the selected state through its existing UI facade. */
final class ConversationState {
  String threadId = "", turnId = "", title = "", cwd = "", state = "已连接", error = "", model = "", effort = "", unsent = "", draft = "";
  PermissionMode permission = PermissionMode.INHERIT;
  boolean busy, steering, readOnly, remoteRunning, replaying, stopping;
  final List<ChatItem> items = new ArrayList<>();
  final List<JSONObject> approvals = new ArrayList<>();
  final List<String> images = new ArrayList<>();
  final Map<String, android.graphics.Bitmap> previews = new HashMap<>();
  void capture(RemoteApp a) {
    threadId=a.threadId; turnId=a.turnId; title=a.threadTitle; cwd=a.threadCwd; state=a.state; error=a.error;
    model=a.model; effort=a.reasoningEffort; unsent=a.unsentText; draft=a.draftText;
    permission=a.permissionMode; busy=a.busy; steering=a.steering; readOnly=a.readOnly; remoteRunning=a.remoteRunning; replaying=a.replaying; stopping=a.stopping;
    items.clear(); items.addAll(a.items); approvals.clear(); approvals.addAll(a.approvals);
    images.clear(); images.addAll(a.draftImages); previews.clear(); previews.putAll(a.draftImagePreviews);
  }
  void apply(RemoteApp a) {
    a.threadId=threadId; a.turnId=turnId; a.threadTitle=title; a.threadCwd=cwd; a.state=state; a.error=error;
    a.model=model; a.reasoningEffort=effort; a.unsentText=unsent; a.draftText=draft;
    a.permissionMode=permission; a.busy=busy; a.steering=steering; a.readOnly=readOnly; a.remoteRunning=remoteRunning; a.replaying=replaying; a.stopping=stopping;
    a.items.clear(); a.items.addAll(items); a.approvals.clear(); a.approvals.addAll(approvals);
    a.draftImages.clear(); a.draftImages.addAll(images); a.draftImagePreviews.clear(); a.draftImagePreviews.putAll(previews);
  }
}
