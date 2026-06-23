package com.sraavan.flowguard.policy;

import java.time.ZoneId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a {@link Policy} against a {@link TriggerRequest}. Pure and dependency-free
 * so the governance rules can be exhaustively unit-tested without Airflow or a database.
 *
 * <p>All applicable rules are evaluated (the decision collects <em>every</em> violation
 * rather than short-circuiting) so callers can show the full picture of why a trigger
 * was blocked.
 */
public final class PolicyEngine {

    private final ZoneId zone;

    public PolicyEngine(ZoneId zone) {
        this.zone = zone;
    }

    public PolicyEngine() {
        this(ZoneId.of("UTC"));
    }

    public PolicyDecision evaluate(Policy policy, TriggerRequest req) {
        List<PolicyDecision.Violation> v = new ArrayList<>();

        if (!policy.dagAllowlist().isEmpty() && !policy.dagAllowlist().contains(req.dagId())) {
            v.add(new PolicyDecision.Violation("DAG_NOT_ALLOWLISTED",
                    "DAG '" + req.dagId() + "' is not on the allowlist"));
        }

        for (String key : policy.requiredConfKeys()) {
            if (!req.confArgs().containsKey(key) || req.confArgs().get(key) == null) {
                v.add(new PolicyDecision.Violation("MISSING_REQUIRED_CONF",
                        "Required conf key '" + key + "' is missing"));
            }
        }

        if (!policy.requiredRoles().isEmpty()) {
            boolean hasRole = req.principalRoles().stream().anyMatch(policy.requiredRoles()::contains);
            if (!hasRole) {
                v.add(new PolicyDecision.Violation("INSUFFICIENT_ROLE",
                        "Requester lacks any of the required roles " + policy.requiredRoles()));
            }
        }

        if (policy.maxActiveRuns() > 0 && req.activeRuns() >= policy.maxActiveRuns()) {
            v.add(new PolicyDecision.Violation("MAX_ACTIVE_RUNS",
                    "DAG already has " + req.activeRuns() + " active run(s); limit is "
                            + policy.maxActiveRuns()));
        }

        if (policy.maxRunsPerWindow() > 0 && req.recentRunsInWindow() >= policy.maxRunsPerWindow()) {
            v.add(new PolicyDecision.Violation("RATE_LIMITED",
                    "DAG hit the rate limit of " + policy.maxRunsPerWindow() + " run(s) per "
                            + policy.windowMinutes() + "m window"));
        }

        LocalTime localNow = req.at().atZone(zone).toLocalTime();
        for (Policy.FreezeWindow fw : policy.freezeWindows()) {
            if (fw.contains(localNow)) {
                v.add(new PolicyDecision.Violation("FREEZE_WINDOW",
                        "Triggering is frozen (" + fw.start() + "-" + fw.end() + "): " + fw.reason()));
            }
        }

        return v.isEmpty() ? PolicyDecision.allow() : PolicyDecision.deny(v);
    }
}
