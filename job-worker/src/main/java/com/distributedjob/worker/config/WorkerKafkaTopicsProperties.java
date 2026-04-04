package com.distributedjob.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record WorkerKafkaTopicsProperties(
        String jobQueueHighTopic,
        String jobQueueLowTopic,
        ConsumerGroups consumerGroups
) {
    public record ConsumerGroups(String high, String low) {
    }
}
