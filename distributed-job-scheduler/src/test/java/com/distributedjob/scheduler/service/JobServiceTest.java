package com.distributedjob.scheduler.service;

import com.distributedjob.scheduler.dto.JobSubmissionRequest;
import com.distributedjob.scheduler.entity.Job;
import com.distributedjob.scheduler.entity.JobPriority;
import com.distributedjob.scheduler.entity.JobStatus;
import com.distributedjob.scheduler.kafka.JobCreatedEvent;
import com.distributedjob.scheduler.repository.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, new ObjectMapper(), eventPublisher);
    }

    @Test
    void submitJobPersistsPendingAndPublishesAfterSave() {
        ObjectNode payload = new ObjectMapper().createObjectNode().put("to", "user@example.com");
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return job;
        });

        var response = jobService.submitJob(new JobSubmissionRequest("EMAIL", payload, JobPriority.HIGH));

        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.priority()).isEqualTo(JobPriority.HIGH);
        ArgumentCaptor<JobCreatedEvent> event = ArgumentCaptor.forClass(JobCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().jobId()).isEqualTo(response.id());
        assertThat(event.getValue().priority()).isEqualTo(JobPriority.HIGH);
    }

    @Test
    void retryFailedJobResetsRetryBudgetAndRequeues() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Job failed = new Job();
        failed.setId(id);
        failed.setJobType("FAILING_JOB");
        failed.setPayload("{}");
        failed.setStatus(JobStatus.FAILED);
        failed.setPriority(JobPriority.LOW);
        failed.setRetryCount(4);
        when(jobRepository.findById(id)).thenReturn(Optional.of(failed));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jobService.retryFailedJob(id);

        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.retryCount()).isZero();
        verify(eventPublisher).publishEvent(any(JobCreatedEvent.class));
    }

    @Test
    void retryRejectsJobsThatAreNotFailed() {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Job running = new Job();
        running.setId(id);
        running.setStatus(JobStatus.RUNNING);
        when(jobRepository.findById(id)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> jobService.retryFailedJob(id))
                .isInstanceOf(ResponseStatusException.class);
        verify(eventPublisher, never()).publishEvent(any());
        verify(jobRepository, never()).save(any());
    }
}
