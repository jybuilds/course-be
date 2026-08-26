package com.junsang.course_backend.domain.course.entity;

import com.junsang.course_backend.domain.place.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 코스 안에서 방문할 장소와 일정 순서를 표현한다.
@Entity
@Table(name = "course_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CourseItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    public static CourseItem create(Course course, Place place, int itemOrder) {
        if (itemOrder < 0) {
            throw new IllegalArgumentException("일정 순서는 0 이상이어야 합니다.");
        }
        CourseItem item = new CourseItem();
        item.course = course;
        item.place = place;
        item.itemOrder = itemOrder;
        return item;
    }
}
