package com.distributedjob.scheduler.kafka;

import com.distributedjob.scheduler.config.KafkaTopicsProperties;
import com.distributedjob.scheduler.entity.JobPriority;
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
    private final KafkaTopicsProperties kafkaTopicsProperties;

    public JobQueueKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            KafkaTopicsProperties kafkaTopicsProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
    }

    public void sendJobQueued(JobQueueMessage message) {
        String topic = resolveTopic(message.priority());
        String key = message.jobId().toString();
        String value;
        try {
            value = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize job queue message", e);
        }
        log.info("Publishing {} priority job {} (type={}) to topic {}",
                message.priority(), message.jobId(), message.jobType(), topic);
        kafkaTemplate.send(topic, key, value)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish job {} to topic {}", message.jobId(), topic, ex);
                    } else {
                        log.debug("Published job {} to partition {} offset {}",
                                message.jobId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private String resolveTopic(JobPriority priority) {
        return priority == JobPriority.HIGH
                ? kafkaTopicsProperties.jobQueueHighTopic()
                : kafkaTopicsProperties.jobQueueLowTopic();
    }
}
