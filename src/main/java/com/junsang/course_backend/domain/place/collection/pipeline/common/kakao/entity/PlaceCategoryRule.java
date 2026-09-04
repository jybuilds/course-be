package com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity;

import com.junsang.course_backend.domain.place.entity.PlaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 장소명과 카카오·네이버 카테고리에 포함된 키워드로 PlaceType을 보정하는 운영 규칙이다.
@Entity
@Table(name = "place_category_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PlaceCategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_place_type", nullable = false, length = 20)
    private PlaceType targetPlaceType;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 테스트와 운영 등록에서 같은 기본값을 사용해 새 규칙을 만든다.
    public static PlaceCategoryRule create(String keyword, PlaceType targetPlaceType, int priority) {
        PlaceCategoryRule rule = new PlaceCategoryRule();
        rule.keyword = keyword;
        rule.targetPlaceType = targetPlaceType;
        rule.priority = priority;
        rule.isActive = true;
        return rule;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
