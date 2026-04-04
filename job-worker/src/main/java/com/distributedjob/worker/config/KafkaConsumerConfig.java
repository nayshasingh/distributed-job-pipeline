package com.distributedjob.worker.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, String> kafkaStringConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Single-threaded consumers per listener + async ack from the fair-dispatch thread requires
     * {@code syncCommits=false} so commits are not tied to the poll thread only.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> priorityFairKafkaListenerContainerFactory(
            ConsumerFactory<String, String> kafkaStringConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaStringConsumerFactory);
        factory.setConcurrency(1);
        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setSyncCommits(false);
        return factory;
    }
}
