package com.distributedjob.worker.service;

import com.distributedjob.worker.kafka.JobQueueMessage;

public record ExecutionFailureResult(Type type, JobQueueMessage republishMessage) {

    public enum Type {
        /** Job returned to PENDING and should be sent to Kafka again. */
        REQUEUE,
        /** retryCount reached max execution attempts; status is FAILED. */
        TERMINAL_FAILED,
        /** Job row missing or not RUNNING; no state change. */
        IGNORED
    }

    public static ExecutionFailureResult requeue(JobQueueMessage message) {
        return new ExecutionFailureResult(Type.REQUEUE, message);
    }

    public static ExecutionFailureResult terminalFailed() {
        return new ExecutionFailureResult(Type.TERMINAL_FAILED, null);
    }

    public static ExecutionFailureResult ignored() {
        return new ExecutionFailureResult(Type.IGNORED, null);
    }
}
