package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.AreaTag;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 여러 Area의 태그와 가중치를 한 번에 조회한다.
public interface AreaTagRepository extends JpaRepository<AreaTag, Long> {

    @Query("""
            select areaTag
            from AreaTag areaTag
            join fetch areaTag.area
            join fetch areaTag.tag
            where areaTag.area.id in :areaIds
            order by areaTag.area.id, areaTag.weight desc
            """)
    List<AreaTag> findWithTagByAreaIdIn(@Param("areaIds") Set<Long> areaIds);
}
