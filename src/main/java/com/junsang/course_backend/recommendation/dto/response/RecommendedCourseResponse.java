package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.entity.CourseItem;

import java.util.List;
import java.util.Set;

/// 추천된 대표 코스와 일정 요약이다.
public record RecommendedCourseResponse(
        Long courseId,
        String title,
        String description,
        Long anchorPlaceId,
        String anchorPlaceName,
        double score,
        MatchType matchType,
        Set<String> matchedTags,
        Set<String> unmatchedTags,
        ScoreBreakdown scoreBreakdown,
        List<ScheduleItemResponse> scheduleItems
) {
    public static RecommendedCourseResponse from(
            Course course,
            double score,
            MatchType matchType,
            Set<String> matchedTags,
            Set<String> unmatchedTags,
            ScoreBreakdown scoreBreakdown
    ) {
        return new RecommendedCourseResponse(
                course.getId(), course.getTitle(), course.getDescription(),
                course.getAnchorPlace().getId(), course.getAnchorPlace().getName(), score, matchType,
                matchedTags, unmatchedTags, scoreBreakdown,
                course.getScheduleItems().stream().map(ScheduleItemResponse::from).toList()
        );
    }

    public record ScoreBreakdown(
            double tagScore,
            double companionScore,
            double timeSlotScore,
            double popularityScore
    ) {
    }

    public record ScheduleItemResponse(
            Long placeId,
            String placeName,
            String placeType,
            String role,
            int order
    ) {
        private static ScheduleItemResponse from(CourseItem item) {
            return new ScheduleItemResponse(
                    item.getPlace().getId(), item.getPlace().getName(),
                    item.getPlace().getPlaceType().name(), item.getRole().name(), item.getItemOrder()
            );
        }
    }

    public enum MatchType {
        FULL_MATCH,
        PARTIAL_MATCH,
        POPULAR_FALLBACK
    }
}
