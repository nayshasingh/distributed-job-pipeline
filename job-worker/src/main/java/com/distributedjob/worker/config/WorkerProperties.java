package com.distributedjob.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(
        /**
         * Total execution attempts (first run + Kafka retries). After this many failures, status becomes FAILED.
         */
        int maxExecutionAttempts,
        /**
         * A RUNNING job older than this threshold can be reclaimed in case a worker crashed mid-flight.
         */
        long staleRunningTimeoutSeconds,
        String distributedLockKeyPrefix,
        long distributedLockTtlSeconds
) {
}
