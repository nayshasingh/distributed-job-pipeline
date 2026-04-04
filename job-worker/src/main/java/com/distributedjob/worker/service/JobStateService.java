package com.distributedjob.worker.service;

import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobStatus;
import com.distributedjob.worker.kafka.JobQueueMessage;
import com.distributedjob.worker.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class JobStateService {

    private static final Logger log = LoggerFactory.getLogger(JobStateService.class);

    private final JobRepository jobRepository;

    public JobStateService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Moves a job from PENDING to RUNNING if it is still pending. Returns empty if the job
     * was already taken, completed, or does not exist.
     */
    @Transactional
    public Optional<Job> tryClaimPending(UUID jobId) {
        int updated = jobRepository.claimIfStatus(jobId, JobStatus.PENDING, JobStatus.RUNNING);
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
            log.info("{} priority job {} Kafka retry scheduled (retryCount={}, maxAttempts={})",
                    job.getPriority(), job.getId(), job.getRetryCount(), limit);
            return ExecutionFailureResult.requeue(
                    new JobQueueMessage(job.getId(), job.getJobType(), job.getPriority()));
        }
        job.setStatus(JobStatus.FAILED);
        jobRepository.save(job);
        log.error("{} priority job {} terminal FAILED after {} execution attempts (retryCount={})",
                job.getPriority(), job.getId(), limit, job.getRetryCount());
        return ExecutionFailureResult.terminalFailed();
    }
}
