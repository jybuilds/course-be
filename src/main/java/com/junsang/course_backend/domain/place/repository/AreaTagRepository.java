package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.AreaTag;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
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

    // 특정 태그 top 10
    @Query("""
           SELECT areaTag
           FROM AreaTag areaTag
           JOIN FETCH areaTag.area
           JOIN FETCH areaTag.tag
           WHERE areaTag.tag.code = :tagCode
           AND areaTag.area.city.id = :cityId
           AND areaTag.recommendationRank IS NOT NULL
           ORDER BY areaTag.recommendationRank ASC, areaTag.area.id ASC
           """)
    List<AreaTag> findRankedAreaTagsByTagCode(
            @Param("tagCode") String tagCode,
            @Param("cityId") Long cityId,
            Pageable pageable);

    // Area, tagCode로 모든 AreaTags
    @Query("""
    select at
    from AreaTag at
    join fetch at.area a
    join fetch at.tag t
    where a.id in :areaIds
      and t.code in :tagCodes
    """)
    List<AreaTag> findAllForAreasAndTags(
            @Param("areaIds") List<Long> areaIds,
            @Param("tagCodes") List<String> tagCodes
    );

    // Area들의 모든 AreaTags
    @Query("""
    select at
    from AreaTag at
    join fetch at.area
    join fetch at.tag
    where at.area.id in :areaIds
    """)
    List<AreaTag> findAllByAreaIds(@Param("areaIds") List<Long> areaIds);

    // 도시·선택 태그의 전체 순위 대상과 응답 정보를 한 번에 조회한다.
    @Query("""
            select at
            from AreaTag at
            join fetch at.area a
            join fetch at.tag t
            where a.city.id = :cityId
              and t.code in :tagCodes
              and at.recommendationRank is not null
            order by a.id, t.displayOrder, t.id
            """)
    List<AreaTag> findRankedByCityAndTagCodes(
            @Param("cityId") Long cityId,
            @Param("tagCodes") List<String> tagCodes);

}
