package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.kafka.JobQueueKafkaProducer;
import com.distributedjob.worker.kafka.JobQueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class JobProcessingService {

    private static final Logger log = LoggerFactory.getLogger(JobProcessingService.class);

    private final JobStateService jobStateService;
    private final MockJobTaskExecutor mockJobTaskExecutor;
    private final WorkerProperties workerProperties;
    private final JobQueueKafkaProducer jobQueueKafkaProducer;
    private final JobDistributedLockService jobDistributedLockService;

    public JobProcessingService(
            JobStateService jobStateService,
            MockJobTaskExecutor mockJobTaskExecutor,
            WorkerProperties workerProperties,
            JobQueueKafkaProducer jobQueueKafkaProducer,
            JobDistributedLockService jobDistributedLockService) {
        this.jobStateService = jobStateService;
        this.mockJobTaskExecutor = mockJobTaskExecutor;
        this.workerProperties = workerProperties;
        this.jobQueueKafkaProducer = jobQueueKafkaProducer;
        this.jobDistributedLockService = jobDistributedLockService;
    }

    public void processQueuedJob(JobQueueMessage message) {
        Optional<JobDistributedLockService.LockHandle> lock =
                jobDistributedLockService.tryLock(message.jobId());
        if (lock.isEmpty()) {
            log.info("Skipped {} priority job {} — Redis execution lock held by another worker",
                    message.priority(), message.jobId());
            return;
        }
        try {
            Optional<Job> claimed = jobStateService.tryClaimPending(message.jobId());
            if (claimed.isEmpty()) {
                log.debug("Skipped {} priority job {} — not in PENDING state or missing",
                        message.priority(), message.jobId());
                return;
            }

            Job job = claimed.get();
            try {
                log.info("Processing {} priority job {} of type {}", job.getPriority(), job.getId(), job.getJobType());
                mockJobTaskExecutor.execute(job);
                jobStateService.markSuccess(job.getId());
                log.info("{} priority job {} completed successfully", job.getPriority(), job.getId());
            } catch (Exception e) {
                log.warn("{} priority job {} execution failed: {}", job.getPriority(), job.getId(), e.toString());
                ExecutionFailureResult outcome = jobStateService.recordExecutionFailure(
                        job.getId(),
                        workerProperties.maxExecutionAttempts());
                switch (outcome.type()) {
                    case REQUEUE -> jobQueueKafkaProducer.sendJobQueued(outcome.republishMessage());
                    case TERMINAL_FAILED -> log.error(
                            "{} priority job {} marked FAILED after {} failed execution attempt(s)",
                            job.getPriority(),
                            job.getId(),
                            workerProperties.maxExecutionAttempts());
                    case IGNORED -> log.debug("Job {} failure not applied (state changed or missing)", job.getId());
                }
            }
        } finally {
            jobDistributedLockService.unlock(lock.get());
        }
    }
}
