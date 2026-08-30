package com.junsang.course_backend.domain.place.collection.ai.dto;

import java.util.List;

/// AI 태깅 배치 처리 건수와 개별 결과다.
public record AiTaggingBatchResponse(
        int requestedCount,
        int processedCount,
        int successCount,
        int failureCount,
        List<AiTaggingResponse> results
) {
}
