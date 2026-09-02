package com.junsang.course_backend.domain.place.collection.pipeline.ai.dto;

import java.util.List;

/// OpenAI가 반환하는 장소별 태그 가중치 묶음이다.
public record AiTaggingResultBatch(List<AiTaggingResult> results) {

    public record AiTaggingResult(
            Long tempId,
            List<TagWeight> tags
    ) {
    }

    public record TagWeight(
            String code,
            Integer weight
    ) {
    }
}
