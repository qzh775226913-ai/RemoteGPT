package app.remotessh;

import java.util.*;
import org.json.*;

/** A presentation archive; never changes the model's conversation or replays answers. */
final class InteractionHistory {
  final Map<String,JSONObject> entries = new LinkedHashMap<>();
  String archiveTurn = "";
  final Set<String> archivePastTurns=new HashSet<>();
  long revision;
  // Presentation-only fallback anchors; persisted question/answer format stays unchanged.
  private final Map<String,Position> positions=new HashMap<>();
  private static final class Position {
    final String item;final boolean before;
    Position(String item,boolean before){this.item=item;this.before=before;}
  }

  static String key(JSONObject request) {
    JSONObject p=Json.object(request,"params");
    return p.has("callId") ? "call:"+p.optString("callId")
        : "native:"+p.optString("turnId")+":"+p.optString("itemId",String.valueOf(request.opt("id")));
  }
  void question(JSONObject request) {
    if(!ConversationJournal.question(request))return;
    String id=key(request);if(entries.containsKey(id))return;
    JSONObject p=Json.object(request,"params");JSONArray source=p.optJSONArray("questions");
    if(source==null || source.length()==0)return;
    // Copy only presentation fields. Secret values are never written to this archive.
    JSONArray questions=new JSONArray();
    for(int i=0;i<source.length();i++) {
      JSONObject q=source.optJSONObject(i);if(q==null)continue;
      questions.put(Json.obj("id",q.optString("id"),"question",q.optString("question"),
          "options",q.optJSONArray("options"),"isSecret",q.optBoolean("isSecret")));
    }
    entries.put(id,Json.obj("id",id,"turnId",p.optString("turnId"),"questions",questions,"status","pending"));revision++;
  }
  void answered(JSONObject request,JSONObject result) {
    question(request);JSONObject entry=entries.get(key(request));if(entry==null)return;
    JSONObject answers=new JSONObject(),source=Json.object(result,"answers");
    JSONArray questions=entry.optJSONArray("questions");
    for(int i=0;i<questions.length();i++) {
      JSONObject q=questions.optJSONObject(i);if(q==null)continue;
      JSONArray values=Json.object(source,q.optString("id")).optJSONArray("answers");
      if(values!=null)try {answers.put(q.optString("id"),q.optBoolean("isSecret") ? "已提供（不记录内容）" : join(values));}catch(JSONException ignored){}
    }
    update(entry,"answered",answers);
  }
  private static String join(JSONArray values) {List<String> list=new ArrayList<>();for(int i=0;i<values.length();i++)list.add(values.optString(i));return String.join("\n",list);}
  void status(JSONObject request,String status) {JSONObject entry=entries.get(key(request));if(entry!=null&&!entry.optString("status").equals("answered"))update(entry,status,null);}
  private void update(JSONObject entry,String status,JSONObject answers) {
    if(status.equals(entry.optString("status")) && (answers==null || answers.toString().equals(Json.object(entry,"answers").toString())))return;
    try {entry.put("status",status);if(answers!=null)entry.put("answers",answers);revision++;}catch(JSONException ignored){}
  }
  void archive(JSONObject result,String thread) {
    JSONArray records=result.optJSONArray("history");if(records==null)return;
    Map<String,JSONObject> candidates=new LinkedHashMap<>();
    // Candidates persist across scanner chunks through the archive itself.
    for(int i=0;i<records.length();i++) {
      JSONObject r=records.optJSONObject(i);if(r==null)continue;
      String type=r.optString("type"),call=r.optString("call_id");
      if(type.equals("task_started")){if(!archiveTurn.isEmpty())archivePastTurns.add(archiveTurn);archiveTurn=r.optString("turn_id");}
      else if(type.equals("function_call")) {
        try {
          JSONObject args=new JSONObject(r.optString("arguments"));JSONArray qs=args.optJSONArray("questions");if(qs==null||qs.length()==0)continue;
          JSONArray normalized=new JSONArray();
          for(int n=0;n<qs.length();n++) {
            JSONObject q=qs.getJSONObject(n);JSONArray options=q.optJSONArray("options"),out=new JSONArray();
            if(options!=null)for(int k=0;k<options.length();k++) {Object o=options.opt(k);out.put(o instanceof String ? Json.obj("label",o,"description","") : o);}
            normalized.put(Json.obj("id",q.optString("id","q"+n),"question",q.optString("title",q.optString("question")),"options",out,"isSecret",q.optBoolean("isSecret")));
          }
          JSONObject req=Json.obj("method",ConversationJournal.METHOD,"params",Json.obj("callId",call,"threadId",thread,"turnId",archiveTurn,"questions",normalized));
          question(req);candidates.put(call,req);
          JSONObject entry=entries.get("call:"+call);if(entry!=null&&entry.optString("status").equals("pending"))update(entry,"unavailable",null);
        }catch(JSONException ignored){}
      } else if(type.equals("function_call_output")) {
        JSONObject entry=entries.get("call:"+call);if(entry==null)continue;
        if(r.optBoolean("accepted") && entry.optString("status").equals("unavailable"))update(entry,"expired",null);
        JSONObject req=candidates.get(call);
        if(req==null)req=Json.obj("method",ConversationJournal.METHOD,"params",Json.obj("callId",call,"questions",entry.optJSONArray("questions")));
        JSONObject answers=r.optJSONObject("answers");if(answers!=null)answered(req,Json.obj("answers",answers));
      } else if(type.equals("user_answer")){JSONObject match=matchTextAnswer(r.optString("text"),archivePastTurns);if(match!=null)update(entries.get(match.optString("id")),"answered",Json.object(match,"answers"));}
    }
  }
  JSONObject matchTextAnswer(String text) {return matchTextAnswer(text,null);}
  private JSONObject matchTextAnswer(String text,Set<String> precedingTurns) {
    return matchTextAnswer(text,precedingTurns,entry->true);
  }
  private JSONObject matchTextAnswer(String text,Set<String> precedingTurns,java.util.function.Predicate<JSONObject> eligible) {
    if(!text.startsWith("回答上一轮的问题：\n"))return null;
    List<JSONObject> list=new ArrayList<>(entries.values());Collections.reverse(list);
    for(JSONObject entry:list) {
      if(!eligible.test(entry))continue;
      if(precedingTurns!=null && !precedingTurns.contains(entry.optString("turnId")))continue;
      JSONArray qs=entry.optJSONArray("questions");if(qs==null||qs.length()==0)continue;
      int position="回答上一轮的问题：\n".length();JSONObject answers=new JSONObject();boolean matches=true;
      for(int i=0;i<qs.length();i++) {
        JSONObject q=qs.optJSONObject(i);if(q==null){matches=false;break;}
        String prefix=q.optString("question")+"\n";if(!text.startsWith(prefix,position)){matches=false;break;}
        int start=position+prefix.length(),end=text.length();
        if(i+1<qs.length()){end=text.indexOf("\n\n"+qs.optJSONObject(i+1).optString("question")+"\n",start);if(end<0){matches=false;break;}}
        String answer=text.substring(start,end).trim();if(answer.isEmpty()){matches=false;break;}
        try{answers.put(q.optString("id"),q.optBoolean("isSecret") ? "已提供（不记录内容）" : answer);}catch(JSONException ignored){}
        position=end+2;
      }
      if(matches)return Json.obj("id",entry.optString("id"),"questions",qs,"answers",answers,"status","answered");
    }
    return null;
  }
  void recordTextAnswer(String text) {JSONObject match=matchTextAnswer(text);if(match!=null)update(entries.get(match.optString("id")),"answered",Json.object(match,"answers"));}
  List<ChatItem> present(List<ChatItem> messages) {
    Map<Integer,List<ChatItem>> insertions=new HashMap<>();
    Map<String,Integer> ends=new HashMap<>(),finals=new HashMap<>();
    for(int i=0;i<messages.size();i++){ChatItem m=messages.get(i);if(!m.turnId.isEmpty()){ends.put(m.turnId,i+1);if(m.phase.equals("final_answer"))finals.putIfAbsent(m.turnId,i);}}
    Map<String,String> nativeCopies=new HashMap<>();
    for(JSONObject entry:entries.values())if(entry.optString("id").startsWith("native:"))nativeCopies.put(entry.optString("turnId")+":"+String.valueOf(entry.optJSONArray("questions")),entry.optString("id"));
    Map<String,JSONObject> unique=new LinkedHashMap<>();
    Map<String,String> identities=new HashMap<>();
    for(JSONObject entry:entries.values()){
      if(entry.optString("status").equals("unavailable"))continue;
      String signature=entry.optString("turnId")+":"+String.valueOf(entry.optJSONArray("questions"));
      String identity=entry.optString("id").startsWith("native:")?entry.optString("id"):nativeCopies.getOrDefault(signature,entry.optString("id"));
      identities.put(entry.optString("id"),identity);
      JSONObject previous=unique.get(identity);
      if(previous==null || rank(entry.optString("status"))>rank(previous.optString("status")))unique.put(identity,entry);
    }
    Map<String,Set<String>> sources=new HashMap<>();
    for(Map.Entry<String,String> identity:identities.entrySet())sources.computeIfAbsent(identity.getValue(),k->new HashSet<>()).add(identity.getKey());
    Map<String,Integer> placements=new HashMap<>();
    for(Map.Entry<String,JSONObject> displayed:unique.entrySet()) {
      Integer at=position(displayed.getKey(),displayed.getValue(),sources.get(displayed.getKey()),messages,finals,ends);
      if(at!=null)placements.put(displayed.getKey(),at);
    }
    // Fold App-formatted answer messages into their question card. Only suppress a
    // duplicate when its question is present; retain the canonical model history.
    Map<String,JSONObject> textAnswers=new HashMap<>();Set<ChatItem> duplicates=new HashSet<>();Set<String> precedingTurns=new HashSet<>();
    for(int i=0;i<messages.size();i++) {
      ChatItem m=messages.get(i);final int index=i;
      if(m.user()) {
        JSONObject answer=matchTextAnswer(m.text,precedingTurns,entry->{
          Integer at=placements.get(identities.get(entry.optString("id")));return at!=null && at<=index;
        });
        if(answer!=null) {
          String identity=identities.get(answer.optString("id"));JSONObject entry=unique.get(identity);
          if(entry!=null && ends.containsKey(entry.optString("turnId"))) {textAnswers.put(identity,answer);duplicates.add(m);}
        }
      }
      if(!m.turnId.isEmpty())precedingTurns.add(m.turnId);
    }
    for(Map.Entry<String,JSONObject> displayed:unique.entrySet()) {
      JSONObject entry=displayed.getValue();
      if(entry.optString("status").equals("unavailable"))continue;
      String turn=entry.optString("turnId");Integer at=placements.get(displayed.getKey());
      if(at==null)continue; // Never attach a different thread's record to the current history.
      JSONObject answer=textAnswers.get(displayed.getKey());
      if(answer!=null)try {entry=new JSONObject(entry.toString());entry.put("status","answered");entry.put("answers",Json.object(answer,"answers"));}catch(JSONException ignored){}
      // Async delivery also emits an AgentMessage with the tool's call ID and
      // question text. It is the question itself, not an additional reply.
      // Require both identity and the full text, so genuine replies stay visible.
      Set<String> sourceIds=sources.get(displayed.getKey());
      for(ChatItem m:messages)if(questionEcho(m,entry,sourceIds))duplicates.add(m);
      ChatItem q=new ChatItem("interaction-"+displayed.getKey(),"question","");q.interaction=entry;q.turnId=turn;q.status=entry.optString("status");
      insertions.computeIfAbsent(at,k->new ArrayList<>()).add(q);
    }
    List<ChatItem> result=new ArrayList<>();
    for(int i=0;i<=messages.size();i++) {
      result.addAll(insertions.getOrDefault(i,Collections.emptyList()));if(i==messages.size())break;
      ChatItem m=messages.get(i);
      if(!duplicates.contains(m))result.add(m);
    }
    return result;
  }
  private Integer position(String identity,JSONObject entry,Set<String> sourceIds,List<ChatItem> messages,
      Map<String,Integer> finals,Map<String,Integer> ends) {
    String turn=entry.optString("turnId");
    // A tool/call identity locates the original question even after later replies,
    // answers, more questions in the same turn, or an archive restore.
    for(int i=0;i<messages.size();i++) {
      ChatItem m=messages.get(i);if(!m.turnId.equals(turn))continue;
      if(sourceIds.contains("call:"+m.id) || sourceIds.contains("native:"+turn+":"+m.id)) {
        boolean before=questionEcho(m,entry,sourceIds);
        positions.put(identity,new Position(m.id,before));return i+(before?0:1);
      }
    }
    Position saved=positions.get(identity);
    if(saved!=null)for(int i=0;i<messages.size();i++) {
      ChatItem m=messages.get(i);
      if(m.id.equals(saved.item) && m.turnId.equals(turn))return i+(saved.before?0:1);
    }
    Integer at=finals.getOrDefault(turn,ends.get(turn));
    if(at==null)return null;
    boolean before=finals.containsKey(turn);int index=before?at:at-1;
    if(index>=0 && index<messages.size())positions.put(identity,new Position(messages.get(index).id,before));
    return at;
  }
  private static boolean questionEcho(ChatItem m,JSONObject entry,Set<String> sourceIds) {
    if(!m.type.equals("agentMessage") || !m.turnId.equals(entry.optString("turnId")) || !m.images.isEmpty())return false;
    if(!sourceIds.contains("call:"+m.id) && !sourceIds.contains("native:"+m.turnId+":"+m.id))return false;
    JSONArray qs=entry.optJSONArray("questions");if(qs==null || qs.length()==0)return false;
    List<String> titles=new ArrayList<>(),withOptions=new ArrayList<>();
    for(int i=0;i<qs.length();i++) {
      JSONObject q=qs.optJSONObject(i);if(q==null || q.optString("question").trim().isEmpty())return false;
      String title=q.optString("question");titles.add(title);withOptions.add(title);
      JSONArray options=q.optJSONArray("options");
      if(options!=null)for(int n=0;n<options.length();n++) {
        Object option=options.opt(n);String label=option instanceof JSONObject ? ((JSONObject)option).optString("label") : option instanceof String ? (String)option : "";
        if(label.trim().isEmpty())return false;
        withOptions.add(label);
      }
    }
    String text=echoText(m.text);
    return text.equals(echoText(String.join("\n",titles))) || text.equals(echoText(String.join("\n",withOptions)));
  }
  private static String echoText(String value) {
    List<String> lines=new ArrayList<>();
    for(String line:value.replace("\r\n","\n").split("\n")) {
      line=line.trim().replaceFirst("^(?:[-*+•]|[0-9]+[.)])\\s+","");
      if(line.startsWith("**") && line.endsWith("**") && line.length()>4)line=line.substring(2,line.length()-2).trim();
      if(!line.isEmpty())lines.add(line);
    }
    return String.join("\n",lines);
  }
  private static int rank(String status){return status.equals("answered")?5:status.equals("pending")?4:status.equals("skipped")?3:2;}
  String serialize() {return new JSONArray(entries.values()).toString();}
  void restore(String value) {
    try{JSONArray saved=new JSONArray(value);for(int i=0;i<saved.length();i++){JSONObject e=saved.optJSONObject(i);if(e!=null&&!e.optString("id").isEmpty())entries.put(e.optString("id"),e);}}catch(JSONException ignored){}
  }
}
