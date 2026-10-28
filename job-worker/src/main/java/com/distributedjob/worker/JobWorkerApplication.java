package com.distributedjob.worker;

import com.distributedjob.worker.config.WorkerKafkaTopicsProperties;
import com.distributedjob.worker.config.WorkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
@EnableConfigurationProperties({WorkerProperties.class, WorkerKafkaTopicsProperties.class})
public class JobWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobWorkerApplication.class, args);
    }
}
