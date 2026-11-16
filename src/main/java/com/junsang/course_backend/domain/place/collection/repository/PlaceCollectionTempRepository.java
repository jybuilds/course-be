package com.junsang.course_backend.domain.place.collection.repository;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCollectionTempRepository extends JpaRepository<PlaceCollectionTemp, Long> {

    Optional<PlaceCollectionTemp> findByProviderAndProviderPlaceId(
            PlaceProvider provider,
            String providerPlaceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlaceCollectionTemp> findByProviderAndProviderPlaceIdIn(
            PlaceProvider provider,
            Collection<String> providerPlaceIds
    );

    // 신규 대기 건과 OpenAI 요청 자체가 실패한 건만 다음 Batch 제출 후보로 조회한다.
    @Query("""
            select temp
            from PlaceCollectionTemp temp
            where temp.processingStep = :processingStep
              and (
                    temp.status = :pendingStatus
                    or (
                        temp.status = :failedStatus
                        and temp.errorCode in :retryableErrorCodes
                        and temp.nextRetryAt <= :retryBefore
                    )
              )
            order by temp.id asc
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlaceCollectionTemp> findAiBatchSubmissionTargets(
            @Param("processingStep") PlaceCollectionStep processingStep,
            @Param("pendingStatus") PlaceCollectionTempStatus pendingStatus,
            @Param("failedStatus") PlaceCollectionTempStatus failedStatus,
            @Param("retryableErrorCodes") List<PlaceRefinementErrorCode> retryableErrorCodes,
            @Param("retryBefore") LocalDateTime retryBefore,
            Pageable pageable
    );

    @Query("""
            select temp from PlaceCollectionTemp temp
            where temp.processingStep = :step
              and ((temp.status = :pending and (temp.nextRetryAt is null or temp.nextRetryAt <= :retryBefore)) or (
                    temp.status = :failed
                    and temp.errorCode in :retryable
                    and temp.nextRetryAt <= :retryBefore
              ))
            order by temp.id asc
            """)
    List<PlaceCollectionTemp> findNaverTargets(
            @Param("step") PlaceCollectionStep step,
            @Param("pending") PlaceCollectionTempStatus pending,
            @Param("failed") PlaceCollectionTempStatus failed,
            @Param("retryable") List<PlaceRefinementErrorCode> retryable,
            @Param("retryBefore") java.time.LocalDateTime retryBefore,
            Pageable pageable
    );

    @Query("select count(temp) from PlaceCollectionTemp temp where temp.processingStep=:step and "
            + "(temp.status in ('PENDING','PROCESSING') or (temp.status='FAILED' and temp.nextRetryAt is not null))")
    long countBacklog(@Param("step") PlaceCollectionStep step);

    @Query("select max(temp.nextRetryAt) from PlaceCollectionTemp temp where temp.processingStep='AI_TAGGING' "
            + "and temp.status='FAILED' and temp.errorCode='AI_API_QUOTA_EXHAUSTED'")
    LocalDateTime findAiQuotaResumeAt();

    List<PlaceCollectionTemp> findByAiBatchJobIdOrderById(Long aiBatchJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select temp
            from PlaceCollectionTemp temp
            where temp.id = :tempId
            """)
    Optional<PlaceCollectionTemp> findByIdForUpdate(@Param("tempId") Long tempId);
}
