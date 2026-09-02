package com.junsang.course_backend.domain.stats.repository;

import com.junsang.course_backend.domain.stats.entity.CourseStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/// 코스 통계 조회를 담당한다.
public interface CourseStatsRepository extends JpaRepository<CourseStats, Long> {
    List<CourseStats> findByCourseIdIn(Set<Long> courseIds);

    // 통계 행이 없으면 생성하고, 있으면 추천 노출 횟수를 한 번에 증가시킨다.
    @Modifying
    @Query(value = """
            INSERT INTO course_stats (course_id, impression_count)
            SELECT id, 1
            FROM courses
            WHERE id IN (:courseIds)
            ON CONFLICT (course_id)
            DO UPDATE SET impression_count = course_stats.impression_count + 1
            """, nativeQuery = true)
    int increaseImpressionCounts(@Param("courseIds") Set<Long> courseIds);
}
