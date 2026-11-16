package com.junsang.course_backend.domain.place.collection.pipeline.naver.dto;

/// 네이버 순차 정제 처리 집계다.
public record PlaceRefinementBatchResponse(
        int requestedCount,
        int processedCount,
        int successCount,
        int failedCount
) {
}
