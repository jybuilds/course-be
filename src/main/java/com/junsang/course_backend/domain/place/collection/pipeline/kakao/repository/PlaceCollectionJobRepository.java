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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from PlaceCollectionJob job where job.id = :id")
    java.util.Optional<PlaceCollectionJob> findByIdForUpdate(@Param("id") Long id);

    @Query("select job from PlaceCollectionJob job where job.status = 'READY' or "
            + "(job.status = 'FAILED' and job.nextRetryAt <= :now) order by job.id")
    List<PlaceCollectionJob> findRunnable(@Param("now") java.time.LocalDateTime now,
            org.springframework.data.domain.Pageable page);

    @Query("""
            select distinct job.collectionProfile.id
            from PlaceCollectionJob job
            where job.area.id = :areaId
              and job.status in :statuses
            """)
    List<Long> findCollectionProfileIdsByAreaIdAndStatusIn(
            @Param("areaId") Long areaId,
            @Param("statuses") List<CollectionJobStatus> statuses
    );
}
