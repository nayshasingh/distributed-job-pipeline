package com.distributedjob.worker.kafka;

import com.distributedjob.worker.config.WorkerKafkaTopicsProperties;
import com.distributedjob.worker.entity.JobPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobQueueKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(JobQueueKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WorkerKafkaTopicsProperties topics;

    public JobQueueKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            WorkerKafkaTopicsProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topics = topics;
    }

    /**
     * Re-publish after failure to the same priority lane (HIGH stays on high topic).
     */
    public void sendJobQueued(JobQueueMessage message) {
        String topic = message.priority() == JobPriority.HIGH
                ? topics.jobQueueHighTopic()
                : topics.jobQueueLowTopic();
        String key = message.jobId().toString();
        String value;
        try {
            value = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize job queue message", e);
        }
        log.info("Re-publishing {} priority job {} (type={}) to topic {} for retry",
                message.priority(), message.jobId(), message.jobType(), topic);
        kafkaTemplate.send(topic, key, value)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to re-publish job {} to topic {}", message.jobId(), topic, ex);
                    } else {
                        log.debug("Re-published job {} to partition {} offset {}",
                                message.jobId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
