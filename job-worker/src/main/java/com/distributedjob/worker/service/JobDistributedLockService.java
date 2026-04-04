package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * <h2>Distributed locking with Redis (why and how)</h2>
 *
 * <p><b>Problem:</b> Several {@code job-worker} instances can consume Kafka messages for the same
 * {@code jobId} at roughly the same time (at-least-once delivery, republish retries, or duplicate
 * records). The database “claim” (PENDING → RUNNING) is atomic, but a lock still helps: we avoid
 * useless claim attempts and, more importantly, we serialize <i>all</i> execution-related work
 * for a given {@code jobId} across the cluster so only one JVM runs the job at a time.
 *
 * <p><b>Acquire:</b> We use {@code SET key token NX EX ttl} (via {@code setIfAbsent} with a TTL).
 * {@code NX} means “set only if not present”—so exactly one client wins per key. The TTL is
 * essential: if a worker dies after acquiring the lock but before releasing it, the key expires
 * automatically so the job is not blocked forever (at-most-once lock hold bounded by {@code ttl}).
 *
 * <p><b>Token value:</b> The lock value is a random UUID unique to this acquisition. A worker must
 * only delete the key if the value still matches that token. Otherwise a slow worker could delete a
 * key that was re-created by another process after expiry—classic “you released someone else’s
 * lock” bug.
 *
 * <p><b>Release:</b> We run a small Lua script in Redis: “GET key; if value == token then DEL key”.
 * Redis executes the script atomically, so there is no race between checking and deleting.
 *
 * <p><b>Skip when locked:</b> If {@code setIfAbsent} returns false, another worker already holds the
 * lock for this {@code jobId}; we skip execution for this message. Kafka still acks (handled by the
 * listener); the other worker (or a later message after TTL expiry) continues the work.
 */
@Service
public class JobDistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(JobDistributedLockService.class);

    /**
     * Atomically delete the lock only if it still holds our token (see class Javadoc).
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();
    static {
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final WorkerProperties workerProperties;

    public JobDistributedLockService(StringRedisTemplate redis, WorkerProperties workerProperties) {
        this.redis = redis;
        this.workerProperties = workerProperties;
    }

    /**
     * Try to take the cluster-wide execution lock for this job. Empty if another worker already holds it.
     */
    public Optional<LockHandle> tryLock(UUID jobId) {
        String key = workerProperties.distributedLockKeyPrefix() + jobId;
        String token = UUID.randomUUID().toString();
        long ttl = Math.max(1L, workerProperties.distributedLockTtlSeconds());
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(ttl));
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Acquired Redis execution lock for job {} (key={}, ttl={}s)", jobId, key, ttl);
            return Optional.of(new LockHandle(key, token));
        }
        log.info("Skipping job {} — Redis execution lock already held (key={})", jobId, key);
        return Optional.empty();
    }

    /**
     * Releases the lock only if this handle still owns it (token match).
     */
    public void unlock(LockHandle handle) {
        Long deleted = redis.execute(UNLOCK_SCRIPT, Collections.singletonList(handle.key()), handle.token());
        if (deleted != null && deleted == 1L) {
            log.debug("Released Redis execution lock (key={})", handle.key());
        } else {
            log.warn("Redis execution lock was not released (expired or taken over): key={}", handle.key());
        }
    }

    /**
     * Handle returned to the caller so only this acquisition can unlock safely.
     */
    public record LockHandle(String key, String token) {
    }
}
