package com.distributedjob.worker.metrics;

import com.distributedjob.worker.entity.JobPriority;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {

    private final MeterRegistry meterRegistry;

    public JobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void lockSkipped(JobPriority priority) {
        meterRegistry.counter("jobs.lock.skipped", "priority", String.valueOf(priority)).increment();
    }

    public void notClaimable(JobPriority priority) {
        meterRegistry.counter("jobs.not_claimable", "priority", String.valueOf(priority)).increment();
    }

    public Timer.Sample startExecution() {
        return Timer.start(meterRegistry);
    }

    public void succeeded(JobPriority priority, Timer.Sample sample) {
        sample.stop(Timer.builder("jobs.execution")
                .tag("priority", priority.name())
                .tag("outcome", "SUCCESS")
                .register(meterRegistry));
        meterRegistry.counter("jobs.completed", "priority", priority.name(), "outcome", "SUCCESS").increment();
    }

    public void failed(JobPriority priority, Timer.Sample sample, boolean requeued) {
        sample.stop(Timer.builder("jobs.execution")
                .tag("priority", priority.name())
                .tag("outcome", requeued ? "REQUEUE" : "FAILED")
                .register(meterRegistry));
        meterRegistry.counter(
                "jobs.completed",
                "priority",
                priority.name(),
                "outcome",
                requeued ? "REQUEUE" : "FAILED").increment();
    }
}
