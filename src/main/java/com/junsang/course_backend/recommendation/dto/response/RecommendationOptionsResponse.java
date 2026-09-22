package com.junsang.course_backend.recommendation.dto.response;

import java.util.List;

/// 추천 요청 화면을 구성하는 선택지 모음이다.
public record RecommendationOptionsResponse(
        List<CityOptionResponse> cities,
        List<CompanionTypeOptionResponse> companionTypes,
        List<TimeSlotOptionResponse> timeSlots,
        List<TagResponse> tags,
        SelectionRulesResponse selectionRules
) {
}
