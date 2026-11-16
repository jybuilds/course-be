package com.junsang.course_backend.domain.course.creation.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// 사용자 태그 적합도와 직전 장소와의 거리를 100점으로 계산한다.
final class CandidateScoreCalculator {
    // 두 배점의 합이 총점 100이다.
    private static final double TAG_MAX = 60.0;           // 태그 적합도 배점
    private static final double PROXIMITY_MAX = 40.0;     // 근접도 배점

    // 태그가 아직 부여되지 않은 장소를 과도하게 불이익 주지 않기 위한 중립 점수다.
    private static final double UNKNOWN_TAG_SCORE = TAG_MAX / 2;

    // 근접도 점수가 절반이 되는 거리. 도보 약 12분이다.
    private static final double HALF_SCORE_DISTANCE_METERS = 800.0;

    private static final int MATCH_WEIGHT_THRESHOLD = 50; // 일치 태그로 표시할 최소 가중치
    private static final int MAX_TAG_WEIGHT = 100;        // PlaceTag.weight 최댓값

    private CandidateScoreCalculator() {
    }

    // 태그 적합도는 채점할 태그별 PlaceTag 가중치의 평균으로 환산한다.
    static Result calculate(
            Set<String> scoredTagCodes,
            Map<String, Integer> placeTagWeights,
            double distanceMeters
    ) {
        // 가까운 곳끼리는 차이를 작게 두고 절반 거리를 넘으면 빠르게 줄어들도록 거리의 제곱을 쓴다.
        // 0m 40점, 300m 35.1점, 800m 20점, 1.5km 8.9점, 3km 2.7점
        double squaredHalf = HALF_SCORE_DISTANCE_METERS * HALF_SCORE_DISTANCE_METERS;
        double proximityScore = squaredHalf / (squaredHalf + distanceMeters * distanceMeters) * PROXIMITY_MAX;

        // 채점할 태그가 없으면 모든 후보가 같은 0점이 되어 거리만으로 순위가 결정된다.
        if (scoredTagCodes.isEmpty()) {
            return result(0, proximityScore, Set.of(), Set.of());
        }
        // 태그가 하나도 없는 장소는 판단 근거가 없는 것이지 불일치가 아니므로 중립 점수를 준다.
        // 수집 직후 AI 태깅을 기다리는 장소가 영구히 밀리는 것을 막는다.
        if (placeTagWeights.isEmpty()) {
            return result(UNKNOWN_TAG_SCORE, proximityScore, Set.of(), Set.of());
        }

        Set<String> matchedTags = new LinkedHashSet<>();
        Set<String> unmatchedTags = new LinkedHashSet<>();
        double relevanceSum = 0;
        for (String code : scoredTagCodes) {
            int weight = placeTagWeights.getOrDefault(code, 0);
            relevanceSum += Math.clamp(weight, 0, MAX_TAG_WEIGHT) / (double) MAX_TAG_WEIGHT;
            if (weight >= MATCH_WEIGHT_THRESHOLD) matchedTags.add(code);
            else unmatchedTags.add(code);
        }
        double tagScore = relevanceSum / scoredTagCodes.size() * TAG_MAX;

        return result(tagScore, proximityScore, matchedTags, unmatchedTags);
    }

    private static Result result(
            double tagScore,
            double proximityScore,
            Set<String> matchedTags,
            Set<String> unmatchedTags
    ) {
        return new Result(
                round(tagScore + proximityScore),
                matchedTags,
                unmatchedTags,
                new Breakdown(round(tagScore), round(proximityScore))
        );
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record Result(
            double totalScore,
            Set<String> matchedTags,
            Set<String> unmatchedTags,
            Breakdown breakdown
    ) {
    }

    record Breakdown(double tagScore, double proximityScore) {
    }
}
