package app.remotessh;

import android.content.*;
import android.net.Uri;
import java.util.List;

/** Explicit immutable PendingIntents only; this receiver is not exported. */
public final class LiveActionReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (intent == null || !LiveStatusNotifications.ACTION.equals(intent.getAction())) return;
    Uri data = intent.getData();
    if (data == null || !"remotegpt".equals(data.getScheme()) || !"live".equals(data.getHost())) return;
    List<String> parts = data.getPathSegments();
    if (parts.size() != 2) return;
    RemoteApp app = (RemoteApp) context.getApplicationContext();
    app.liveStatus.handle(parts.get(0), parts.get(1));
  }
}
