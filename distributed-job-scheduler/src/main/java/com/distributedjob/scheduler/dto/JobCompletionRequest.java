package com.distributedjob.scheduler.dto;

import com.distributedjob.scheduler.entity.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobCompletionRequest(
        @NotNull JobStatus status
) {
}
