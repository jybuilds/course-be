package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByProviderAndProviderPlaceId(PlaceProvider provider, String providerPlaceId);

    @EntityGraph(attributePaths = "area")
    Page<Place> findAllByOrderByIdAsc(Pageable pageable);

    // 검토 화면은 전체 또는 유형별로 추천 우선도 합계 내림차순을 사용한다.
    @EntityGraph(attributePaths = "area")
    @Query("""
            select p from Place p
            where (:placeType is null or p.placeType = :placeType)
            order by (coalesce(p.priorityExperience, 0)
                    + coalesce(p.priorityDistinctiveness, 0)
                    + coalesce(p.priorityOutingDemand, 0)) desc, p.id asc
            """)
    Page<Place> findAllForCheckOrderByPriority(
            @Param("placeType") PlaceType placeType,
            Pageable pageable
    );

    List<Place> findAllByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    long countByAreaId(Long areaId);
}
