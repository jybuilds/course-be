package com.junsang.course_backend.domain.place.collection.kakao.repository;

import com.junsang.course_backend.domain.place.collection.kakao.entity.PlaceCategoryRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCategoryRuleRepository extends JpaRepository<PlaceCategoryRule, Long> {

    List<PlaceCategoryRule> findByIsActiveTrueOrderByPriorityDescIdAsc();
}
