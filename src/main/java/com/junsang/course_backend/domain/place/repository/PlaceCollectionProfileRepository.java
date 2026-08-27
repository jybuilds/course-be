package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.PlaceCollectionProfile;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCollectionProfileRepository extends JpaRepository<PlaceCollectionProfile, Long> {
    List<PlaceCollectionProfile> findByProviderAndIsActiveTrueOrderById(PlaceProvider provider);
}
