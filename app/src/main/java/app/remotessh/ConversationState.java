package app.remotessh;

import java.util.*;
import org.json.JSONObject;

/** Per-thread state. RemoteApp exposes the selected state through its existing UI facade. */
final class ConversationState {
  String threadId = "", turnId = "", title = "", cwd = "", state = "已连接", error = "", model = "", effort = "", unsent = "", draft = "";
  PermissionMode permission = PermissionMode.INHERIT;
  PermissionMode effectivePermission = PermissionMode.INHERIT;
  ForcedApprovalController.State forcedApproval = new ForcedApprovalController.State();
  String journalPath = "";
  ConversationJournal journal = new ConversationJournal();
  InteractionHistory interactions = new InteractionHistory();
  long savedInteractionRevision;
  String serviceTier = "", inheritedServiceTier = "", effectiveServiceTier = "", effectiveModel = "";
  boolean inheritedTierKnown, tierOverrideApplied;
  boolean busy, steering, readOnly, remoteRunning, replaying, stopping;
  final List<ChatItem> items = new ArrayList<>();
  final List<JSONObject> approvals = new ArrayList<>();
  final List<String> images = new ArrayList<>();
  final List<FileAttachment> files = new ArrayList<>();
  final Map<String, android.graphics.Bitmap> previews = new HashMap<>();
  void capture(RemoteApp a) {
    forcedApproval = a.forcedApprovalState;
    threadId=a.threadId; turnId=a.turnId; title=a.threadTitle; cwd=a.threadCwd; state=a.state; error=a.error;
    model=a.model; effort=a.reasoningEffort; unsent=a.unsentText; draft=a.draftText;
    serviceTier=a.serviceTier; inheritedServiceTier=a.inheritedServiceTier; effectiveServiceTier=a.effectiveServiceTier;
    effectiveModel=a.effectiveModel; inheritedTierKnown=a.inheritedTierKnown; tierOverrideApplied=a.tierOverrideApplied;
    permission=a.permissionMode; busy=a.busy; steering=a.steering; readOnly=a.readOnly; remoteRunning=a.remoteRunning; replaying=a.replaying; stopping=a.stopping;
    effectivePermission=a.effectivePermissionMode;journalPath=a.journalPath;journal=a.journal;
    interactions=a.interactions;savedInteractionRevision=a.savedInteractionRevision;
    items.clear(); items.addAll(a.items); approvals.clear(); approvals.addAll(a.approvals);
    images.clear(); images.addAll(a.draftImages); previews.clear(); previews.putAll(a.draftImagePreviews);
    files.clear();files.addAll(a.draftFiles);
  }
  void apply(RemoteApp a) {
    a.forcedApprovalState = forcedApproval;
    a.threadId=threadId; a.turnId=turnId; a.threadTitle=title; a.threadCwd=cwd; a.state=state; a.error=error;
    a.model=model; a.reasoningEffort=effort; a.unsentText=unsent; a.draftText=draft;
    a.serviceTier=serviceTier; a.inheritedServiceTier=inheritedServiceTier; a.effectiveServiceTier=effectiveServiceTier;
    a.effectiveModel=effectiveModel; a.inheritedTierKnown=inheritedTierKnown; a.tierOverrideApplied=tierOverrideApplied;
    a.permissionMode=permission; a.busy=busy; a.steering=steering; a.readOnly=readOnly; a.remoteRunning=remoteRunning; a.replaying=replaying; a.stopping=stopping;
    a.effectivePermissionMode=effectivePermission;a.journalPath=journalPath;a.journal=journal;
    a.interactions=interactions;a.savedInteractionRevision=savedInteractionRevision;
    a.items.clear(); a.items.addAll(items); a.approvals.clear(); a.approvals.addAll(approvals);
    a.draftImages.clear(); a.draftImages.addAll(images); a.draftImagePreviews.clear(); a.draftImagePreviews.putAll(previews);
    a.draftFiles.clear();a.draftFiles.addAll(files);
  }
}
