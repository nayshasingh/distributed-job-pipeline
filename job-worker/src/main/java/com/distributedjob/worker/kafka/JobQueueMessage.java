package com.distributedjob.worker.kafka;

import com.distributedjob.worker.entity.JobPriority;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

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
