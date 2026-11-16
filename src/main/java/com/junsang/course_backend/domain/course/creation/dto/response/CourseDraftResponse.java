package com.junsang.course_backend.domain.course.creation.dto.response;

import com.junsang.course_backend.domain.course.entity.CourseItemRole;

import java.util.List;

/// 앵커에서 시작해 칸마다 추천순 후보를 이어 붙인 코스 초안이다. 칸마다 0번째 장소가 추천이다.
public record CourseDraftResponse(
        Long anchorPlaceId,
        String anchorPlaceName,
        List<SlotResponse> slots
) {
    /// basePlaceId는 이 칸의 거리를 잰 기준 장소다. 첫 칸은 앵커, 이후는 앞 칸의 추천 장소다.
    public record SlotResponse(
            int order,
            CourseItemRole role,
            Long basePlaceId,
            List<CourseCandidateResponse> places
    ) {
    }
}
