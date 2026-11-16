package com.junsang.course_backend.domain.course.creation.dto.response;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.entity.CourseItem;

import java.util.List;
import java.util.Set;

/// 저장된 직접 생성 코스와 자동 계산된 코스 태그다.
public record CreatedCourseResponse(
        Long courseId,
        Long areaId,
        Long anchorPlaceId,
        String anchorPlaceName,
        String title,
        String description,
        boolean published,
        List<ScheduleItemResponse> scheduleItems,
        Set<String> generatedTags
) {
    public static CreatedCourseResponse of(Course course, Set<String> generatedTags) {
        return new CreatedCourseResponse(
                course.getId(),
                course.getArea().getId(),
                course.getAnchorPlace().getId(),
                course.getAnchorPlace().getName(),
                course.getTitle(),
                course.getDescription(),
                course.isPublished(),
                course.getScheduleItems().stream().map(ScheduleItemResponse::from).toList(),
                generatedTags
        );
    }

    public record ScheduleItemResponse(
            Long placeId,
            String placeName,
            String placeType,
            String role,
            int itemOrder
    ) {
        private static ScheduleItemResponse from(CourseItem item) {
            return new ScheduleItemResponse(
                    item.getPlace().getId(),
                    item.getPlace().getName(),
                    item.getPlace().getPlaceType().name(),
                    item.getRole().name(),
                    item.getItemOrder()
            );
        }
    }
}
