package com.sraavan.flowguard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flowguardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("flowguard API")
                .version("0.1.0")
                .description("A policy and governance control-plane for Apache Airflow: "
                        + "gate, rate-limit, and audit every DAG run.")
                .license(new License().name("MIT")));
    }
}
