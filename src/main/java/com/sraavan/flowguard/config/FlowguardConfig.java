package com.sraavan.flowguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sraavan.flowguard.airflow.AirflowClient;
import com.sraavan.flowguard.airflow.RestAirflowClient;
import com.sraavan.flowguard.policy.PolicyCodec;
import com.sraavan.flowguard.policy.PolicyEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class FlowguardConfig {

    @Bean
    public PolicyEngine policyEngine(@Value("${flowguard.timezone:UTC}") String tz) {
        return new PolicyEngine(ZoneId.of(tz));
    }

    @Bean
    public PolicyCodec policyCodec(ObjectMapper mapper) {
        return new PolicyCodec(mapper);
    }

    /**
     * The real Airflow client. Tests register their own {@link AirflowClient} bean, so
     * {@code @ConditionalOnMissingBean} lets the stub take precedence in the test context.
     */
    @Bean
    @ConditionalOnMissingBean(AirflowClient.class)
    public AirflowClient airflowClient(
            @Value("${flowguard.airflow.base-url:http://localhost:8080}") String baseUrl,
            @Value("${flowguard.airflow.username:airflow}") String username,
            @Value("${flowguard.airflow.password:airflow}") String password) {
        return new RestAirflowClient(baseUrl, username, password);
    }
}
