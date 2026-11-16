package com.junsang.course_backend.domain.course.creation.dto.response;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceType;

import java.math.BigDecimal;
import java.util.Set;

/// 직접 코스 생성에서 다음 일정으로 고를 수 있는 장소 후보다.
public record CourseCandidateResponse(
        Long placeId,
        String name,
        String addressName,
        String roadAddressName,
        PlaceType placeType,
        BigDecimal latitude,
        BigDecimal longitude,
        String placeUrl,
        int distanceMeters,
        double score,
        Set<String> matchedTags,
        Set<String> unmatchedTags,
        ScoreBreakdown scoreBreakdown
) {
    public static CourseCandidateResponse of(
            Place place,
            double distanceMeters,
            double score,
            Set<String> matchedTags,
            Set<String> unmatchedTags,
            ScoreBreakdown scoreBreakdown
    ) {
        return new CourseCandidateResponse(
                place.getId(),
                place.getName(),
                place.getAddressName(),
                place.getRoadAddressName(),
                place.getPlaceType(),
                place.getLatitude(),
                place.getLongitude(),
                place.getPlaceUrl(),
                (int) Math.round(distanceMeters),
                score,
                matchedTags,
                unmatchedTags,
                scoreBreakdown
        );
    }

    public record ScoreBreakdown(
            double tagScore,
            double proximityScore
    ) {
    }
}
