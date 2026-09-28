package app.remotessh;

import static org.junit.Assert.*;

import org.junit.Test;

public class RemoteDirectoryTest {
  @Test
  public void supportsUnicodeAndSpacesWithoutShellInterpretation() {
    assertEquals(
        "/projects/中文 project; echo", RemoteDirectory.child("/projects", "中文 project; echo"));
    assertEquals("/work", RemoteDirectory.child("/", "work"));
    assertEquals("/", RemoteDirectory.parent("/work"));
  }

  @Test
  public void refusesTraversalAndEmptyNames() {
    for (String name : new String[] {"", " ", ".", "..", "a/b", "x\0y"}) {
      try {
        RemoteDirectory.child("/projects", name);
        fail(name);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void projectPathsSurviveProfileRoundTrip() throws Exception {
    Profile p = new Profile();
    p.cwd = "/projects/work";
    p.projects.add("/projects/中文");
    Profile restored = Profile.from(p.json());
    assertTrue(restored.projects.contains(p.cwd));
    assertTrue(restored.projects.contains("/projects/中文"));
  }
}
