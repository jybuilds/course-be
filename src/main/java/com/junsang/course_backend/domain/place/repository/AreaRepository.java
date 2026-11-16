package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.Area;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findByCityIdOrderById(Long cityId);

    List<Area> findByCodeIn(Collection<String> codes);

    Optional<Area> findByCode(String code);

    List<Area> findByCity_IdAndRankIsNotNullOrderByRankAscIdAsc(Long cityId , Pageable pageable);


    @Query("""
        select area
        from Area area
        where area.city.code = :cityCode
          and not exists (
              select job.id
              from PlaceCollectionJob job
              where job.area.id = area.id
          )
        order by area.id
        """)
    List<Area> findUnregisteredCollectionAreas(@Param("cityCode") String cityCode, Pageable page);
}
