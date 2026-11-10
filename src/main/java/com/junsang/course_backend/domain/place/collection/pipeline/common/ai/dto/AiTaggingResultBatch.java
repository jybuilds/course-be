package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import java.util.List;

/// OpenAI가 반환하는 장소별 태그 가중치 묶음이다.
public record AiTaggingResultBatch(List<AiTaggingResult> results) {

    public record AiTaggingResult(
            Long tempId,
            PlaceType placeType,
            @JsonAlias("keyword")
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<String> keywords,
            PriorityScores priorityScores,
            List<TagWeight> tags
    ) {
    }

    public record PriorityScores(Integer experience, Integer distinctiveness) {

        public static final int BASE_SCORE = 25;

        public PriorityScores {
            if (outsideRange(experience) || outsideRange(distinctiveness)) {
                throw new IllegalArgumentException("추천 우선도의 조정값은 -25부터 25까지의 정수여야 합니다.");
            }
        }

        private static boolean outsideRange(Integer adjustment) {
            return adjustment == null || adjustment < -BASE_SCORE || adjustment > BASE_SCORE;
        }
    }

    public record TagWeight(
            String code,
            Integer weight
    ) {
    }
}
