package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.PlaceTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

/// 장소에 연결된 태그 조회를 담당한다.
public interface PlaceTagRepository extends JpaRepository<PlaceTag, Long> {
    List<PlaceTag> findByPlaceIdIn(Set<Long> placeIds);

    List<PlaceTag> findByPlaceId(Long placeId);
}
