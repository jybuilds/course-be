package com.junsang.course_backend.domain.place.repository;

import com.junsang.course_backend.domain.place.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

/// 활성 태그 조회를 담당한다.
public interface TagRepository extends JpaRepository<Tag, Long> {
    long countByCodeInAndIsActiveTrue(Set<String> codes);
}
