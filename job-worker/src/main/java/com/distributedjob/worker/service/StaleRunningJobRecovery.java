package com.distributedjob.worker.service;

import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.kafka.JobQueueKafkaProducer;
import com.distributedjob.worker.kafka.JobQueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic sweeper for jobs left RUNNING after a worker crash.
 *
 * <p>Kafka redelivery alone is not enough: a later delivery can be skipped and acknowledged while
 * the dead worker's Redis lock is still held. This job scans Postgres and re-publishes reclaimed
 * rows so execution can resume without waiting for another message.
 */
@Component
public class StaleRunningJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleRunningJobRecovery.class);

    private final JobStateService jobStateService;
    private final JobQueueKafkaProducer jobQueueKafkaProducer;

    public StaleRunningJobRecovery(
            JobStateService jobStateService,
            JobQueueKafkaProducer jobQueueKafkaProducer) {
        this.jobStateService = jobStateService;
        this.jobQueueKafkaProducer = jobQueueKafkaProducer;
    }

    @Scheduled(fixedDelayString = "${app.worker.stale-reclaim-interval-ms:30000}")
    public void reclaimAndRepublish() {
        List<Job> reclaimed = jobStateService.reclaimStaleRunningJobs();
        for (Job job : reclaimed) {
            log.info(
                    "event=job_stale_requeued jobId={} priority={} jobType={}",
                    job.getId(),
                    job.getPriority(),
                    job.getJobType());
            jobQueueKafkaProducer.sendJobQueued(
                    new JobQueueMessage(job.getId(), job.getJobType(), job.getPriority()));
        }
    }
}
