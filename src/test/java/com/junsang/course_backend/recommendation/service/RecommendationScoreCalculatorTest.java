package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.recommendation.dto.response.RecommendedCourseResponse.MatchType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoreCalculatorTest {

    @Test
    void 자동_생성된_코스_태그가_선택한_태그를_만족하면_완전_일치다() {
        var result = RecommendationScoreCalculator.calculate(
                new LinkedHashSet<>(Set.of("SHOPPING", "KOREAN_FOOD")),
                Map.of("SHOPPING", 80, "KOREAN_FOOD", 90),
                Set.of(CompanionType.LOVER),
                Set.of(TimeSlot.AFTERNOON),
                CompanionType.LOVER,
                TimeSlot.AFTERNOON,
                20
        );

        assertThat(result.matchType()).isEqualTo(MatchType.FULL_MATCH);
        assertThat(result.matchedTags()).containsExactlyInAnyOrder("SHOPPING", "KOREAN_FOOD");
        assertThat(result.totalScore()).isEqualTo(84.5);
    }

    @Test
    void 일부_태그만_일치하면_부분_일치다() {
        var result = RecommendationScoreCalculator.calculate(
                Set.of("SHOPPING", "KOREAN_FOOD"),
                Map.of("SHOPPING", 80),
                Set.of(),
                Set.of(),
                CompanionType.FRIEND,
                TimeSlot.NIGHT,
                0
        );

        assertThat(result.matchType()).isEqualTo(MatchType.PARTIAL_MATCH);
        assertThat(result.matchedTags()).containsExactly("SHOPPING");
        assertThat(result.unmatchedTags()).containsExactly("KOREAN_FOOD");
    }

    @Test
    void 태그가_맞지_않으면_인기_코스_fallback이다() {
        var result = RecommendationScoreCalculator.calculate(
                Set.of("SHOPPING"),
                Map.of(),
                Set.of(),
                Set.of(),
                CompanionType.FAMILY,
                TimeSlot.MORNING,
                100
        );

        assertThat(result.matchType()).isEqualTo(MatchType.POPULAR_FALLBACK);
        assertThat(result.breakdown().tagScore()).isZero();
        assertThat(result.breakdown().popularityScore()).isEqualTo(8.33);
    }
}
