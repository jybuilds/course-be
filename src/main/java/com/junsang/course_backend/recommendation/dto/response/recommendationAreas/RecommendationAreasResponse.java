package com.junsang.course_backend.recommendation.dto.response.recommendationAreas;

import java.util.List;

public record RecommendationAreasResponse(
        List<areaResponse> areas
) {
    public record areaResponse(
            Long areaId,
            String areaName,
            List<AreaTagResponse> areaTags
    ) {
    }

    public record AreaTagResponse(
            String tagName,
            List<RecommendedVisitPlace> recommendedVisitPlaces,
            List<String> attractionFeatures,
            List<String> differentiators
    ) {
    }

    public record RecommendedVisitPlace(Long placeId, String name) {
    }
}
