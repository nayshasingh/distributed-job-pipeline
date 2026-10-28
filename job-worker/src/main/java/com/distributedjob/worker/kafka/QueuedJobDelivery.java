package com.distributedjob.worker.kafka;

import com.distributedjob.worker.entity.JobPriority;
import com.distributedjob.worker.service.JobProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Parses a Kafka payload and decides whether the offset can be committed.
 *
 * <p>Malformed JSON is acknowledged so a poison message cannot block the partition.
 * Unexpected processing errors are left unacked so Kafka can redeliver after a rebalance
 * or restart. Expected skips (lock held, already claimed) still ack because another worker
 * owns the job or the DB already moved on.
 */
@Component
public class QueuedJobDelivery {

    private static final Logger log = LoggerFactory.getLogger(QueuedJobDelivery.class);

    private final ObjectMapper objectMapper;
    private final JobProcessingService jobProcessingService;

    public QueuedJobDelivery(ObjectMapper objectMapper, JobProcessingService jobProcessingService) {
        this.objectMapper = objectMapper;
        this.jobProcessingService = jobProcessingService;
    }

    public void deliver(String payload, Acknowledgment acknowledgment, JobPriority lane) {
        try {
            JobQueueMessage message = objectMapper.readValue(payload, JobQueueMessage.class);
            log.info("Dequeuing {} priority job {} (type={}) from Kafka {} lane for execution",
                    message.priority(), message.jobId(), message.jobType(), lane);
            jobProcessingService.processQueuedJob(message);
            acknowledgment.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Poison Kafka payload on {} lane; acknowledging to avoid a retry loop", lane, e);
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            log.error("Transient failure processing Kafka payload from {} lane; leaving unacked", lane, e);
        }
    }
}
