package com.distributedjob.scheduler.entity;

/**
 * Job queue priority. {@link #HIGH} is listed first in declaration so JPA {@code ORDER BY priority ASC}
 * processes high-priority work first when using ordinal ordering; we use {@code STRING} storage and explicit
 * CASE ordering in queries where needed.
 */
public enum JobPriority {
    HIGH,
    LOW
}
