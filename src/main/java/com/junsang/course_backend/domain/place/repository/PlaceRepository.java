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
              and (:areaId is null or p.area.id = :areaId)
            order by (coalesce(p.priorityExperience, 0)
                    + coalesce(p.priorityDistinctiveness, 0)) desc, p.id asc
            """)
    Page<Place> findAllForCheckOrderByPriority(
            @Param("placeType") PlaceType placeType,
            @Param("areaId") Long areaId,
            Pageable pageable
    );

    @Query("""
            select a.id as areaId, a.name as areaName, a.city.name as cityName,
                   p.placeType as placeType, count(p.id) as placeCount
            from Area a left join Place p on p.area.id = a.id
            group by a.id, a.name, a.city.name, p.placeType
            order by a.city.id, a.id
            """)
    List<PlaceCheckAreaCount> findPlaceCheckCountsByArea();

    interface PlaceCheckAreaCount {
        Long getAreaId();
        String getAreaName();
        String getCityName();
        PlaceType getPlaceType();
        Long getPlaceCount();
    }

    List<Place> findAllByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    long countByAreaId(Long areaId);

    // idx_places_area_type_active 인덱스를 그대로 사용한다.
    List<Place> findByAreaIdAndPlaceTypeAndIsActiveTrue(Long areaId, PlaceType placeType);
}
