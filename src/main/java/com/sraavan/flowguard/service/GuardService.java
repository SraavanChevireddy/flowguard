package com.sraavan.flowguard.service;

import com.sraavan.flowguard.airflow.AirflowClient;
import com.sraavan.flowguard.domain.DagPolicy;
import com.sraavan.flowguard.domain.RunRecord;
import com.sraavan.flowguard.policy.Policy;
import com.sraavan.flowguard.policy.PolicyCodec;
import com.sraavan.flowguard.policy.PolicyDecision;
import com.sraavan.flowguard.policy.PolicyEngine;
import com.sraavan.flowguard.policy.TriggerRequest;
import com.sraavan.flowguard.repo.DagPolicyRepository;
import com.sraavan.flowguard.repo.RunRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The control-plane core: resolves the effective policy for a DAG, evaluates a trigger
 * request against it, and — only if allowed — forwards the trigger to Airflow. Every
 * decision is persisted as a {@link RunRecord} for audit and rate-limiting.
 *
 * <p>The wildcard DAG id {@code *} holds a default policy applied to any DAG without its
 * own. If neither exists, the policy is {@link Policy#permissive()}.
 */
@Service
public class GuardService {

    public static final String DEFAULT_POLICY_ID = "*";

    private final DagPolicyRepository policies;
    private final RunRecordRepository runs;
    private final AirflowClient airflow;
    private final PolicyEngine engine;
    private final PolicyCodec codec;

    public GuardService(DagPolicyRepository policies, RunRecordRepository runs,
                        AirflowClient airflow, PolicyEngine engine, PolicyCodec codec) {
        this.policies = policies;
        this.runs = runs;
        this.airflow = airflow;
        this.engine = engine;
        this.codec = codec;
    }

    @Transactional
    public DagPolicy upsertPolicy(String dagId, Policy policy, String updatedBy) {
        String json = codec.toJson(policy);
        return policies.findByDagId(dagId)
                .map(existing -> {
                    existing.update(json, updatedBy);
                    return policies.save(existing);
                })
                .orElseGet(() -> policies.save(new DagPolicy(dagId, json, updatedBy)));
    }

    @Transactional(readOnly = true)
    public Policy effectivePolicy(String dagId) {
        return policies.findByDagId(dagId)
                .or(() -> policies.findByDagId(DEFAULT_POLICY_ID))
                .map(p -> codec.fromJson(p.getPolicyJson()))
                .orElseGet(Policy::permissive);
    }

    /** Evaluate without triggering — the dry-run a CI job or operator can call first. */
    @Transactional(readOnly = true)
    public PolicyDecision evaluate(String dagId, String requestedBy,
                                   Map<String, Object> conf, Set<String> roles) {
        Policy policy = effectivePolicy(dagId);
        TriggerRequest req = buildRequest(dagId, requestedBy, conf, roles, policy);
        return engine.evaluate(policy, req);
    }

    /**
     * Evaluate and, if allowed, trigger the DAG in Airflow. Returns the decision plus the
     * assigned dag_run_id when triggered.
     */
    @Transactional
    public TriggerOutcome trigger(String dagId, String requestedBy,
                                  Map<String, Object> conf, Set<String> roles) {
        Policy policy = effectivePolicy(dagId);
        TriggerRequest req = buildRequest(dagId, requestedBy, conf, roles, policy);
        PolicyDecision decision = engine.evaluate(policy, req);

        if (!decision.allowed()) {
            runs.save(new RunRecord(dagId, requestedBy, RunRecord.Outcome.BLOCKED,
                    summarize(decision), null));
            return new TriggerOutcome(decision, null);
        }

        try {
            String dagRunId = airflow.triggerDagRun(dagId, conf);
            runs.save(new RunRecord(dagId, requestedBy, RunRecord.Outcome.ALLOWED,
                    "triggered", dagRunId));
            return new TriggerOutcome(decision, dagRunId);
        } catch (RuntimeException e) {
            runs.save(new RunRecord(dagId, requestedBy, RunRecord.Outcome.FORWARD_FAILED,
                    "airflow error: " + e.getMessage(), null));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<RunRecord> history(String dagId) {
        return runs.findByDagIdOrderByRequestedAtDesc(dagId);
    }

    private TriggerRequest buildRequest(String dagId, String requestedBy,
                                        Map<String, Object> conf, Set<String> roles, Policy policy) {
        int window = policy.windowMinutes() > 0 ? policy.windowMinutes() : 60;
        Instant since = Instant.now().minus(window, ChronoUnit.MINUTES);
        long recent = runs.countAllowedSince(dagId, since);
        int active = safeActiveRuns(dagId);
        return new TriggerRequest(dagId, requestedBy,
                conf == null ? Map.of() : conf, Instant.now(),
                active, (int) recent, roles == null ? Set.of() : roles);
    }

    private int safeActiveRuns(String dagId) {
        try {
            return airflow.countActiveRuns(dagId);
        } catch (RuntimeException e) {
            // If Airflow is unreachable for the count, treat as unknown (0) rather than
            // failing the whole evaluation; the trigger forward will surface real errors.
            return 0;
        }
    }

    private String summarize(PolicyDecision decision) {
        StringBuilder sb = new StringBuilder();
        for (PolicyDecision.Violation v : decision.violations()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(v.rule()).append(": ").append(v.message());
        }
        return sb.toString();
    }

    /** Result of a trigger attempt. {@code dagRunId} is null when blocked. */
    public record TriggerOutcome(PolicyDecision decision, String dagRunId) {
        public Optional<String> dagRunIdOpt() {
            return Optional.ofNullable(dagRunId);
        }
    }
}
