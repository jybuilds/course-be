package com.junsang.course_backend.domain.place.collection.test.pipeline.dto;

import com.junsang.course_backend.domain.place.collection.pipeline.naver.dto.PlaceRefinementBatchResponse;

/// Area 수집부터 OpenAI Batch 제출까지 실행한 테스트 결과다.
public record AreaCollectionPipelineResponse(
        Long areaId,
        long storedTempCount,
        PlaceRefinementBatchResponse refinement,
        Long aiBatchJobId
) {
}
