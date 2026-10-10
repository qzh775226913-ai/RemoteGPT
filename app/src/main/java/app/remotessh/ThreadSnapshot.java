package app.remotessh;

import java.util.*;
import org.json.*;

/** Convert history on the RPC reader before posting it, preserving event order. */
final class ThreadSnapshot {
  final JSONObject response;
  final List<List<ChatItem>> turns=new ArrayList<>();
  RuntimeException failure;
  ThreadSnapshot(JSONObject response) {
    this.response=response;
    try {
      JSONArray source=Json.object(response,"thread").optJSONArray("turns");
      if(source!=null)for(int i=0;i<source.length();i++) {
        List<ChatItem> converted=new ArrayList<>();turns.add(converted);
        JSONObject turn=source.optJSONObject(i);if(turn==null)continue;
        JSONArray items=turn.optJSONArray("items");if(items==null)continue;
        for(int n=0;n<items.length();n++) {
          JSONObject item=items.optJSONObject(n);if(item==null)continue;
          ChatItem message=ChatItem.from(item);message.turnId=turn.optString("id");converted.add(message);
        }
      }
    }catch(RuntimeException e){failure=e;}
  }
}
