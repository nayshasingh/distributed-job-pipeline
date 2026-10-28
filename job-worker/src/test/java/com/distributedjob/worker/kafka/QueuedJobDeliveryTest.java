package com.distributedjob.worker.kafka;

import com.distributedjob.worker.entity.JobPriority;
import com.distributedjob.worker.service.JobProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueuedJobDeliveryTest {

    @Mock
    private JobProcessingService jobProcessingService;
    @Mock
    private Acknowledgment acknowledgment;

    private QueuedJobDelivery queuedJobDelivery;

    @BeforeEach
    void setUp() {
        queuedJobDelivery = new QueuedJobDelivery(new ObjectMapper(), jobProcessingService);
    }

    @Test
    void acknowledgesAfterSuccessfulProcessing() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(
                new JobQueueMessage(UUID.randomUUID(), "EMAIL", JobPriority.LOW));

        queuedJobDelivery.deliver(payload, acknowledgment, JobPriority.LOW);

        verify(jobProcessingService).processQueuedJob(any(JobQueueMessage.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void acknowledgesPoisonJsonSoThePartitionIsNotBlocked() {
        queuedJobDelivery.deliver("not-json", acknowledgment, JobPriority.HIGH);

        verify(jobProcessingService, never()).processQueuedJob(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void leavesTransientFailuresUnacked() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(
                new JobQueueMessage(UUID.randomUUID(), "EMAIL", JobPriority.HIGH));
        doThrow(new IllegalStateException("redis down"))
                .when(jobProcessingService).processQueuedJob(any());

        queuedJobDelivery.deliver(payload, acknowledgment, JobPriority.HIGH);

        verify(acknowledgment, never()).acknowledge();
    }
}
