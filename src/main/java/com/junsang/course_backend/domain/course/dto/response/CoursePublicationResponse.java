package com.junsang.course_backend.domain.course.dto.response;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.place.entity.CourseTag;
import com.junsang.course_backend.domain.place.entity.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/// 코스의 공개 상태와, 추천에서 매칭 근거가 되는 코스 태그다.
public record CoursePublicationResponse(
        Long courseId,
        String title,
        boolean published,
        Set<String> tags
) {
    public static CoursePublicationResponse of(Course course) {
        return new CoursePublicationResponse(
                course.getId(),
                course.getTitle(),
                course.isPublished(),
                course.getTags().stream()
                        .map(CourseTag::getTag)
                        .map(Tag::getCode)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }
}
