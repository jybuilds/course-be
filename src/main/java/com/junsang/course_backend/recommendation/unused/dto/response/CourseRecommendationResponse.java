package com.junsang.course_backend.recommendation.unused.dto.response;

import java.util.List;

/// 대표 코스 추천 결과다.
public record CourseRecommendationResponse(
        RecommendationMode recommendationMode,
        RecommendationReason reason,
        List<RecommendedCourseResponse> courses
) {
    public enum RecommendationMode {
        PERSONALIZED,
        MIXED,
        POPULAR_FALLBACK
    }

    public enum RecommendationReason {
        NONE,
        INSUFFICIENT_TAG_MATCHES,
        NO_TAG_MATCH,
        NO_COURSES
    }
}
