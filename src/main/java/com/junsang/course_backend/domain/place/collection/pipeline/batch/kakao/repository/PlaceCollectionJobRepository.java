package com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.repository;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.PlaceCollectionJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCollectionJobRepository extends JpaRepository<PlaceCollectionJob, Long> {
    boolean existsByAreaIdAndCollectionProfileIdAndStatusIn(Long areaId, Long profileId, List<CollectionJobStatus> statuses);
}
