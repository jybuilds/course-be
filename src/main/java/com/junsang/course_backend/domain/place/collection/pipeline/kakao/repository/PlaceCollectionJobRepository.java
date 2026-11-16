package com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCollectionJobRepository extends JpaRepository<PlaceCollectionJob, Long> {

    List<PlaceCollectionJob> findByAreaIdAndParentJobIsNullOrderByIdAsc(Long areaId);
    List<PlaceCollectionJob> findByParentJobIdOrderByIdAsc(Long parentId);

    boolean existsByStatusIn(List<CollectionJobStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from PlaceCollectionJob job where job.id = :id")
    java.util.Optional<PlaceCollectionJob> findByIdForUpdate(@Param("id") Long id);

    @Query("select job from PlaceCollectionJob job where (job.status = 'READY' and (job.nextRetryAt is null or job.nextRetryAt <= :now)) or "
            + "(job.status = 'FAILED' and job.nextRetryAt <= :now) order by job.id")
    List<PlaceCollectionJob> findRunnable(@Param("now") java.time.LocalDateTime now,
            org.springframework.data.domain.Pageable page);

}
