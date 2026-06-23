package com.sraavan.flowguard.web;

import com.sraavan.flowguard.airflow.AirflowClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardWorkflowIntegrationTest {

    /** Records triggers in-memory instead of calling a real Airflow. */
    static class StubAirflowClient implements AirflowClient {
        final AtomicInteger triggered = new AtomicInteger();

        @Override
        public String triggerDagRun(String dagId, Map<String, Object> conf) {
            int n = triggered.incrementAndGet();
            return "run-" + dagId + "-" + n;
        }

        @Override
        public int countActiveRuns(String dagId) {
            return 0;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        AirflowClient stubAirflowClient() {
            return new StubAirflowClient();
        }
    }

    @Autowired
    private MockMvc mvc;

    private void setPolicy(String dagId, String policyJson) throws Exception {
        mvc.perform(put("/api/dags/{dagId}/policy", dagId).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"updatedBy\": \"admin\", \"policy\": " + policyJson + " }"))
                .andExpect(status().isOk());
    }

    @Test
    void allowedTriggerReachesAirflowAndIsAudited() throws Exception {
        setPolicy("orders", """
                { "dagAllowlist": ["orders"], "requiredConfKeys": ["run_date"],
                  "requiredRoles": [], "maxActiveRuns": 0, "maxRunsPerWindow": 0,
                  "windowMinutes": 60, "freezeWindows": [] }""");

        mvc.perform(post("/api/dags/orders/trigger").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "requestedBy": "alice", "conf": { "run_date": "2026-06-23" },
                                  "roles": ["data-eng"] }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.dagRunId", notNullValue()));

        mvc.perform(get("/api/dags/orders/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome", is("ALLOWED")));
    }

    @Test
    void blockedTriggerReturns403AndDoesNotReachAirflow() throws Exception {
        setPolicy("payments", """
                { "dagAllowlist": [], "requiredConfKeys": ["run_date"],
                  "requiredRoles": [], "maxActiveRuns": 0, "maxRunsPerWindow": 0,
                  "windowMinutes": 60, "freezeWindows": [] }""");

        mvc.perform(post("/api/dags/payments/trigger").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"requestedBy\": \"alice\", \"conf\": {}, \"roles\": [] }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.violations[0].rule", is("MISSING_REQUIRED_CONF")));

        mvc.perform(get("/api/dags/payments/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome", is("BLOCKED")));
    }

    @Test
    void evaluateIsDryRunAndDoesNotAudit() throws Exception {
        setPolicy("events", """
                { "dagAllowlist": ["events"], "requiredConfKeys": [], "requiredRoles": [],
                  "maxActiveRuns": 0, "maxRunsPerWindow": 0, "windowMinutes": 60, "freezeWindows": [] }""");

        mvc.perform(post("/api/dags/events/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"requestedBy\": \"alice\", \"conf\": {}, \"roles\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)));

        mvc.perform(get("/api/dags/events/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void defaultPolicyAppliesToDagsWithoutOwnPolicy() throws Exception {
        // Wildcard default: block everything not allowlisted.
        setPolicy("*", """
                { "dagAllowlist": ["only-this"], "requiredConfKeys": [], "requiredRoles": [],
                  "maxActiveRuns": 0, "maxRunsPerWindow": 0, "windowMinutes": 60, "freezeWindows": [] }""");

        mvc.perform(post("/api/dags/random-dag/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"requestedBy\": \"alice\", \"conf\": {}, \"roles\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.violations[0].rule", is("DAG_NOT_ALLOWLISTED")));
    }

    @Test
    void policyCanBeUpdated() throws Exception {
        setPolicy("upd", """
                { "dagAllowlist": [], "requiredConfKeys": ["a"], "requiredRoles": [],
                  "maxActiveRuns": 0, "maxRunsPerWindow": 0, "windowMinutes": 60, "freezeWindows": [] }""");
        setPolicy("upd", """
                { "dagAllowlist": [], "requiredConfKeys": ["b"], "requiredRoles": [],
                  "maxActiveRuns": 0, "maxRunsPerWindow": 0, "windowMinutes": 60, "freezeWindows": [] }""");

        mvc.perform(get("/api/dags/upd/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.requiredConfKeys[0]", is("b")));
    }
}
