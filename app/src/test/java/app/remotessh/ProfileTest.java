package app.remotessh;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProfileTest {
  private Profile profile() {
    Profile p = new Profile();
    p.host = "host.example";
    p.user = "me";
    return p;
  }

  @Test
  public void credentialsAreNotSerializedByDefault() {
    Profile p = profile();
    p.password = "test-password";
    p.privateKey = "test-key";
    p.passphrase = "test-passphrase";
    Profile restored = Profile.from(p.json());
    assertEquals("", restored.password);
    assertEquals("", restored.privateKey);
    assertEquals("", restored.passphrase);
  }

  @Test
  public void explicitRememberPreservesCredentialsForEncryption() {
    Profile p = profile();
    p.remember = true;
    p.password = "test-password";
    assertEquals(p.password, Profile.from(p.json()).password);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsOutOfRangePorts() {
    Profile p = profile();
    p.port = 65536;
    p.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsRelativeWorkingDirectory() {
    Profile p = profile();
    p.cwd = "~/project";
    p.validate();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsSshUrlInHostField() {
    Profile p = profile();
    p.host = "ssh://host.example";
    p.validate();
  }

  @Test
  public void acceptsIpv6AndAbsoluteDirectory() {
    Profile p = profile();
    p.host = "2001:db8::1";
    p.cwd = "/home/me/my project";
    p.validate();
  }
  @Test public void persistsTailnetAuthenticationModeWithoutSecrets() {
    Profile profile = new Profile(); profile.host = "100.109.221.115"; profile.user = "me"; profile.tailscaleSsh = true;
    profile.validate();
    assertTrue(Profile.from(profile.json()).tailscaleSsh);
    assertFalse(Profile.from(Json.obj("host", "legacy")).tailscaleSsh);
  }
}
