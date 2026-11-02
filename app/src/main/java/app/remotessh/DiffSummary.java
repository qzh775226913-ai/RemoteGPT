package app.remotessh;

import java.util.*;
import org.json.JSONObject;

/** Counts changed lines from server-provided unified diffs, never from local git state. */
final class DiffSummary {
  static final class File {
    final String path;
    String diff;
    int added, removed;
    File(String path,String diff,int added,int removed){this.path=path;this.diff=diff;this.added=added;this.removed=removed;}
  }
  final List<File> files;
  DiffSummary(List<File> files){this.files=files;}
  int added(){int n=0;for(File f:files)n+=f.added;return n;}
  int removed(){int n=0;for(File f:files)n+=f.removed;return n;}

  static DiffSummary fromItem(ChatItem item) {
    if(item.changes!=null && item.changes.length()>0){
      List<File> files=new ArrayList<>();
      for(int i=0;i<item.changes.length();i++){
        JSONObject change=item.changes.optJSONObject(i);
        if(change==null)continue;
        String path=change.optString("path"),diff=change.optString("diff");
        int[] lines=count(diff);
        if(!path.isEmpty())files.add(new File(path,diff,lines[0],lines[1]));
      }
      return new DiffSummary(files);
    }
    return parse(item.detail);
  }

  static DiffSummary forTurn(List<ChatItem> items,String turnId) {
    ChatItem aggregate=null;
    for(ChatItem item:items)if(item.turnId.equals(turnId) && item.id.equals("diff-"+turnId))aggregate=item;
    if(aggregate!=null && !aggregate.detail.isEmpty())return fromItem(aggregate);
    LinkedHashMap<String,File> files=new LinkedHashMap<>();
    for(ChatItem item:items)if(item.type.equals("fileChange") && item.turnId.equals(turnId)
        && !item.status.equals("failed") && !item.status.equals("declined"))
      for(File source:fromItem(item).files){
        File prior=files.get(source.path);
        if(prior==null)files.put(source.path,new File(source.path,source.diff,source.added,source.removed));
        else {prior.added+=source.added;prior.removed+=source.removed;prior.diff+="\n"+source.diff;}
      }
    return new DiffSummary(new ArrayList<>(files.values()));
  }

  static DiffSummary parse(String diff) {
    List<File> files=new ArrayList<>();
    StringBuilder block=new StringBuilder();String path="";
    for(String line:diff.split("\n",-1)){
      if(line.startsWith("diff --git ")){
        if(!path.isEmpty())files.add(file(path,block.toString()));
        block.setLength(0);
        int b=line.lastIndexOf(" b/");
        path=b<0?"":line.substring(b+3).replaceAll("^\\\"|\\\"$","");
      }
      if(line.startsWith("+++ b/"))path=line.substring(6);
      block.append(line).append('\n');
    }
    if(!path.isEmpty())files.add(file(path,block.toString()));
    return new DiffSummary(files);
  }

  private static File file(String path,String diff){int[] lines=count(diff);return new File(path,diff,lines[0],lines[1]);}
  static int[] count(String diff){
    int add=0,remove=0;
    for(String line:diff.split("\n")){
      if(line.startsWith("+")&&!line.startsWith("+++"))add++;
      else if(line.startsWith("-")&&!line.startsWith("---"))remove++;
    }
    return new int[]{add,remove};
  }
}
