package com.distributedjob.worker.service;

import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobPriority;
import com.distributedjob.worker.entity.JobStatus;
import com.distributedjob.worker.kafka.JobQueueKafkaProducer;
import com.distributedjob.worker.kafka.JobQueueMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleRunningJobRecoveryTest {

    @Mock
    private JobStateService jobStateService;
    @Mock
    private JobQueueKafkaProducer jobQueueKafkaProducer;

    @InjectMocks
    private StaleRunningJobRecovery recovery;

    @Test
    void republishesEachReclaimedJobOnItsPriorityLane() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setJobType("SLOW_JOB");
        job.setStatus(JobStatus.PENDING);
        job.setPriority(JobPriority.HIGH);
        when(jobStateService.reclaimStaleRunningJobs()).thenReturn(List.of(job));

        recovery.reclaimAndRepublish();

        ArgumentCaptor<JobQueueMessage> message = ArgumentCaptor.forClass(JobQueueMessage.class);
        verify(jobQueueKafkaProducer).sendJobQueued(message.capture());
        assertThat(message.getValue().jobId()).isEqualTo(job.getId());
        assertThat(message.getValue().priority()).isEqualTo(JobPriority.HIGH);
    }

    @Test
    void doesNothingWhenNoRowsAreStale() {
        when(jobStateService.reclaimStaleRunningJobs()).thenReturn(List.of());

        recovery.reclaimAndRepublish();

        verifyNoInteractions(jobQueueKafkaProducer);
    }
}
