package app.remotessh;

import static org.junit.Assert.*;
import org.junit.Test;

public class RemoteVersionTest {
  @Test public void handshakeReadsServerBuildNotAndroidClientVersion() {
    assertEquals("0.156.1", RemoteVersion.fromHandshake(Json.obj("userAgent",
        "remote_ssh_android/0.156.1 (Linux 6.0; x86_64) dumb (remote_ssh_android; 1.0.27)")));
    assertEquals("", RemoteVersion.fromHandshake(Json.obj("userAgent", "unknown")));
  }
  @Test public void comparesNumericVersionsAndPrereleases() {
    assertTrue(RemoteVersion.newer("0.100.0", "0.99.9"));
    assertTrue(RemoteVersion.newer("1.0.0", "0.999.0"));
    assertTrue(RemoteVersion.newer("1.0.0", "1.0.0-alpha.99"));
    assertTrue(RemoteVersion.newer("1.0.0-alpha.10", "1.0.0-alpha.2"));
    assertFalse(RemoteVersion.newer("0.156.0", "0.157.0-alpha.1"));
    assertFalse(RemoteVersion.newer("1.0.0+new", "1.0.0+old"));
    assertFalse(RemoteVersion.newer("invalid", "1.0.0"));
  }
  @Test public void installedUpdateDoesNotMisreportLiveServerAsUpdated() {
    RemoteVersion v = new RemoteVersion(); v.running = "0.100.0";
    v.accept(Json.obj("installed", "0.101.0", "latest", "0.101.0", "manager", "npm", "path", "/codex"));
    assertTrue(v.needsRestart()); assertFalse(v.updateAvailable());
    assertTrue(v.label().contains("Codex 0.100.0"));
    assertTrue(v.label().contains("待重启"));
    assertFalse(v.label().contains("已是最新版"));
  }
  @Test public void updateRequiresKnownTargetAndInstallation() {
    RemoteVersion v = new RemoteVersion(); v.running = "0.100.0";
    v.accept(Json.obj("installed", "0.100.0", "latest", "0.101.0"));
    assertTrue(v.updateAvailable()); assertFalse(v.canUpdate());
    assertTrue(v.label().contains("现版本 0.100.0 → 最新版 0.101.0"));
    v.manager = "npm"; v.path = "/codex"; assertTrue(v.canUpdate());
    v.updating = true; assertFalse(v.canUpdate());
  }
  @Test public void offlineCheckDoesNotClaimLatest() {
    RemoteVersion v = new RemoteVersion(); v.running = "0.100.0";
    v.accept(Json.obj("installed", "0.100.0", "checkError", "offline"));
    assertTrue(v.label().contains("检查失败"));
    assertFalse(v.label().contains("已是最新版"));
  }
}
