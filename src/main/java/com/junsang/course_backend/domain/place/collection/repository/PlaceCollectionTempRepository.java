package com.junsang.course_backend.domain.place.collection.repository;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCollectionTempRepository extends JpaRepository<PlaceCollectionTemp, Long> {

    Optional<PlaceCollectionTemp> findByProviderAndProviderPlaceId(
            PlaceProvider provider,
            String providerPlaceId
    );

    List<PlaceCollectionTemp> findByProcessingStepAndStatusOrderByIdAsc(
            PlaceCollectionStep processingStep,
            PlaceCollectionTempStatus status,
            Pageable pageable
    );

    long countByAreaId(Long areaId);
}
