package com.sraavan.flowguard.web;

import com.sraavan.flowguard.domain.DagPolicy;
import com.sraavan.flowguard.policy.PolicyCodec;
import com.sraavan.flowguard.service.GuardService;
import com.sraavan.flowguard.service.GuardService.TriggerOutcome;
import com.sraavan.flowguard.web.Dtos.DecisionResponse;
import com.sraavan.flowguard.web.Dtos.PolicyResponse;
import com.sraavan.flowguard.web.Dtos.RunRecordView;
import com.sraavan.flowguard.web.Dtos.TriggerRequestBody;
import com.sraavan.flowguard.web.Dtos.TriggerResponse;
import com.sraavan.flowguard.web.Dtos.UpsertPolicyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * flowguard control-plane API.
 *
 * <pre>
 * PUT  /api/dags/{dagId}/policy     set/update the policy for a DAG (use "*" for default)
 * GET  /api/dags/{dagId}/policy     view the effective policy for a DAG
 * POST /api/dags/{dagId}/evaluate   dry-run: would this trigger be allowed?
 * POST /api/dags/{dagId}/trigger    evaluate and, if allowed, trigger the run in Airflow
 * GET  /api/dags/{dagId}/history    decision/audit history for a DAG
 * </pre>
 */
@RestController
@RequestMapping("/api/dags")
public class GuardController {

    private final GuardService service;
    private final PolicyCodec codec;

    public GuardController(GuardService service, PolicyCodec codec) {
        this.service = service;
        this.codec = codec;
    }

    @PutMapping("/{dagId}/policy")
    public ResponseEntity<PolicyResponse> upsertPolicy(@PathVariable String dagId,
                                                       @RequestBody UpsertPolicyRequest req) {
        DagPolicy saved = service.upsertPolicy(dagId, req.policy(), req.updatedBy());
        PolicyResponse body = new PolicyResponse(saved.getDagId(),
                codec.fromJson(saved.getPolicyJson()), saved.getUpdatedBy(), saved.getUpdatedAt());
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @GetMapping("/{dagId}/policy")
    public PolicyResponse getPolicy(@PathVariable String dagId) {
        return new PolicyResponse(dagId, service.effectivePolicy(dagId), null, null);
    }

    @PostMapping("/{dagId}/evaluate")
    public DecisionResponse evaluate(@PathVariable String dagId, @RequestBody TriggerRequestBody req) {
        return DecisionResponse.from(
                service.evaluate(dagId, req.requestedBy(), req.conf(), req.roles()));
    }

    @PostMapping("/{dagId}/trigger")
    public ResponseEntity<TriggerResponse> trigger(@PathVariable String dagId,
                                                   @RequestBody TriggerRequestBody req) {
        TriggerOutcome outcome = service.trigger(dagId, req.requestedBy(), req.conf(), req.roles());
        TriggerResponse body = new TriggerResponse(
                outcome.decision().allowed(), outcome.dagRunId(), outcome.decision().violations());
        HttpStatus status = outcome.decision().allowed() ? HttpStatus.ACCEPTED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{dagId}/history")
    public List<RunRecordView> history(@PathVariable String dagId) {
        return service.history(dagId).stream().map(RunRecordView::from).toList();
    }
}
