package app.remotessh;

import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;

public class ServiceTierOptionsTest {
  @Test public void preservesServerIdsNamesDescriptionsAndFutureTiers() {
    JSONObject m = Json.obj("serviceTiers", new JSONArray()
        .put(Json.obj("id", "priority", "name", "Fast", "description", "server usage"))
        .put(Json.obj("id", "future-tier", "name", "Future speed", "description", "future usage"))
        .put(Json.obj("id", "priority", "name", "duplicate")));
    assertEquals(2, ServiceTierOptions.tiers(m).size());
    assertEquals("server usage", ServiceTierOptions.tiers(m).get(0).optString("description"));
    assertEquals("priority", ServiceTierOptions.supportedId(m, "fast"));
    assertEquals("Fast", ServiceTierOptions.label("priority", m));
    assertEquals("Future speed", ServiceTierOptions.label("future-tier", m));
    assertEquals("", ServiceTierOptions.supportedId(m, "invented"));
  }
  @Test public void exactIdsAndRemoteLabelsWinWhenBothFastAliasesAreAdvertised() {
    JSONObject m = Json.obj("serviceTiers", new JSONArray()
        .put(Json.obj("id", "fast", "name", "Fast 2x", "description", "fast details"))
        .put(Json.obj("id", "priority", "name", "Priority 3x", "description", "priority details"))
        .put(Json.obj("id", "economy", "name", "Economy", "description", "Lower usage")));
    assertEquals("priority", ServiceTierOptions.supportedId(m, "priority"));
    assertEquals("fast", ServiceTierOptions.supportedId(m, "fast"));
    assertEquals("Priority 3x", ServiceTierOptions.label("priority", m));
    assertEquals("priority details", ServiceTierOptions.description("priority", m));
    assertEquals("Lower usage", ServiceTierOptions.description("economy", m));
  }
  @Test public void authoritativeEmptyModernListDoesNotResurrectLegacyFast() {
    JSONObject m = Json.obj("serviceTiers", new JSONArray(), "additionalSpeedTiers", new JSONArray().put("fast"));
    assertTrue(ServiceTierOptions.tiers(m).isEmpty());
  }
  @Test public void legacyValuesRemainServerValuesAndMissingMetadataIsUnknown() {
    JSONObject m = Json.obj("additionalSpeedTiers", new JSONArray().put("fast").put("fast").put(JSONObject.NULL).put(""));
    assertEquals(1, ServiceTierOptions.tiers(m).size());
    assertEquals("fast", ServiceTierOptions.supportedId(m, "priority"));
    assertTrue(ServiceTierOptions.tiers(new JSONObject()).isEmpty());
    assertFalse(ServiceTierOptions.hasProtocolMetadata(new JSONArray().put(Json.obj("model", "new-model"))));
  }
}
