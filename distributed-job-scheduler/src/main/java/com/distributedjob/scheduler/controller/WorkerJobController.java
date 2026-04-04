package com.distributedjob.scheduler.controller;

import com.distributedjob.scheduler.dto.JobCompletionRequest;
import com.distributedjob.scheduler.dto.JobResponse;
import com.distributedjob.scheduler.service.JobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workers/jobs")
public class WorkerJobController {

    private final JobService jobService;

    public WorkerJobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/claim-next")
    public JobResponse claimNext() {
        return jobService.claimNextPendingJob();
    }

    @PostMapping("/{id}/complete")
    public JobResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody JobCompletionRequest request) {
        return jobService.completeJob(id, request);
    }
}
