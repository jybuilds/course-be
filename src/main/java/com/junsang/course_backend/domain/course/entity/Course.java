package com.junsang.course_backend.domain.course.entity;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.CourseTag;
import com.junsang.course_backend.domain.place.entity.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 사용자의 입력과 태그를 기준으로 추천할 수 있도록 미리 저장한 대표 코스다.
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anchor_place_id", nullable = false)
    private Place anchorPlace;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "is_published", nullable = false)
    private boolean isPublished;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder ASC")
    private List<CourseItem> scheduleItems = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CourseTag> tags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "course_companion_types", joinColumns = @JoinColumn(name = "course_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "companion_type", nullable = false, length = 20)
    private Set<CompanionType> companionTypes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "course_time_slots", joinColumns = @JoinColumn(name = "course_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", nullable = false, length = 20)
    private Set<TimeSlot> timeSlots = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Course create(Area area, Place anchorPlace, String title, String description) {
        Course course = new Course();
        course.area = area;
        course.anchorPlace = anchorPlace;
        course.title = title;
        course.description = description;
        return course;
    }

    // 코스의 일정 장소를 순서대로 추가한다.
    public void addScheduleItem(Place place, CourseItemRole role, int itemOrder) {
        if (scheduleItems.stream().anyMatch(item -> item.getItemOrder() == itemOrder)) {
            throw new IllegalArgumentException("코스 내 일정 순서는 중복될 수 없습니다.");
        }
        scheduleItems.add(CourseItem.create(this, place, role, itemOrder));
    }

    // 코스 장소에서 다시 계산한 태그만 남기고 가중치를 동기화한다.
    public void synchronizeTags(Map<Tag, Integer> calculatedWeights) {
        Set<Long> calculatedTagIds = calculatedWeights.keySet().stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
        tags.removeIf(existing -> !calculatedTagIds.contains(existing.getTag().getId()));

        calculatedWeights.forEach((tag, weight) -> tags.stream()
                .filter(existing -> Objects.equals(existing.getTag().getId(), tag.getId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.updateWeight(weight),
                        () -> tags.add(CourseTag.create(this, tag, weight))
                ));
    }

    // 코스에 적합한 동행자 유형을 연결한다.
    public void addCompanionType(CompanionType companionType) {
        companionTypes.add(companionType);
    }

    // 코스에 적합한 시간대를 연결한다.
    public void addTimeSlot(TimeSlot timeSlot) {
        timeSlots.add(timeSlot);
    }

    // 대표 코스를 사용자에게 노출한다.
    public void publish() {
        isPublished = true;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
