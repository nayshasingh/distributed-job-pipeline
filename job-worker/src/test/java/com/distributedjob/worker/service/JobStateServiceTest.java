package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobPriority;
import com.distributedjob.worker.entity.JobStatus;
import com.distributedjob.worker.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobStateServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobStateService jobStateService;

    @BeforeEach
    void setUp() {
        jobStateService = new JobStateService(
                jobRepository,
                new WorkerProperties(4, 900, "job-worker:exec:", 600));
    }

    @Test
    void tryClaimPendingSucceedsWhenRowIsStillPending() {
        UUID id = UUID.randomUUID();
        Job running = job(id, JobStatus.RUNNING, 0);
        when(jobRepository.claimIfStatus(id, JobStatus.PENDING, JobStatus.RUNNING)).thenReturn(1);
        when(jobRepository.findById(id)).thenReturn(Optional.of(running));

        assertThat(jobStateService.tryClaimPending(id)).contains(running);
    }

    @Test
    void tryClaimPendingReclaimsStaleRunningThenClaims() {
        UUID id = UUID.randomUUID();
        Job running = job(id, JobStatus.RUNNING, 1);
        when(jobRepository.claimIfStatus(id, JobStatus.PENDING, JobStatus.RUNNING)).thenReturn(0, 1);
        when(jobRepository.reclaimStaleRunning(eq(id), eq(JobStatus.RUNNING), eq(JobStatus.PENDING), any()))
                .thenReturn(1);
        when(jobRepository.findById(id)).thenReturn(Optional.of(running));

        assertThat(jobStateService.tryClaimPending(id)).isPresent();
        verify(jobRepository).reclaimStaleRunning(eq(id), eq(JobStatus.RUNNING), eq(JobStatus.PENDING), any());
    }

    @Test
    void recordExecutionFailureRequeuesUntilAttemptCap() {
        UUID id = UUID.randomUUID();
        Job running = job(id, JobStatus.RUNNING, 2);
        when(jobRepository.findById(id)).thenReturn(Optional.of(running));
        when(jobRepository.save(running)).thenReturn(running);

        ExecutionFailureResult result = jobStateService.recordExecutionFailure(id, 4);

        assertThat(result.type()).isEqualTo(ExecutionFailureResult.Type.REQUEUE);
        assertThat(running.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(running.getRetryCount()).isEqualTo(3);
    }

    @Test
    void recordExecutionFailureMarksFailedAtCap() {
        UUID id = UUID.randomUUID();
        Job running = job(id, JobStatus.RUNNING, 3);
        when(jobRepository.findById(id)).thenReturn(Optional.of(running));
        when(jobRepository.save(running)).thenReturn(running);

        ExecutionFailureResult result = jobStateService.recordExecutionFailure(id, 4);

        assertThat(result.type()).isEqualTo(ExecutionFailureResult.Type.TERMINAL_FAILED);
        assertThat(running.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(running.getRetryCount()).isEqualTo(4);
    }

    @Test
    void reclaimStaleRunningJobsOnlyReturnsRowsThisWorkerUpdated() {
        UUID claimed = UUID.randomUUID();
        UUID raced = UUID.randomUUID();
        Job staleClaimed = job(claimed, JobStatus.RUNNING, 0);
        staleClaimed.setUpdatedAt(Instant.now().minusSeconds(1_000));
        Job staleRaced = job(raced, JobStatus.RUNNING, 0);
        staleRaced.setUpdatedAt(Instant.now().minusSeconds(1_000));
        when(jobRepository.findStaleRunning(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(staleClaimed, staleRaced));
        when(jobRepository.reclaimStaleRunning(eq(claimed), eq(JobStatus.RUNNING), eq(JobStatus.PENDING), any()))
                .thenReturn(1);
        when(jobRepository.reclaimStaleRunning(eq(raced), eq(JobStatus.RUNNING), eq(JobStatus.PENDING), any()))
                .thenReturn(0);
        Job pending = job(claimed, JobStatus.PENDING, 0);
        when(jobRepository.findById(claimed)).thenReturn(Optional.of(pending));

        assertThat(jobStateService.reclaimStaleRunningJobs()).containsExactly(pending);
    }

    private static Job job(UUID id, JobStatus status, int retryCount) {
        Job job = new Job();
        job.setId(id);
        job.setJobType("EMAIL");
        job.setPayload("{}");
        job.setStatus(status);
        job.setPriority(JobPriority.HIGH);
        job.setRetryCount(retryCount);
        return job;
    }
}
