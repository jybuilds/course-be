package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.collection.CollectionJobStatus;
import com.junsang.course_backend.domain.place.entity.PlaceCollectionJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCollectionJobRepository extends JpaRepository<PlaceCollectionJob, Long> {
    boolean existsByAreaIdAndCollectionProfileIdAndStatusIn(Long areaId, Long profileId, List<CollectionJobStatus> statuses);
}
