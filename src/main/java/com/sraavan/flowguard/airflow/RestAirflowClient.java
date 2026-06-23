package com.sraavan.flowguard.airflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to a real Airflow instance via its stable REST API (v1) using HTTP Basic auth.
 *
 * <p>Active in every profile except {@code test}, where a stub is wired instead so the
 * suite never needs a running Airflow.
 */
public class RestAirflowClient implements AirflowClient {

    private final RestClient http;

    public RestAirflowClient(String baseUrl, String username, String password) {
        String basic = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @Override
    public String triggerDagRun(String dagId, Map<String, Object> conf) {
        String runId = "flowguard__" + UUID.randomUUID();
        JsonNode resp = http.post()
                .uri("/api/v1/dags/{dagId}/dagRuns", dagId)
                .body(Map.of("dag_run_id", runId, "conf", conf == null ? Map.of() : conf))
                .retrieve()
                .body(JsonNode.class);
        if (resp != null && resp.hasNonNull("dag_run_id")) {
            return resp.get("dag_run_id").asText();
        }
        return runId;
    }

    @Override
    public int countActiveRuns(String dagId) {
        JsonNode resp = http.get()
                .uri("/api/v1/dags/{dagId}/dagRuns?state=running&state=queued", dagId)
                .retrieve()
                .body(JsonNode.class);
        if (resp != null && resp.hasNonNull("total_entries")) {
            return resp.get("total_entries").asInt();
        }
        return 0;
    }
}
