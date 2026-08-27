package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.Area;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findByCityIdOrderById(Long cityId);
    Optional<Area> findByCode(String code);
}
