package com.distributedjob.scheduler.kafka;

import com.distributedjob.scheduler.entity.JobPriority;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Payload published to priority-specific Kafka topics after a job is persisted or re-queued.
 * Legacy messages without {@code priority} deserialize as {@link JobPriority#LOW}.
 */
public record JobQueueMessage(
        @JsonProperty("jobId") UUID jobId,
        @JsonProperty("jobType") String jobType,
        @JsonProperty("priority") JobPriority priority
) {
    public JobQueueMessage {
        if (priority == null) {
            priority = JobPriority.LOW;
        }
    }
}
