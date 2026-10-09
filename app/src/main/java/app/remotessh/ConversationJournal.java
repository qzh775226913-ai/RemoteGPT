package app.remotessh;

import java.util.*;
import org.json.*;

/** Compatibility for accepted asynchronous tools which have no app-server question event. */
final class ConversationJournal {
  static final String METHOD = "remote/asyncQuestion";
  long offset;
  String turn = "";
  JSONObject context;
  final Map<String, JSONObject> candidates = new LinkedHashMap<>(), pending = new LinkedHashMap<>();
  final Set<String> dismissed = new HashSet<>();

  static boolean savedQuestion(JSONObject request) { return METHOD.equals(request.optString("method")); }
  static boolean question(JSONObject request) { return savedQuestion(request) || request.optString("method").equals("item/tool/requestUserInput"); }

  void apply(JSONObject result, String thread) {
    if(result.optBoolean("reset")) {candidates.clear();pending.clear();context=null;turn="";}
    offset=result.optLong("offset",offset);
    JSONObject latest=result.optJSONObject("context");
    JSONArray records=result.optJSONArray("records");
    if(records!=null)for(int i=0;i<records.length();i++) {
      JSONObject record=records.optJSONObject(i);if(record==null)continue;
      String type=record.optString("type"),call=record.optString("call_id");
      if(type.equals("task_started") || type.equals("user_message") || type.equals("task_interrupted")) {
        candidates.clear();pending.clear();if(type.equals("task_started"))turn=record.optString("turn_id");
      } else if(type.equals("function_call") && !call.isEmpty()) {
        try {
          JSONArray source=new JSONObject(record.optString("arguments")).optJSONArray("questions"),questions=new JSONArray();
          if(source==null || source.length()==0 || source.length()>3)continue;
          for(int k=0;k<source.length();k++) {
            JSONObject q=source.getJSONObject(k);JSONArray options=q.optJSONArray("options"),normalized=new JSONArray();
            if(options!=null)for(int n=0;n<Math.min(options.length(),16);n++) {
              Object option=options.opt(n);
              if(option instanceof String)normalized.put(Json.obj("label",option,"description",""));
              else if(option instanceof JSONObject)normalized.put(option);
            }
            questions.put(Json.obj("id","q"+k,"question",q.optString("title"),"isOther",true,"options",normalized));
          }
          candidates.put(call,Json.obj("id","saved-"+call,"method",METHOD,"params",Json.obj("threadId",thread,"turnId",turn,
              "callId",call,"isBlocking",false,"questions",questions)));
        }catch(JSONException ignored){}
      } else if(type.equals("function_call_output") && record.optBoolean("accepted") && candidates.containsKey(call) && !dismissed.contains(call)) {
        pending.put(call,candidates.get(call));
      }
    }
    if(latest!=null)context=latest;
  }
  void dismiss(JSONObject request) {String call=Json.object(request,"params").optString("callId");dismissed.add(call);pending.remove(call);}
}
