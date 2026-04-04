package com.distributedjob.scheduler.repository;

import com.distributedjob.scheduler.entity.Job;
import com.distributedjob.scheduler.entity.JobPriority;
import com.distributedjob.scheduler.entity.JobStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "org.hibernate.lock.skip_locked", value = "true"))
    @Query("select j from Job j where j.status = :status "
            + "order by case when j.priority = :highPriority then 0 else 1 end asc, j.createdAt asc")
    List<Job> findPendingForClaim(
            @Param("status") JobStatus status,
            @Param("highPriority") JobPriority highPriority,
            Pageable pageable);

    Optional<Job> findByIdAndStatus(UUID id, JobStatus status);

    List<Job> findAllByOrderByCreatedAtDesc();
}
