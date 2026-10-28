package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobPriority;
import com.distributedjob.worker.entity.JobStatus;
import com.distributedjob.worker.kafka.JobQueueKafkaProducer;
import com.distributedjob.worker.kafka.JobQueueMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProcessingServiceTest {

    @Mock
    private JobStateService jobStateService;
    @Mock
    private MockJobTaskExecutor mockJobTaskExecutor;
    @Mock
    private JobQueueKafkaProducer jobQueueKafkaProducer;
    @Mock
    private JobDistributedLockService jobDistributedLockService;

    private JobProcessingService jobProcessingService;

    @BeforeEach
    void setUp() {
        jobProcessingService = new JobProcessingService(
                jobStateService,
                mockJobTaskExecutor,
                new WorkerProperties(4, 900, "job-worker:exec:", 600),
                jobQueueKafkaProducer,
                jobDistributedLockService);
    }

    @Test
    void skipsWhenRedisLockIsHeld() {
        JobQueueMessage message = message();
        when(jobDistributedLockService.tryLock(message.jobId())).thenReturn(Optional.empty());

        jobProcessingService.processQueuedJob(message);

        verify(jobStateService, never()).tryClaimPending(any());
        verify(mockJobTaskExecutor, never()).execute(any());
    }

    @Test
    void skipsWhenJobIsNotClaimable() {
        JobQueueMessage message = message();
        JobDistributedLockService.LockHandle lock = new JobDistributedLockService.LockHandle("k", "t");
        when(jobDistributedLockService.tryLock(message.jobId())).thenReturn(Optional.of(lock));
        when(jobStateService.tryClaimPending(message.jobId())).thenReturn(Optional.empty());

        jobProcessingService.processQueuedJob(message);

        verify(mockJobTaskExecutor, never()).execute(any());
        verify(jobDistributedLockService).unlock(lock);
    }

    @Test
    void marksSuccessAndReleasesLock() {
        JobQueueMessage message = message();
        JobDistributedLockService.LockHandle lock = new JobDistributedLockService.LockHandle("k", "t");
        Job job = job(message.jobId());
        when(jobDistributedLockService.tryLock(message.jobId())).thenReturn(Optional.of(lock));
        when(jobStateService.tryClaimPending(message.jobId())).thenReturn(Optional.of(job));

        jobProcessingService.processQueuedJob(message);

        verify(mockJobTaskExecutor).execute(job);
        verify(jobStateService).markSuccess(job.getId());
        verify(jobDistributedLockService).unlock(lock);
    }

    @Test
    void republishesOnRetryableFailure() {
        JobQueueMessage message = message();
        JobDistributedLockService.LockHandle lock = new JobDistributedLockService.LockHandle("k", "t");
        Job job = job(message.jobId());
        when(jobDistributedLockService.tryLock(message.jobId())).thenReturn(Optional.of(lock));
        when(jobStateService.tryClaimPending(message.jobId())).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("boom")).when(mockJobTaskExecutor).execute(job);
        when(jobStateService.recordExecutionFailure(job.getId(), 4))
                .thenReturn(ExecutionFailureResult.requeue(message));

        jobProcessingService.processQueuedJob(message);

        verify(jobQueueKafkaProducer).sendJobQueued(message);
        verify(jobDistributedLockService).unlock(lock);
    }

    @Test
    void doesNotRepublishWhenAttemptsAreExhausted() {
        JobQueueMessage message = message();
        JobDistributedLockService.LockHandle lock = new JobDistributedLockService.LockHandle("k", "t");
        Job job = job(message.jobId());
        when(jobDistributedLockService.tryLock(message.jobId())).thenReturn(Optional.of(lock));
        when(jobStateService.tryClaimPending(message.jobId())).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("boom")).when(mockJobTaskExecutor).execute(job);
        when(jobStateService.recordExecutionFailure(job.getId(), 4))
                .thenReturn(ExecutionFailureResult.terminalFailed());

        jobProcessingService.processQueuedJob(message);

        verify(jobQueueKafkaProducer, never()).sendJobQueued(any());
        verify(jobDistributedLockService).unlock(lock);
    }

    private static JobQueueMessage message() {
        return new JobQueueMessage(UUID.randomUUID(), "EMAIL", JobPriority.HIGH);
    }

    private static Job job(UUID id) {
        Job job = new Job();
        job.setId(id);
        job.setJobType("EMAIL");
        job.setStatus(JobStatus.RUNNING);
        job.setPriority(JobPriority.HIGH);
        return job;
    }
}
