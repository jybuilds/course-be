package com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.repository;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.PlaceCollectionJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCollectionJobRepository extends JpaRepository<PlaceCollectionJob, Long> {

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
