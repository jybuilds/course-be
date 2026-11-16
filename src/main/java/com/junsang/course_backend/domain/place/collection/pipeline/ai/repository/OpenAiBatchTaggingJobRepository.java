package com.junsang.course_backend.domain.place.collection.pipeline.ai.repository;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpenAiBatchTaggingJobRepository extends JpaRepository<OpenAiBatchTaggingJob, Long> {

    boolean existsByStatusAndErrorCodeIn(OpenAiBatchTaggingStatus status, Collection<PlaceRefinementErrorCode> errorCodes);

    List<OpenAiBatchTaggingJob> findByStatus(OpenAiBatchTaggingStatus status);

    long countByStatusIn(Collection<OpenAiBatchTaggingStatus> statuses);

    @Query("""
           SELECT job
           FROM OpenAiBatchTaggingJob job
           WHERE job.status = :status
            AND(job.errorCode IS NULL OR job.errorCode NOT IN :blockedCodes)
            AND(job.collectLeaseUntil IS NULL OR job.collectLeaseUntil < :now)
           ORDER BY
            CASE WHEN job.lastPolledAt IS NULL THEN 0 ELSE 1 END,
            job.lastPolledAt asc,
            job.id asc
           """)
    List<OpenAiBatchTaggingJob> findPollableJobs(
            @Param("status") OpenAiBatchTaggingStatus status,
            @Param("blockedCodes") Collection<PlaceRefinementErrorCode> blockedCodes,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
