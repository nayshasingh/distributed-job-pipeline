package com.distributedjob.worker.service;

import com.distributedjob.worker.entity.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulates domain work (email, report, etc.) for demonstration.
 *
 * <p><b>Testing the pipeline:</b>
 * <ul>
 *   <li>{@code EMAIL}, {@code REPORT}, generic types → succeed immediately (happy path).</li>
 *   <li>{@code SLOW_JOB} → sleeps (default 8s) so you can see RUNNING in the UI while work is "in flight".</li>
 *   <li>{@code FAILING_JOB} → always throws; exercises Kafka retries and eventual FAILED.</li>
 * </ul>
 */
@Component
public class MockJobTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(MockJobTaskExecutor.class);

    private static final long DEFAULT_SLOW_MS = 8_000L;
    private static final long MAX_SLOW_MS = 60_000L;

    private final ObjectMapper objectMapper;

    public MockJobTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void execute(Job job) {
        String type = job.getJobType() == null ? "" : job.getJobType().trim();
        log.info("Executing {} priority job {} type={} payloadChars={}",
                job.getPriority(),
                job.getId(),
                type,
                job.getPayload() == null ? 0 : job.getPayload().length());

        switch (type.toUpperCase()) {
            case "EMAIL" -> mockSendEmail(job);
            case "REPORT" -> mockGenerateReport(job);
            case "SLOW_JOB" -> mockSlowJob(job);
            case "FAILING_JOB" -> throw new IllegalStateException(
                    "Intentional mock failure (use job type FAILING_JOB to exercise retries)");
            default -> mockGenericTask(job);
        }
    }

    /**
     * Sleeps so status stays RUNNING long enough to observe async behavior (refresh the dashboard).
     * Payload optional: {@code { "delayMs": 5000 }} (capped at 60s).
     */
    private void mockSlowJob(Job job) {
        long ms = DEFAULT_SLOW_MS;
        try {
            if (job.getPayload() != null && !job.getPayload().isBlank()) {
                JsonNode root = objectMapper.readTree(job.getPayload());
                if (root.has("delayMs") && root.get("delayMs").isNumber()) {
                    ms = Math.min(MAX_SLOW_MS, Math.max(500L, root.get("delayMs").asLong()));
                }
            }
        } catch (Exception e) {
            log.warn("SLOW_JOB invalid payload, using default delay {}ms", DEFAULT_SLOW_MS);
        }
        log.info("[SLOW_JOB] Job {} simulating work for {} ms — watch status RUNNING in UI", job.getId(), ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SLOW_JOB interrupted", e);
        }
        log.info("[SLOW_JOB] Job {} finished simulated work", job.getId());
    }

    private void mockSendEmail(Job job) {
        log.info("[EMAIL] Mock send for job {} — payload: {}", job.getId(), abbreviate(job.getPayload(), 200));
    }

    private void mockGenerateReport(Job job) {
        log.info("[REPORT] Mock generate report for job {} — payload: {}", job.getId(), abbreviate(job.getPayload(), 200));
    }

    private void mockGenericTask(Job job) {
        log.info("[GENERIC] Mock task for job {} type={}", job.getId(), job.getJobType());
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "null";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
