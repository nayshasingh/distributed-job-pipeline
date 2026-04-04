package com.distributedjob.scheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic jobQueueHighTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.jobQueueHighTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobQueueLowTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.jobQueueLowTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
