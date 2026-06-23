package com.sraavan.flowguard.policy;

import java.util.List;

/**
 * The outcome of evaluating a {@link Policy} against a {@link TriggerRequest}.
 *
 * @param allowed    whether the request may proceed to Airflow
 * @param violations the reasons it was blocked (empty when allowed)
 */
public record PolicyDecision(boolean allowed, List<Violation> violations) {

    public PolicyDecision {
        violations = List.copyOf(violations);
    }

    public static PolicyDecision allow() {
        return new PolicyDecision(true, List.of());
    }

    public static PolicyDecision deny(List<Violation> violations) {
        return new PolicyDecision(false, violations);
    }

    /**
     * A single policy violation.
     *
     * @param rule    machine-readable rule id (e.g. {@code DAG_NOT_ALLOWLISTED})
     * @param message human-readable explanation
     */
    public record Violation(String rule, String message) {
    }
}
