package com.junsang.course_backend.domain.place.collection.pipeline.single.dto;

/// 사용자가 선택한 앵커와 후속 비동기 정제 등록 결과다.
public record SingleAnchorResponse(
        Long placeId,
        boolean enrichmentQueued,
        SinglePlaceCandidateResponse place
) {
}
