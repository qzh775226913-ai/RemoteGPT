package app.remotessh;

import java.io.IOException;
import org.json.JSONObject;

/** The complete server error is kept for diagnostics, including operation and error data. */
public final class RpcException extends IOException {
  public final String method;
  public final int code;
  public final Object data;

  public RpcException(String method, JSONObject error) {
    super(error.optString("message", "远端请求失败"));
    this.method = method;
    this.code = error.optInt("code", -32603);
    this.data = error.opt("data");
  }
}
