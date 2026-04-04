package com.distributedjob.scheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jobSchedulerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Job Scheduler API")
                        .version("1.0")
                        .description("""
                                HTTP API for creating and inspecting jobs persisted in PostgreSQL. \
                                New jobs are published to priority topics `job-queue-high` or `job-queue-low` after commit. \
                                Optional REST endpoints under `/api/workers/jobs` support pull-based workers."""));
    }
}
