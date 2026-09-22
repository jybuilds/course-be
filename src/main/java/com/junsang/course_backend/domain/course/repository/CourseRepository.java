package com.junsang.course_backend.domain.course.repository;

import com.junsang.course_backend.domain.course.entity.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/// 추천 대상 코스 조회를 담당한다.
public interface CourseRepository extends JpaRepository<Course, Long> {
    @EntityGraph(attributePaths = {
            "area", "anchorPlace", "scheduleItems", "scheduleItems.place",
            "tags", "tags.tag"
    })
    List<Course> findByAreaCityIdAndIsPublishedTrue(Long cityId);
}
