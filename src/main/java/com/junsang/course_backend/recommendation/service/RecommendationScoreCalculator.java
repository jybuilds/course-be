package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.recommendation.dto.response.RecommendedCourseResponse.MatchType;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// 태그 관련성, 상황 적합도, 인기도를 100점으로 계산한다.
final class RecommendationScoreCalculator {
    private static final double TAG_MAX = 70.0;
    private static final double COMPANION_MAX = 10.0;
    private static final double TIME_SLOT_MAX = 10.0;
    private static final double POPULARITY_MAX = 10.0;
    private static final double UNKNOWN_CONTEXT_SCORE = 5.0;
    private static final int MATCH_WEIGHT_THRESHOLD = 50;
    private static final double POPULARITY_HALF_SCORE_COUNT = 20.0;

    private RecommendationScoreCalculator() {
    }

    // 코스 장소에서 미리 계산한 태그 가중치를 사용자 선택과 비교한다.
    static Result calculate(
            Set<String> requestedTags,
            Map<String, Integer> courseTagWeights,
            Set<CompanionType> companionTypes,
            Set<TimeSlot> timeSlots,
            CompanionType requestedCompanionType,
            TimeSlot requestedTimeSlot,
            long selectionCount
    ) {
        Set<String> matchedTags = new LinkedHashSet<>();
        Set<String> unmatchedTags = new LinkedHashSet<>();
        double relevanceSum = 0;

        for (String code : requestedTags) {
            int evidence = courseTagWeights.getOrDefault(code, 0);
            relevanceSum += Math.clamp(evidence, 0, 100) / 100.0;
            if (evidence >= MATCH_WEIGHT_THRESHOLD) matchedTags.add(code);
            else unmatchedTags.add(code);
        }

        double tagScore = relevanceSum / requestedTags.size() * TAG_MAX;
        double companionScore = contextScore(companionTypes, requestedCompanionType, COMPANION_MAX);
        double timeSlotScore = contextScore(timeSlots, requestedTimeSlot, TIME_SLOT_MAX);
        // 20회 선택에서 인기 점수의 절반을 주고 이후 증가 폭을 완만하게 만든다.
        double popularityScore = selectionCount / (selectionCount + POPULARITY_HALF_SCORE_COUNT) * POPULARITY_MAX;
        double total = tagScore + companionScore + timeSlotScore + popularityScore;

        MatchType matchType = matchedTags.size() == requestedTags.size()
                ? MatchType.FULL_MATCH
                : matchedTags.isEmpty() ? MatchType.POPULAR_FALLBACK : MatchType.PARTIAL_MATCH;
        return new Result(
                round(total), matchType, matchedTags, unmatchedTags,
                new Breakdown(round(tagScore), round(companionScore), round(timeSlotScore), round(popularityScore))
        );
    }

    private static <T> double contextScore(Set<T> supportedValues, T requestedValue, double maxScore) {
        if (supportedValues.isEmpty()) return UNKNOWN_CONTEXT_SCORE;
        return supportedValues.contains(requestedValue) ? maxScore : 0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record Result(
            double totalScore,
            MatchType matchType,
            Set<String> matchedTags,
            Set<String> unmatchedTags,
            Breakdown breakdown
    ) {
    }

    record Breakdown(double tagScore, double companionScore, double timeSlotScore, double popularityScore) {
    }
}
