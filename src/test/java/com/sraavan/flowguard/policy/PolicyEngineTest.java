package com.sraavan.flowguard.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine(ZoneId.of("UTC"));

    private TriggerRequest req(String dagId, Map<String, Object> conf, int active,
                               int recent, Set<String> roles, Instant at) {
        return new TriggerRequest(dagId, "alice", conf, at, active, recent, roles);
    }

    private Instant utc(int hour, int minute) {
        return ZonedDateTime.of(2026, 6, 23, hour, minute, 0, 0, ZoneId.of("UTC")).toInstant();
    }

    @Test
    void permissivePolicyAllowsEverything() {
        PolicyDecision d = engine.evaluate(Policy.permissive(),
                req("anything", Map.of(), 99, 99, Set.of(), utc(3, 0)));
        assertTrue(d.allowed());
        assertTrue(d.violations().isEmpty());
    }

    @Test
    void allowlistBlocksUnknownDag() {
        Policy p = new Policy(Set.of("orders", "payments"), Set.of(), Set.of(), 0, 0, 60, List.of());
        assertFalse(engine.evaluate(p, req("sneaky", Map.of(), 0, 0, Set.of(), utc(3, 0))).allowed());
        assertTrue(engine.evaluate(p, req("orders", Map.of(), 0, 0, Set.of(), utc(3, 0))).allowed());
    }

    @Test
    void missingRequiredConfIsReported() {
        Policy p = new Policy(Set.of(), Set.of("run_date", "region"), Set.of(), 0, 0, 60, List.of());
        PolicyDecision d = engine.evaluate(p,
                req("orders", Map.of("run_date", "2026-06-23"), 0, 0, Set.of(), utc(3, 0)));
        assertFalse(d.allowed());
        assertEquals(1, d.violations().size());
        assertEquals("MISSING_REQUIRED_CONF", d.violations().get(0).rule());
    }

    @Test
    void nullConfValueCountsAsMissing() {
        Policy p = new Policy(Set.of(), Set.of("region"), Set.of(), 0, 0, 60, List.of());
        Map<String, Object> conf = new java.util.HashMap<>();
        conf.put("region", null);
        assertFalse(engine.evaluate(p, req("orders", conf, 0, 0, Set.of(), utc(3, 0))).allowed());
    }

    @Test
    void requiredRolesEnforced() {
        Policy p = new Policy(Set.of(), Set.of(), Set.of("data-admin"), 0, 0, 60, List.of());
        assertFalse(engine.evaluate(p,
                req("orders", Map.of(), 0, 0, Set.of("viewer"), utc(3, 0))).allowed());
        assertTrue(engine.evaluate(p,
                req("orders", Map.of(), 0, 0, Set.of("viewer", "data-admin"), utc(3, 0))).allowed());
    }

    @Test
    void maxActiveRunsBlocksAtLimit() {
        Policy p = new Policy(Set.of(), Set.of(), Set.of(), 2, 0, 60, List.of());
        assertTrue(engine.evaluate(p, req("orders", Map.of(), 1, 0, Set.of(), utc(3, 0))).allowed());
        assertFalse(engine.evaluate(p, req("orders", Map.of(), 2, 0, Set.of(), utc(3, 0))).allowed());
    }

    @Test
    void rateLimitBlocksWhenWindowExceeded() {
        Policy p = new Policy(Set.of(), Set.of(), Set.of(), 0, 5, 60, List.of());
        assertTrue(engine.evaluate(p, req("orders", Map.of(), 0, 4, Set.of(), utc(3, 0))).allowed());
        PolicyDecision d = engine.evaluate(p, req("orders", Map.of(), 0, 5, Set.of(), utc(3, 0)));
        assertFalse(d.allowed());
        assertEquals("RATE_LIMITED", d.violations().get(0).rule());
    }

    @Test
    void freezeWindowBlocksInsideRange() {
        Policy p = new Policy(Set.of(), Set.of(), Set.of(), 0, 0, 60,
                List.of(new Policy.FreezeWindow(LocalTime.of(9, 0), LocalTime.of(17, 0), "business hours")));
        assertFalse(engine.evaluate(p, req("orders", Map.of(), 0, 0, Set.of(), utc(12, 0))).allowed());
        assertTrue(engine.evaluate(p, req("orders", Map.of(), 0, 0, Set.of(), utc(20, 0))).allowed());
    }

    @Test
    void freezeWindowAcrossMidnight() {
        Policy.FreezeWindow overnight = new Policy.FreezeWindow(
                LocalTime.of(22, 0), LocalTime.of(6, 0), "overnight freeze");
        assertTrue(overnight.contains(LocalTime.of(23, 30)));
        assertTrue(overnight.contains(LocalTime.of(2, 0)));
        assertFalse(overnight.contains(LocalTime.of(12, 0)));
    }

    @Test
    void multipleViolationsAllCollected() {
        Policy p = new Policy(Set.of("orders"), Set.of("run_date"), Set.of("admin"), 1, 0, 60,
                List.of(new Policy.FreezeWindow(LocalTime.of(0, 0), LocalTime.of(23, 59), "frozen")));
        PolicyDecision d = engine.evaluate(p,
                req("other", Map.of(), 5, 0, Set.of("viewer"), utc(12, 0)));
        assertFalse(d.allowed());
        // allowlist + missing conf + role + active runs + freeze = 5
        assertEquals(5, d.violations().size());
    }
}
