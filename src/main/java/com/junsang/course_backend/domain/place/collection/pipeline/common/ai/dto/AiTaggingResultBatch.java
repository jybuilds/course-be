package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto;

import com.junsang.course_backend.domain.place.entity.PlaceType;
import java.util.List;

/// OpenAI가 반환하는 장소별 태그 가중치 묶음이다.
public record AiTaggingResultBatch(List<AiTaggingResult> results) {

    public record AiTaggingResult(
            Long tempId,
            PlaceType placeType,
            List<TagWeight> tags
    ) {
    }

    public record TagWeight(
            String code,
            Integer weight
    ) {
    }
}
