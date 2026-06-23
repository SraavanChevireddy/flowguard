package com.sraavan.flowguard.web;

import com.sraavan.flowguard.domain.RunRecord;
import com.sraavan.flowguard.policy.Policy;
import com.sraavan.flowguard.policy.PolicyDecision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API request/response payloads. */
public final class Dtos {

    private Dtos() {
    }

    public record UpsertPolicyRequest(String updatedBy, Policy policy) {
    }

    public record PolicyResponse(String dagId, Policy policy, String updatedBy, Instant updatedAt) {
    }

    public record TriggerRequestBody(
            String requestedBy, Map<String, Object> conf, Set<String> roles) {
    }

    public record DecisionResponse(boolean allowed, List<PolicyDecision.Violation> violations) {
        public static DecisionResponse from(PolicyDecision d) {
            return new DecisionResponse(d.allowed(), d.violations());
        }
    }

    public record TriggerResponse(boolean allowed, String dagRunId,
                                  List<PolicyDecision.Violation> violations) {
    }

    public record RunRecordView(String dagId, String requestedBy, String outcome,
                                String detail, String dagRunId, Instant requestedAt) {
        public static RunRecordView from(RunRecord r) {
            return new RunRecordView(r.getDagId(), r.getRequestedBy(), r.getOutcome().name(),
                    r.getDetail(), r.getDagRunId(), r.getRequestedAt());
        }
    }
}
