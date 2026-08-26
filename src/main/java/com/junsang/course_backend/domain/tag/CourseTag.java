package com.junsang.course_backend.domain.tag;

import com.junsang.course_backend.domain.course.entity.Course;
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

/// 코스 전체의 활동·음식·분위기 특성을 연결하는 태그다.
@Entity
@Table(name = "course_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CourseTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private int weight;

    public static CourseTag create(Course course, Tag tag, int weight) {
        CourseTag courseTag = new CourseTag();
        courseTag.course = course;
        courseTag.tag = tag;
        courseTag.weight = weight;
        return courseTag;
    }
}
