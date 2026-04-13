package com.distributedjob.scheduler.service;

import com.distributedjob.scheduler.entity.Job;
import com.distributedjob.scheduler.entity.JobPriority;
import com.distributedjob.scheduler.entity.JobStatus;
import com.distributedjob.scheduler.dto.JobCompletionRequest;
import com.distributedjob.scheduler.dto.JobResponse;
import com.distributedjob.scheduler.dto.JobSubmissionRequest;
import com.distributedjob.scheduler.kafka.JobCreatedEvent;
import com.distributedjob.scheduler.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public JobService(
            JobRepository jobRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public JobResponse submitJob(JobSubmissionRequest request) {
        JobPriority priority = request.priority() != null ? request.priority() : JobPriority.LOW;
        Job job = new Job();
        job.setJobType(request.jobType());
        job.setPayload(jsonNodeToString(request.payload()));
        job.setStatus(JobStatus.PENDING);
        job.setPriority(priority);
        job.setRetryCount(0);
        Job saved = jobRepository.save(job);
        log.info(
                "event=job_submitted jobId={} priority={} jobType={} retryCount={}",
                saved.getId(),
                priority,
                saved.getJobType(),
                saved.getRetryCount());
        eventPublisher.publishEvent(new JobCreatedEvent(saved.getId(), saved.getJobType(), saved.getPriority()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id) {
        return jobRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getAllJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse retryFailedJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getStatus() != JobStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FAILED jobs can be retried");
        }
        job.setStatus(JobStatus.PENDING);
        job.setRetryCount(job.getRetryCount() + 1);
        Job saved = jobRepository.save(job);
        log.info(
                "event=job_retry_requested jobId={} priority={} retryCount={}",
                saved.getId(),
                saved.getPriority(),
                saved.getRetryCount());
        eventPublisher.publishEvent(new JobCreatedEvent(saved.getId(), saved.getJobType(), saved.getPriority()));
        return toResponse(saved);
    }

    /**
     * Atomically claims the oldest pending job for execution by a worker (HIGH before LOW, then FIFO).
     */
    @Transactional
    public JobResponse claimNextPendingJob() {
        List<Job> batch = jobRepository.findPendingForClaim(
                JobStatus.PENDING, JobPriority.HIGH, PageRequest.of(0, 1));
        if (batch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending jobs");
        }
        Job job = batch.get(0);
        job.setStatus(JobStatus.RUNNING);
        Job saved = jobRepository.save(job);
        log.info(
                "event=job_claimed_via_rest_worker jobId={} priority={} jobType={}",
                saved.getId(),
                saved.getPriority(),
                saved.getJobType());
        return toResponse(saved);
    }

    /**
     * Records the outcome of a job after a worker finishes processing.
     */
    @Transactional
    public JobResponse completeJob(UUID id, JobCompletionRequest request) {
        JobStatus outcome = request.status();
        if (outcome != JobStatus.SUCCESS && outcome != JobStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completion status must be SUCCESS or FAILED");
        }
        Job job = jobRepository.findByIdAndStatus(id, JobStatus.RUNNING)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Running job not found for id: " + id));
        job.setStatus(outcome);
        Job saved = jobRepository.save(job);
        log.info(
                "event=job_completed_via_rest_worker jobId={} priority={} status={}",
                saved.getId(),
                saved.getPriority(),
                saved.getStatus());
        return toResponse(saved);
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getJobType(),
                stringToJsonNode(job.getPayload()),
                job.getStatus(),
                job.getPriority(),
                job.getRetryCount(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private String jsonNodeToString(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", e);
        }
    }

    private JsonNode stringToJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored job payload is not valid JSON", e);
        }
    }
}
