package com.sraavan.flowguard.policy;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A request to trigger a DAG run, as seen by the policy engine.
 *
 * @param dagId        the target DAG
 * @param requestedBy  the principal asking for the run
 * @param confArgs     the run configuration ("conf") supplied by the caller
 * @param at           when the request arrived (injected so policies are testable)
 * @param activeRuns   how many runs of this DAG are currently active
 * @param recentRunsInWindow how many runs this DAG has had within the rate-limit window
 * @param principalRoles roles held by the requester (for allowlist policies)
 */
public record TriggerRequest(
        String dagId,
        String requestedBy,
        Map<String, Object> confArgs,
        Instant at,
        int activeRuns,
        int recentRunsInWindow,
        Set<String> principalRoles) {

    public TriggerRequest {
        // A null-tolerant defensive copy: JSON conf may legitimately contain null values
        // (e.g. {"region": null}), which Map.copyOf rejects.
        confArgs = confArgs == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(confArgs));
        principalRoles = principalRoles == null ? Set.of() : Set.copyOf(principalRoles);
    }
}
