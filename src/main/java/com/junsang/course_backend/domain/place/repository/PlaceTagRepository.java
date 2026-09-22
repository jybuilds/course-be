package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.PlaceTag;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 장소에 연결된 태그 조회를 담당한다.
public interface PlaceTagRepository extends JpaRepository<PlaceTag, Long> {
    List<PlaceTag> findByPlaceIdIn(Set<Long> placeIds);

    @Query("""
            select placeTag
            from PlaceTag placeTag
            join fetch placeTag.tag
            where placeTag.place.id in :placeIds
            order by placeTag.place.id, placeTag.weight desc
            """)
    List<PlaceTag> findWithTagByPlaceIdIn(@Param("placeIds") Set<Long> placeIds);

    List<PlaceTag> findByPlaceId(Long placeId);
}
