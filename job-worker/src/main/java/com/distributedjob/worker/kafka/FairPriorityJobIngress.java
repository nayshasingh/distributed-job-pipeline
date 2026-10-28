package com.distributedjob.worker.kafka;

import com.distributedjob.worker.entity.JobPriority;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h2>Fair priority ingress (3 HIGH : 1 LOW)</h2>
 *
 * <p>We use <b>two Kafka topics</b> ({@code job-queue-high}, {@code job-queue-low}) so the broker keeps
 * lanes separate. Each topic is consumed by its <b>own consumer group</b> ({@code job-worker-high},
 * {@code job-worker-low}) because a single Kafka consumer group must share the same subscription;
 * splitting groups per lane is the standard way to subscribe to different topics in parallel.
 *
 * <p>Messages are acknowledged by {@link QueuedJobDelivery} after processing succeeds (or after a
 * poison payload). Unexpected errors are left unacked. See {@code syncCommits} on the listener container.
 *
 * <p><b>Fairness:</b> Each cycle we drain up to {@link #HIGH_BATCH_SIZE} messages from the HIGH lane
 * (waiting briefly per slot). Then we take at most one message from the LOW lane. If the HIGH lane is
 * empty before any HIGH was served, we do not spin: we go straight to LOW, and if both are empty we
 * block briefly on either queue. That prevents LOW starvation when no HIGH traffic exists, while under
 * sustained HIGH load the ratio tends to {@code HIGH_BATCH_SIZE} : 1.
 */
@Component
public class FairPriorityJobIngress {

    private static final Logger log = LoggerFactory.getLogger(FairPriorityJobIngress.class);

    private static final int HIGH_BATCH_SIZE = 3;

    private final LinkedBlockingQueue<PendingKafkaWork> highLane = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<PendingKafkaWork> lowLane = new LinkedBlockingQueue<>();
    private final QueuedJobDelivery queuedJobDelivery;

    private volatile boolean running = true;
    private Thread dispatcherThread;

    public FairPriorityJobIngress(QueuedJobDelivery queuedJobDelivery) {
        this.queuedJobDelivery = queuedJobDelivery;
    }

    private record PendingKafkaWork(String payload, Acknowledgment ack, JobPriority lane) {
    }

    @PostConstruct
    void startDispatcher() {
        this.dispatcherThread = new Thread(this::dispatchLoop, "fair-priority-job-dispatch");
        this.dispatcherThread.setDaemon(true);
        this.dispatcherThread.start();
    }

    @PreDestroy
    void stopDispatcher() {
        running = false;
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
    }

    @KafkaListener(
            topics = "${app.kafka.job-queue-high-topic}",
            groupId = "${app.kafka.consumer-groups.high}",
            containerFactory = "priorityFairKafkaListenerContainerFactory")
    public void onHighLane(String payload, Acknowledgment acknowledgment) {
        enqueue(highLane, payload, acknowledgment, JobPriority.HIGH);
    }

    @KafkaListener(
            topics = "${app.kafka.job-queue-low-topic}",
            groupId = "${app.kafka.consumer-groups.low}",
            containerFactory = "priorityFairKafkaListenerContainerFactory")
    public void onLowLane(String payload, Acknowledgment acknowledgment) {
        enqueue(lowLane, payload, acknowledgment, JobPriority.LOW);
    }

    private void enqueue(
            LinkedBlockingQueue<PendingKafkaWork> queue,
            String payload,
            Acknowledgment acknowledgment,
            JobPriority lane) {
        try {
            queue.put(new PendingKafkaWork(payload, acknowledgment, lane));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acknowledgment.acknowledge();
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                runOneFairCycle();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("Fair dispatch loop error", e);
            }
        }
    }

    private void runOneFairCycle() throws InterruptedException {
        int highsServed = 0;
        while (highsServed < HIGH_BATCH_SIZE) {
            PendingKafkaWork w = highLane.poll(250, TimeUnit.MILLISECONDS);
            if (w == null) {
                break;
            }
            deliver(w);
            highsServed++;
        }

        PendingKafkaWork low = lowLane.poll(250, TimeUnit.MILLISECONDS);
        if (low != null) {
            deliver(low);
            return;
        }

        if (highsServed == 0) {
            PendingKafkaWork any = pollEitherBlocking();
            if (any != null) {
                deliver(any);
            }
        }
    }

    private PendingKafkaWork pollEitherBlocking() throws InterruptedException {
        PendingKafkaWork h = highLane.poll(500, TimeUnit.MILLISECONDS);
        if (h != null) {
            return h;
        }
        return lowLane.poll(500, TimeUnit.MILLISECONDS);
    }

    private void deliver(PendingKafkaWork w) {
        queuedJobDelivery.deliver(w.payload(), w.ack(), w.lane());
    }
}
