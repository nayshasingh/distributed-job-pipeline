package com.distributedjob.scheduler.dto;

import com.distributedjob.scheduler.entity.JobPriority;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobSubmissionRequest(
        @NotBlank
        @Size(max = 255)
        String jobType,
        @NotNull JsonNode payload,
        @Schema(description = "Optional. Defaults to LOW when omitted.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        JobPriority priority
) {
}
