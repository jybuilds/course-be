package com.junsang.course_backend.domain.stats.entity;

import com.junsang.course_backend.domain.course.entity.Course;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/// 코스에 대한 사용자 행동을 누적 집계한다.
@Entity
@Table(name = "course_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CourseStats {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    private Course course;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "selection_count", nullable = false)
    private long selectionCount;

    @Column(name = "save_count", nullable = false)
    private long saveCount;

    @Column(name = "share_count", nullable = false)
    private long shareCount;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    @Column(name = "last_selected_at")
    private LocalDateTime lastSelectedAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "last_shared_at")
    private LocalDateTime lastSharedAt;

    public static CourseStats create(Course course) {
        CourseStats stats = new CourseStats(); stats.course = course; return stats;

    }

    // 코스 조회를 집계한다.
    public void recordView() { viewCount++; lastViewedAt = LocalDateTime.now(); }
    // 코스 선택을 집계한다.
    public void recordSelection() { selectionCount++; lastSelectedAt = LocalDateTime.now(); }
    // 코스 저장을 집계한다.
    public void recordSave() { saveCount++; lastSavedAt = LocalDateTime.now(); }
    // 코스 공유를 집계한다.
    public void recordShare() { shareCount++; lastSharedAt = LocalDateTime.now(); }
}
