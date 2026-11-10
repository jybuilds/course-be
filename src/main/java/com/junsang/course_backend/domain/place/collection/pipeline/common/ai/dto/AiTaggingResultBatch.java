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

    public record PriorityScores(Integer experience, Integer distinctiveness, Integer outingDemand) {

        public static final int MAX_EXPERIENCE = 40;
        public static final int MAX_DISTINCTIVENESS = 35;
        public static final int MAX_OUTING_DEMAND = 25;

        public PriorityScores {
            if (outsideRange(experience, MAX_EXPERIENCE)
                    || outsideRange(distinctiveness, MAX_DISTINCTIVENESS)
                    || outsideRange(outingDemand, MAX_OUTING_DEMAND)) {
                throw new IllegalArgumentException("추천 우선도의 항목별 점수가 배점 범위를 벗어났습니다.");
            }
        }

        private static boolean outsideRange(Integer score, int max) {
            return score == null || score < 0 || score > max;
        }
    }

    public record TagWeight(
            String code,
            Integer weight
    ) {
    }
}
