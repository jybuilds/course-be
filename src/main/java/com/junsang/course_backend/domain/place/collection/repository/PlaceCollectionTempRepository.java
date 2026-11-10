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

    List<PlaceCollectionTemp> findByProviderAndProviderPlaceIdIn(
            PlaceProvider provider,
            Collection<String> providerPlaceIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlaceCollectionTemp> findByProcessingStepAndStatusOrderByIdAsc(
            PlaceCollectionStep processingStep,
            PlaceCollectionTempStatus status,
            Pageable pageable
    );

    List<PlaceCollectionTemp> findByAreaIdAndProcessingStepAndStatusOrderByIdAsc(
            Long areaId,
            PlaceCollectionStep processingStep,
            PlaceCollectionTempStatus status,
            Pageable pageable
    );

    // 신규 대기 건과 OpenAI 요청 자체가 실패한 건만 다음 Batch 제출 후보로 조회한다.
    @Query("""
            select temp
            from PlaceCollectionTemp temp
            where temp.processingStep = :processingStep
              and (
                    (temp.status = :pendingStatus and :retryOnly = false)
                    or (
                        temp.status = :failedStatus
                        and temp.errorCode in :retryableErrorCodes
                        and ((temp.aiRetryCount < 3 and temp.lastAttemptAt <= :shortRetryBefore)
                          or (temp.aiRetryCount >= 3 and temp.lastAttemptAt <= :longRetryBefore))
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
            @Param("shortRetryBefore") LocalDateTime shortRetryBefore,
            @Param("longRetryBefore") LocalDateTime longRetryBefore,
            @Param("retryOnly") boolean retryOnly,
            Pageable pageable
    );

    // 지정 Area에서 신규 대기 건과 재시도 가능한 OpenAI 요청 실패 건만 조회한다.
    @Query("""
            select temp
            from PlaceCollectionTemp temp
            where temp.area.id = :areaId
              and temp.processingStep = :processingStep
              and (
                    (temp.status = :pendingStatus and :retryOnly = false)
                    or (
                        temp.status = :failedStatus
                        and temp.errorCode in :retryableErrorCodes
                        and ((temp.aiRetryCount < 3 and temp.lastAttemptAt <= :shortRetryBefore)
                          or (temp.aiRetryCount >= 3 and temp.lastAttemptAt <= :longRetryBefore))
                    )
              )
            order by temp.id asc
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlaceCollectionTemp> findAiBatchSubmissionTargetsByAreaId(
            @Param("areaId") Long areaId,
            @Param("processingStep") PlaceCollectionStep processingStep,
            @Param("pendingStatus") PlaceCollectionTempStatus pendingStatus,
            @Param("failedStatus") PlaceCollectionTempStatus failedStatus,
            @Param("retryableErrorCodes") List<PlaceRefinementErrorCode> retryableErrorCodes,
            @Param("shortRetryBefore") LocalDateTime shortRetryBefore,
            @Param("longRetryBefore") LocalDateTime longRetryBefore,
            @Param("retryOnly") boolean retryOnly,
            Pageable pageable
    );

    @Query("""
            select temp from PlaceCollectionTemp temp
            where (:areaId is null or temp.area.id = :areaId)
              and temp.processingStep = :step
              and (temp.status = :pending or (
                    temp.status = :failed
                    and temp.errorCode in :retryable
                    and temp.attemptCount < 3
                    and temp.lastAttemptAt <= :retryBefore
              ))
            order by temp.id asc
            """)
    List<PlaceCollectionTemp> findNaverTargets(
            @Param("areaId") Long areaId,
            @Param("step") PlaceCollectionStep step,
            @Param("pending") PlaceCollectionTempStatus pending,
            @Param("failed") PlaceCollectionTempStatus failed,
            @Param("retryable") List<PlaceRefinementErrorCode> retryable,
            @Param("retryBefore") java.time.LocalDateTime retryBefore,
            Pageable pageable
    );

    long countByAreaId(Long areaId);

    List<PlaceCollectionTemp> findByAiBatchJobIdOrderById(Long aiBatchJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select temp
            from PlaceCollectionTemp temp
            where temp.id = :tempId
            """)
    Optional<PlaceCollectionTemp> findByIdForUpdate(@Param("tempId") Long tempId);
}
