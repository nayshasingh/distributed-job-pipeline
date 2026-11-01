package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.kafka.JobQueueKafkaProducer;
import com.distributedjob.worker.kafka.JobQueueMessage;
import com.distributedjob.worker.metrics.JobMetrics;
import io.micrometer.core.instrument.Timer;
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
    private final JobMetrics jobMetrics;

    public JobProcessingService(
            JobStateService jobStateService,
            MockJobTaskExecutor mockJobTaskExecutor,
            WorkerProperties workerProperties,
            JobQueueKafkaProducer jobQueueKafkaProducer,
            JobDistributedLockService jobDistributedLockService,
            JobMetrics jobMetrics) {
        this.jobStateService = jobStateService;
        this.mockJobTaskExecutor = mockJobTaskExecutor;
        this.workerProperties = workerProperties;
        this.jobQueueKafkaProducer = jobQueueKafkaProducer;
        this.jobDistributedLockService = jobDistributedLockService;
        this.jobMetrics = jobMetrics;
    }

    public void processQueuedJob(JobQueueMessage message) {
        Optional<JobDistributedLockService.LockHandle> lock =
                jobDistributedLockService.tryLock(message.jobId());
        if (lock.isEmpty()) {
            log.info(
                    "event=job_skipped_lock_held jobId={} priority={}",
                    message.jobId(),
                    message.priority());
            jobMetrics.lockSkipped(message.priority());
            return;
        }
        try {
            Optional<Job> claimed = jobStateService.tryClaimPending(message.jobId());
            if (claimed.isEmpty()) {
                log.debug(
                        "event=job_skipped_not_claimable jobId={} priority={}",
                        message.jobId(),
                        message.priority());
                jobMetrics.notClaimable(message.priority());
                return;
            }

            Job job = claimed.get();
            Timer.Sample sample = jobMetrics.startExecution();
            try {
                log.info(
                        "event=job_processing_started jobId={} priority={} jobType={}",
                        job.getId(),
                        job.getPriority(),
                        job.getJobType());
                mockJobTaskExecutor.execute(job);
                jobStateService.markSuccess(job.getId());
                jobMetrics.succeeded(job.getPriority(), sample);
                log.info(
                        "event=job_processing_succeeded jobId={} priority={} jobType={}",
                        job.getId(),
                        job.getPriority(),
                        job.getJobType());
            } catch (Exception e) {
                log.warn(
                        "event=job_processing_failed jobId={} priority={} jobType={} error={}",
                        job.getId(),
                        job.getPriority(),
                        job.getJobType(),
                        e.toString());
                ExecutionFailureResult outcome = jobStateService.recordExecutionFailure(
                        job.getId(),
                        workerProperties.maxExecutionAttempts());
                boolean requeued = outcome.type() == ExecutionFailureResult.Type.REQUEUE;
                jobMetrics.failed(job.getPriority(), sample, requeued);
                switch (outcome.type()) {
                    case REQUEUE -> jobQueueKafkaProducer.sendJobQueued(outcome.republishMessage());
                    case TERMINAL_FAILED -> log.error(
                            "event=job_processing_exhausted jobId={} priority={} maxAttempts={}",
                            job.getId(),
                            job.getPriority(),
                            workerProperties.maxExecutionAttempts());
                    case IGNORED -> log.debug(
                            "event=job_failure_ignored jobId={} reason=state_changed_or_missing",
                            job.getId());
                }
            }
        } finally {
            jobDistributedLockService.unlock(lock.get());
        }
    }
}
