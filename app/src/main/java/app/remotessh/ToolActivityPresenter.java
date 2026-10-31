package app.remotessh;

import java.util.*;
import org.json.JSONObject;

/** Builds stable visual groups from adjacent events without altering server history. */
final class ToolActivityPresenter {
  private ToolActivityPresenter() {}

  static List<ChatItem> present(List<ChatItem> source,String runningTurn) {
    List<ChatItem> result=new ArrayList<>();
    Set<String> detailedDiffTurns=new HashSet<>();
    for(ChatItem item:source)if(item.type.equals("fileChange") && !item.id.startsWith("diff-") &&
        !item.turnId.isEmpty())detailedDiffTurns.add(item.turnId);
    List<ChatItem> display=new ArrayList<>();
    for(ChatItem item:source)if(!item.id.startsWith("diff-") || !detailedDiffTurns.contains(item.turnId))display.add(item);
    Set<String> summaries=new HashSet<>();
    for(int i=0;i<display.size();){
      ChatItem first=display.get(i);
      if(groupable(first)){
        int end=i+1;
        while(end<display.size() && groupable(display.get(end)) &&
            first.turnId.equals(display.get(end).turnId))end++;
        if(end-i>=2){result.add(group(display.subList(i,end),runningTurn));i=end;continue;}
      }
      result.add(first);
      if(first.type.equals("agentMessage") && (first.phase.equals("final") || first.phase.equals("final_answer")) &&
          !first.turnId.isEmpty() && !first.turnId.equals(runningTurn) && summaries.add(first.turnId)){
        DiffSummary diff=DiffSummary.forTurn(source,first.turnId);
        if(!diff.files.isEmpty())result.add(summary(first.turnId,diff));
      }
      i++;
    }
    return result;
  }

  private static boolean groupable(ChatItem item) {
    return !item.turnId.isEmpty() && (item.type.equals("commandExecution") ||
        item.type.equals("fileChange") || item.type.equals("mcpToolCall") ||
        item.type.equals("webSearch") || item.type.equals("imageView") ||
        item.type.equals("imageGeneration"));
  }

  private static ChatItem group(List<ChatItem> children,String runningTurn) {
    ChatItem first=children.get(0);
    ChatItem group=new ChatItem("activity-"+first.turnId+"-"+first.id,"activityGroup","");
    group.turnId=first.turnId;
    group.status=first.turnId.equals(runningTurn)?"inProgress":"completed";
    group.children.addAll(children);
    Set<String> edited=new HashSet<>(),viewed=new HashSet<>();int actions=0,added=0,removed=0;
    StringBuilder version=new StringBuilder();
    for(ChatItem child:children){
      version.append(child.id).append(child.status).append(child.text).append(child.detail.hashCode());
      if(child.type.equals("fileChange") && !child.status.equals("failed") && !child.status.equals("declined"))
        for(DiffSummary.File file:DiffSummary.fromItem(child).files){
          edited.add(file.path);added+=file.added;removed+=file.removed;
        }
      if(child.type.equals("imageView"))viewed.addAll(child.images);
      if(child.commandActions!=null)for(int i=0;i<child.commandActions.length();i++){
        JSONObject action=child.commandActions.optJSONObject(i);
        if(action!=null && action.optString("type").equals("read") && !action.optString("path").isEmpty())
          viewed.add(action.optString("path"));
      }
      if(child.type.equals("commandExecution") || child.type.equals("mcpToolCall") ||
          child.type.equals("webSearch") || child.type.equals("imageGeneration"))actions++;
    }
    group.detail=version.toString();
    List<String> parts=new ArrayList<>();
    if(!edited.isEmpty())parts.add(UiText.get(R.string.ui_edited_files_count,edited.size())+"  +"+added+" −"+removed);
    if(!viewed.isEmpty())parts.add(UiText.get(R.string.ui_viewed_files_count,viewed.size()));
    if(actions>0)parts.add(UiText.get(R.string.ui_ran_actions_count,actions));
    group.text=String.join(" · ",parts);
    return group;
  }

  private static ChatItem summary(String turnId,DiffSummary diff) {
    ChatItem item=new ChatItem("files-"+turnId,"fileSummary",
        UiText.get(R.string.ui_changed_files_count,diff.files.size()));
    item.turnId=turnId;
    item.detail="+"+diff.added()+" −"+diff.removed();
    for(DiffSummary.File file:diff.files){
      ChatItem child=new ChatItem(item.id+"-"+file.path,"fileChange",file.path);
      child.detail=file.diff;child.turnId=turnId;
      child.changes=new org.json.JSONArray().put(Json.obj("path",file.path,"diff",file.diff));
      item.children.add(child);
    }
    return item;
  }
}
