package com.distributedjob.worker.entity;

/**
 * Mirrors scheduler {@code JobPriority}; stored as VARCHAR in {@code jobs.priority}.
 */
public enum JobPriority {
    HIGH,
    LOW
}
