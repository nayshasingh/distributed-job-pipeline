package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobStatus;
import com.distributedjob.worker.kafka.JobQueueMessage;
import com.distributedjob.worker.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobStateService {

    private static final Logger log = LoggerFactory.getLogger(JobStateService.class);

    private final JobRepository jobRepository;
    private final WorkerProperties workerProperties;

    public JobStateService(JobRepository jobRepository, WorkerProperties workerProperties) {
        this.jobRepository = jobRepository;
        this.workerProperties = workerProperties;
    }

    /**
     * Moves a job from PENDING to RUNNING if it is still pending. Returns empty if the job
     * was already taken, completed, or does not exist.
     */
    @Transactional
    public Optional<Job> tryClaimPending(UUID jobId) {
        int updated = jobRepository.claimIfStatus(jobId, JobStatus.PENDING, JobStatus.RUNNING);
        if (updated == 0) {
            Instant staleBefore = Instant.now().minusSeconds(Math.max(1, workerProperties.staleRunningTimeoutSeconds()));
            int reclaimed = jobRepository.reclaimStaleRunning(
                    jobId,
                    JobStatus.RUNNING,
                    JobStatus.PENDING,
                    staleBefore);
            if (reclaimed > 0) {
                log.warn("event=job_stale_running_reclaimed jobId={} staleBefore={}", jobId, staleBefore);
                updated = jobRepository.claimIfStatus(jobId, JobStatus.PENDING, JobStatus.RUNNING);
            }
        }
        if (updated == 0) {
            return Optional.empty();
        }
        return jobRepository.findById(jobId);
    }

    @Transactional
    public void markSuccess(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) {
            return;
        }
        job.setStatus(JobStatus.SUCCESS);
        jobRepository.save(job);
    }

    /**
     * Increments {@code retryCount} after a failed execution. If under the attempt limit, sets PENDING for a
     * Kafka-driven retry; otherwise marks FAILED.
     */
    @Transactional
    public ExecutionFailureResult recordExecutionFailure(UUID jobId, int maxExecutionAttempts) {
        int limit = Math.max(1, maxExecutionAttempts);
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING) {
            return ExecutionFailureResult.ignored();
        }
        job.setRetryCount(job.getRetryCount() + 1);
        if (job.getRetryCount() < limit) {
            job.setStatus(JobStatus.PENDING);
            jobRepository.save(job);
            log.info(
                    "event=job_retry_scheduled jobId={} priority={} retryCount={} maxAttempts={}",
                    job.getId(),
                    job.getPriority(),
                    job.getRetryCount(),
                    limit);
            return ExecutionFailureResult.requeue(
                    new JobQueueMessage(job.getId(), job.getJobType(), job.getPriority()));
        }
        job.setStatus(JobStatus.FAILED);
        jobRepository.save(job);
        log.error(
                "event=job_terminal_failed jobId={} priority={} maxAttempts={} retryCount={}",
                job.getId(),
                job.getPriority(),
                limit,
                job.getRetryCount());
        return ExecutionFailureResult.terminalFailed();
    }
}
