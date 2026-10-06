package com.junsang.course_backend.domain.stats.repository;

import com.junsang.course_backend.domain.stats.entity.AreaStats;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Area 통계 조회를 담당한다.
public interface AreaStatsRepository extends JpaRepository<AreaStats, Long> {

    Optional<AreaStats> findByAreaId(Long areaId);

    @Query("""
            select areaStats
            from AreaStats areaStats
            join fetch areaStats.area
            where areaStats.area.id in :areaIds
            """)
    List<AreaStats> findByAreaIdIn(@Param("areaIds") Set<Long> areaIds);
}
