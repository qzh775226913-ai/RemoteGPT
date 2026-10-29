package app.remotessh;

import java.util.function.Supplier;
import org.json.JSONObject;

/** Provenance for client-authored diagnostic UI; raw exception/server text is never translated. */
final class DiagnosticText {
  final String raw;
  private final Supplier<String> localized;
  private DiagnosticText(String raw, Supplier<String> localized) { this.raw=raw; this.localized=localized; }
  static DiagnosticText connection(Throwable error, String phase, int exit, String stderr) {
    Throwable snapshot = snapshot(error);
    return new DiagnosticText(ConnectionFailure.describe(snapshot,phase,exit,stderr),
        () -> ConnectionFailure.describe(snapshot,phase,exit,stderr,true));
  }
  static DiagnosticText operation(String method, JSONObject params, Throwable error) {
    JSONObject snapshot;
    try {
      snapshot=new JSONObject();
      for(String key : new String[]{"threadId","path"})
        if(params.has(key))snapshot.put(key,params.optString(key));
    }
    catch (org.json.JSONException invalid) { throw new IllegalArgumentException(invalid); }
    Throwable failure = snapshot(error);
    return new DiagnosticText(ConnectionFailure.operationError(method,snapshot,failure),
        () -> ConnectionFailure.operationError(method,snapshot,failure,true));
  }
  private static Throwable snapshot(Throwable failure) {
    Throwable unwrapped = ConnectionFailure.unwrap(failure);
    if (unwrapped instanceof RpcException) {
      RpcException rpc = (RpcException)unwrapped;
      Object data=rpc.data;
      if(data instanceof JSONObject || data instanceof org.json.JSONArray)data=data.toString();
      return new RpcException(rpc.method,Json.obj("code",rpc.code,"message",rpc.getMessage(),"data",data));
    }
    return unwrapped;
  }
  static String display(String raw, DiagnosticText diagnostic) {
    // Only the exact String created here has trusted local diagnostic provenance.
    return diagnostic!=null && diagnostic.raw==raw ? diagnostic.localized.get() : raw;
  }
  static String text(boolean localized, int id, String original) {
    return localized ? UiText.get(id) : original;
  }
  static String phase(boolean localized, String phase) {
    if(!localized)return phase;
    if (phase.equals("初始化远端程序")) return UiText.get(R.string.diagnostic_phase_initializing);
    if (phase.equals("SSH 连接与认证")) return UiText.get(R.string.diagnostic_phase_ssh);
    String value=UiPresentation.status(phase+"…");
    return value.endsWith("…") ? value.substring(0,value.length()-1) : value;
  }
}
