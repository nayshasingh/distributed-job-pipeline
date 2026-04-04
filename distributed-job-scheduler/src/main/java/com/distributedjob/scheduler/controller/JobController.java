package com.distributedjob.scheduler.controller;

import com.distributedjob.scheduler.dto.JobResponse;
import com.distributedjob.scheduler.dto.JobSubmissionRequest;
import com.distributedjob.scheduler.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody JobSubmissionRequest request) {
        return jobService.submitJob(request);
    }

    @GetMapping
    public List<JobResponse> listJobs() {
        return jobService.getAllJobs();
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @PostMapping("/{id}/retry")
    public JobResponse retryJob(@PathVariable UUID id) {
        return jobService.retryFailedJob(id);
    }
}
