package com.sraavan.flowguard.policy;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * The governance policy applied to DAG-run requests. A policy is a set of independent
 * constraints; a request must satisfy all of them to be allowed.
 *
 * <p>Any field left null/empty means "that constraint is not enforced", so a policy can
 * be as light or strict as needed.
 *
 * @param dagAllowlist        if non-empty, only these DAG ids may be triggered through flowguard
 * @param requiredConfKeys    conf keys that must be present (and non-null) on every request
 * @param requiredRoles       if non-empty, the requester must hold at least one of these roles
 * @param maxActiveRuns       if &gt; 0, reject when this many runs are already active
 * @param maxRunsPerWindow    if &gt; 0, reject when this many runs already happened in the window
 * @param windowMinutes       the rate-limit window length
 * @param freezeWindows       time-of-day ranges during which triggering is frozen (e.g. business hours)
 */
public record Policy(
        Set<String> dagAllowlist,
        Set<String> requiredConfKeys,
        Set<String> requiredRoles,
        int maxActiveRuns,
        int maxRunsPerWindow,
        int windowMinutes,
        List<FreezeWindow> freezeWindows) {

    public Policy {
        dagAllowlist = dagAllowlist == null ? Set.of() : Set.copyOf(dagAllowlist);
        requiredConfKeys = requiredConfKeys == null ? Set.of() : Set.copyOf(requiredConfKeys);
        requiredRoles = requiredRoles == null ? Set.of() : Set.copyOf(requiredRoles);
        freezeWindows = freezeWindows == null ? List.of() : List.copyOf(freezeWindows);
    }

    /** A daily time-of-day range during which triggering is blocked. */
    public record FreezeWindow(LocalTime start, LocalTime end, String reason) {

        /** @return true if {@code t} falls inside this window (handles ranges crossing midnight). */
        public boolean contains(LocalTime t) {
            if (start.equals(end)) {
                return false;
            }
            if (start.isBefore(end)) {
                return !t.isBefore(start) && t.isBefore(end);
            }
            // window wraps past midnight, e.g. 22:00 -> 06:00
            return !t.isBefore(start) || t.isBefore(end);
        }
    }

    /** A permissive default that enforces nothing. */
    public static Policy permissive() {
        return new Policy(Set.of(), Set.of(), Set.of(), 0, 0, 60, List.of());
    }
}
