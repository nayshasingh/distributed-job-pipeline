package com.distributedjob.worker.service;

import com.distributedjob.worker.config.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDistributedLockServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private JobDistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lockService = new JobDistributedLockService(
                redis,
                new WorkerProperties(4, 900, "job-worker:exec:", 600));
    }

    @Test
    void tryLockSucceedsWhenKeyIsAbsent() {
        UUID jobId = UUID.randomUUID();
        when(valueOperations.setIfAbsent(eq("job-worker:exec:" + jobId), anyString(), eq(Duration.ofSeconds(600))))
                .thenReturn(true);

        Optional<JobDistributedLockService.LockHandle> lock = lockService.tryLock(jobId);

        assertThat(lock).isPresent();
        assertThat(lock.get().key()).isEqualTo("job-worker:exec:" + jobId);
    }

    @Test
    void tryLockFailsWhenKeyAlreadyExists() {
        UUID jobId = UUID.randomUUID();
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(lockService.tryLock(jobId)).isEmpty();
    }

    @Test
    void unlockRunsTokenCheckScript() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        JobDistributedLockService.LockHandle handle = new JobDistributedLockService.LockHandle("k", "token");

        lockService.unlock(handle);

        verify(redis).execute(any(RedisScript.class), anyList(), eq("token"));
    }
}
