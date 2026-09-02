package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

/// 도시 선택지 조회를 담당한다.
public interface CityRepository extends JpaRepository<City, Long> {
}
