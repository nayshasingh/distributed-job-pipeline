package com.distributedjob.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicsProperties(
        String jobQueueHighTopic,
        String jobQueueLowTopic
) {
}
