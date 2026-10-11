package app.remotessh;

import android.content.Context;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Stable per-host/thread storage keys and snapshots; never persists raw launch identities. */
final class ConversationPreferences {
  private final RemoteApp app;
  private final ExecutorService interactionWriter = Executors.newSingleThreadExecutor();

  ConversationPreferences(RemoteApp app) { this.app = app; }

  void restoreInteractions(ConversationState c,String id) {
    c.interactions.restore(app.getSharedPreferences("interaction-history",Context.MODE_PRIVATE).getString(key(id),"[]"));
    c.savedInteractionRevision=c.interactions.revision;
  }
  void saveInteractions() {
    if(app.profile==null || app.threadId.isEmpty() || app.savedInteractionRevision==app.interactions.revision)return;
    app.savedInteractionRevision=app.interactions.revision;
    String key=key(app.threadId),value=app.interactions.serialize();
    interactionWriter.execute(()->app.getSharedPreferences("interaction-history",Context.MODE_PRIVATE).edit().putString(key,value).apply());
  }

  void restorePermission(ConversationState c,String id) {
    String value=app.getSharedPreferences("permission-choices",Context.MODE_PRIVATE).getString(key(id),PermissionMode.INHERIT.name());
    try{c.permission=PermissionMode.valueOf(value);}catch(IllegalArgumentException ignored){c.permission=PermissionMode.INHERIT;}
  }
  void savePermission() {
    if(app.profile!=null)app.getSharedPreferences("permission-choices",Context.MODE_PRIVATE).edit().putString(key(app.threadId),app.permissionMode.name()).apply();
  }
  String key(String id) {
    if (app.profile == null) return "unconnected:" + id;
    String pin = app.vault == null ? null : app.vault.getPin(app.profile.host + ":" + app.profile.port);
    String identity = pin == null ? app.profile.id : pin + "\0" + app.profile.user + "\0" + app.profile.command;
    try {
      // Custom launch commands may contain private values; do not copy them into preference keys.
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
          .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest) + ":" + id;
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }

  void saveServiceTier() {
    if (app.profile == null) return;
    android.content.SharedPreferences.Editor edit = app.getSharedPreferences("service-tiers", Context.MODE_PRIVATE).edit();
    String key = key(app.threadId);
    edit.putString(key + ":choice", app.serviceTier);
    edit.putBoolean(key + ":applied", app.tierOverrideApplied);
    if (app.inheritedTierKnown) edit.putString(key + ":base", app.inheritedServiceTier);
    edit.apply();
  }

  void restoreServiceTier(ConversationState c, String id) {
    android.content.SharedPreferences prefs = app.getSharedPreferences("service-tiers", Context.MODE_PRIVATE);
    String prefix = key(id);
    c.serviceTier = prefs.getString(prefix + ":choice", "");
    c.inheritedTierKnown = prefs.contains(prefix + ":base");
    c.inheritedServiceTier = prefs.getString(prefix + ":base", "");
    c.tierOverrideApplied = prefs.getBoolean(prefix + ":applied", false);
  }

}
