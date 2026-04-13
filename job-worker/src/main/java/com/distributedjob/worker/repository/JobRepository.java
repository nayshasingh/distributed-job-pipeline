package com.distributedjob.worker.repository;

import com.distributedjob.worker.entity.Job;
import com.distributedjob.worker.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Job j set j.status = :newStatus, j.updatedAt = CURRENT_TIMESTAMP "
            + "where j.id = :id and j.status = :expectedStatus")
    int claimIfStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("newStatus") JobStatus newStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Job j set j.status = :pendingStatus, j.updatedAt = CURRENT_TIMESTAMP "
            + "where j.id = :id and j.status = :runningStatus and j.updatedAt < :staleBefore")
    int reclaimStaleRunning(
            @Param("id") UUID id,
            @Param("runningStatus") JobStatus runningStatus,
            @Param("pendingStatus") JobStatus pendingStatus,
            @Param("staleBefore") Instant staleBefore);
}
