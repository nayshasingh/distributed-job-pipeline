package com.distributedjob.scheduler.kafka;

import com.distributedjob.scheduler.entity.JobPriority;

import java.util.UUID;

public record JobCreatedEvent(UUID jobId, String jobType, JobPriority priority) {
}
