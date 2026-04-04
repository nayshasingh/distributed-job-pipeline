package com.distributedjob.scheduler.kafka;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JobCreatedKafkaPublisher {

    private final JobQueueKafkaProducer jobQueueKafkaProducer;

    public JobCreatedKafkaPublisher(JobQueueKafkaProducer jobQueueKafkaProducer) {
        this.jobQueueKafkaProducer = jobQueueKafkaProducer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueuedForKafka(JobCreatedEvent event) {
        jobQueueKafkaProducer.sendJobQueued(
                new JobQueueMessage(event.jobId(), event.jobType(), event.priority()));
    }
}
