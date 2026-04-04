package com.distributedjob.scheduler.dto;

import com.distributedjob.scheduler.entity.JobPriority;
import com.distributedjob.scheduler.entity.JobStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String jobType,
        JsonNode payload,
        JobStatus status,
        JobPriority priority,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {
}
