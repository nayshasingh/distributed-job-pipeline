package com.distributedjob.scheduler.metrics;

import com.distributedjob.scheduler.entity.JobPriority;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {

    private final MeterRegistry meterRegistry;

    public JobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void submitted(JobPriority priority) {
        meterRegistry.counter("jobs.submitted", "priority", priority.name()).increment();
    }

    public void retried(JobPriority priority) {
        meterRegistry.counter("jobs.retried", "priority", priority.name()).increment();
    }
}
