package app.remotessh;

import static org.junit.Assert.*;
import org.junit.Test;

public class ApprovalBurstTest {
  @Test public void threePriorUniqueApprovalsUnlockOnlyTheFollowingRequests() {
    ForcedApprovalController.Burst burst = new ForcedApprovalController.Burst();
    for (int n = 1; n <= 3; n++) {
      burst.record("request-" + n, n * 1000L);
      assertFalse(burst.offers("request-" + n));
    }
    burst.record("fourth", 4000); assertTrue(burst.offers("fourth"));
    burst.record("fifth", 5000); assertTrue(burst.offers("fifth"));
  }

  @Test public void slidingWindowExpiresAndDuplicatesCannotQualifyAsNewRequests() {
    ForcedApprovalController.Burst burst = new ForcedApprovalController.Burst();
    burst.record("one", 0); burst.record("two", 1000); burst.record("three", 2000);
    burst.record("two", 3000); assertFalse(burst.offers("two"));
    burst.record("four", 30001); assertFalse(burst.offers("four"));
    burst.record("five", 31002); assertFalse(burst.offers("five"));
    burst.record("six", 32003); assertFalse(burst.offers("six"));
    burst.record("seven", 33000); assertTrue(burst.offers("seven"));
    // A duplicate cannot turn an originally hidden button into an offered one.
    burst.record("six", 33001); assertFalse(burst.offers("six"));
  }

  @Test public void exactThirtySecondBoundaryQualifiesAndEligibilitySurvivesReadingThePrompt() {
    ForcedApprovalController.Burst burst = new ForcedApprovalController.Burst();
    burst.record("one", 0); burst.record("two", 10000); burst.record("three", 20000);
    burst.record("four", 30000); assertTrue(burst.offers("four"));
    burst.record("later", 100000); assertFalse(burst.offers("later"));
    assertTrue(burst.offers("four"));
    burst.reset(); assertFalse(burst.offers("four"));
    burst.record("fresh", 100001); assertFalse(burst.offers("fresh"));
  }
}
