package com.junsang.course_backend.domain.tag.repository;

import com.junsang.course_backend.domain.tag.TagOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// 태그별 활성 선택지 조회를 담당한다.
public interface TagOptionRepository extends JpaRepository<TagOption, Long> {
    List<TagOption> findByTagIdAndIsActiveTrueOrderByDisplayOrder(Long tagId);
}
