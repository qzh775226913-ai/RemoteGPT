package app.remotessh;

import static org.junit.Assert.*;
import org.junit.Test;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35, qualifiers="zh-rCN")
public class UsageLimitsTest {
  @Test public void quotaWindowsShowRemainingAndRetainSparseUpdates() {
    UsageLimits usage = new UsageLimits();
    usage.update(Json.obj("rateLimits", Json.obj("primary", Json.obj("usedPercent", 25, "windowDurationMins", 300),
        "secondary", Json.obj("usedPercent", 90, "windowDurationMins", 10080))), true);
    assertEquals("剩余用量 · 5小时 75% · 7天 10%", usage.summary());
    usage.update(Json.obj("rateLimits", Json.obj("primary", Json.obj("usedPercent", 40, "windowDurationMins", 300))), false);
    assertEquals("剩余用量 · 5小时 60% · 7天 10%", usage.summary());
    usage.clear();
    assertFalse(usage.summary().contains("60%"));
  }

  @Test public void bucketsUnknownAndOutOfRangeValuesAreExplicit() {
    UsageLimits usage = new UsageLimits();
    usage.update(Json.obj("rateLimitsByLimitId", Json.obj("other", Json.obj("primary", Json.obj("usedPercent", 120)),
        "codex", Json.obj("primary", Json.obj("usedPercent", 100)))), true);
    assertEquals("剩余用量 · 主额度 0%", usage.summary());
    assertTrue(usage.details().contains("other"));
    usage.update(Json.obj("rateLimits", Json.obj("primary", Json.obj("windowDurationMins", 300))), true);
    assertFalse(usage.summary().contains("100%"));
    assertEquals("远端未提供用量数据", usage.summary());
  }
}
