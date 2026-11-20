package com.junsang.course_backend.domain.course.repository;

import com.junsang.course_backend.domain.course.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/// 추천 대상 코스 조회를 담당한다.
public interface CourseRepository extends JpaRepository<Course, Long> {
    @EntityGraph(attributePaths = {
            "area", "anchorPlace", "scheduleItems", "scheduleItems.place",
            "tags", "tags.tag"
    })
    List<Course> findByAreaCityIdAndIsPublishedTrue(Long cityId);

    // 목록인 scheduleItems와 tags를 한 쿼리로 함께 가져오면 행이 곱해져 일정이 태그 수만큼 중복된다.
    // 그래서 상세 조회는 두 쿼리로 나눈다.
    @EntityGraph(attributePaths = {"area", "area.city", "anchorPlace", "tags", "tags.tag"})
    Optional<Course> findWithTagsById(Long id);

    @EntityGraph(attributePaths = {"scheduleItems", "scheduleItems.place"})
    Optional<Course> findWithScheduleItemsById(Long id);
}
