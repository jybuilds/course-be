package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByProviderAndProviderPlaceId(PlaceProvider provider, String providerPlaceId);
}
