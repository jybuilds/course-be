package com.junsang.course_backend.domain.stats.entity;

import com.junsang.course_backend.domain.place.entity.Area;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/// Area에 대한 사용자 행동을 누적 집계한다.
@Entity
@Table(name = "area_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class AreaStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false, unique = true)
    private Area area;

    // Area 상세 조회 횟수
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    // Area 추천 목록에 노출된 횟수
    @Column(name = "impression_count", nullable = false)
    private long impressionCount;

    // Area 선택 횟수
    @Column(name = "selection_count", nullable = false)
    private long selectionCount;

    // Area가 포함된 코스 저장 횟수
    @Column(name = "save_count", nullable = false)
    private long saveCount;


    // Area가 공유된 횟수
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

    public static AreaStats create(Area area) {
        AreaStats stats = new AreaStats();
        stats.area = area;
        return stats;
    }

    // Area 상세 조회를 집계한다.
    public void recordView() {
        viewCount++;
        lastViewedAt = LocalDateTime.now();
    }

    // Area 추천 노출을 집계한다.
    public void recordImpression() {
        impressionCount++;
    }

    // Area 선택을 집계한다.
    public void recordSelection() {
        selectionCount++;
        lastSelectedAt = LocalDateTime.now();
    }

    // Area가 포함된 코스 저장을 집계한다.
    public void recordSave() {
        saveCount++;
        lastSavedAt = LocalDateTime.now();
    }

    // Area 공유를 집계한다.
    public void recordShare() {
        shareCount++;
        lastSharedAt = LocalDateTime.now();
    }
}
