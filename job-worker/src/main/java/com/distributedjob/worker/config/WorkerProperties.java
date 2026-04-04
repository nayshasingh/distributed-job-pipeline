package com.distributedjob.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(
        /**
         * Total execution attempts (first run + Kafka retries). After this many failures, status becomes FAILED.
         */
        int maxExecutionAttempts,
        String distributedLockKeyPrefix,
        long distributedLockTtlSeconds
) {
}
