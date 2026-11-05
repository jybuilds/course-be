package com.junsang.course_backend.recommendation.calculator;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.AreaTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** DB 접근 없이 도시 내 태그별 전체 순위를 결합해 추천 지역을 선정한다. */
@Component
public class AreaRecommendationCalculator {

    private static final double ADEQUATE_SCORE = 50;
    private static final double QUALITY_THRESHOLD = 75;

    public List<Long> recommend(List<AreaTag> areaTags, List<String> selectedTags, int limit) {
        if (limit < 1 || selectedTags.isEmpty()) {
            return List.of();
        }
        List<String> tags = selectedTags.stream().distinct().toList();
        Set<String> selected = Set.copyOf(tags);

        // 모든 지역들의 AreaTag에서 사용자가 고른 태그만 남긴다.
        List<AreaTag> rankedTags = areaTags.stream()
                .filter(at -> selected.contains(at.getTag().getCode()))
                .filter(at -> at.getRecommendationRank() != null && at.getRecommendationRank() > 0)
                .toList();

        // 태그별 전체 지역 수를 알아야 순위를 점수로 바꿀 수 있어 전체 순위 데이터를 조회한다
        // 해당 태그를 가지고 있는 지역 수 (점수변환에 사용)
        Map<String, Long> counts = rankedTags.stream().collect(Collectors.groupingBy(
                at -> at.getTag().getCode(), Collectors.counting()));
        // ex)성수 ID -> 성수의 카페·쇼핑 AreaTag (각 지역 한 번씩 평가할때 사용)
        Map<Long, List<AreaTag>> byArea = rankedTags.stream().collect(Collectors.groupingBy(
                at -> at.getArea().getId()));

        // 지역마다 선택 태그의 순위를 점수로 바꿈
        List<Candidate> candidates = new ArrayList<>();
        for (List<AreaTag> rows : byArea.values()) {
            Area area = rows.getFirst().getArea();
            Map<String, Double> scores = new HashMap<>();
            for (AreaTag row : rows) {
                long count = counts.get(row.getTag().getCode());
                double score = count == 1 ? 100 :
                        100.0 * (count - row.getRecommendationRank()) / (count - 1);
                scores.put(row.getTag().getCode(), Math.clamp(score, 0, 100));
            }
            double[] values = tags.stream()
                                .mapToDouble(tag -> scores.getOrDefault(tag, 0.0))
                                .toArray();
            candidates.add(new Candidate(area.getId(), area.getRank(), evaluate(values)));
        }


        // 단계별 계산값은 서로 비교하지 않고 종합 후보, 대안, 최후 보충 순서로 선택한다.
        return candidates.stream()
                .sorted(Comparator.comparing((Candidate c) -> c.score().stage())
                        .thenComparing(Comparator.comparingDouble((Candidate c) -> c.score().value()).reversed())
                        .thenComparing(Candidate::areaRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Candidate::areaId))
                .limit(limit)
                .map(Candidate::areaId)
                .toList();
    }

    Score evaluate(double[] values) {
        double sum = 0;
        double squares = 0;
        double reciprocal = 0;
        double min = 100;
        double max = 0;
        for (double value : values) {
            sum += value;
            squares += value * value;
            if (value > 0) reciprocal += 1 / value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double mean = sum / values.length;
        // 모든 태그가 충분한 수준일 때만 낮은 점수에 민감한 조화평균으로 균형을 평가한다.
        if (min >= ADEQUATE_SCORE) {
            double harmonic = values.length / reciprocal;
            if (harmonic >= QUALITY_THRESHOLD) return new Score(Stage.BALANCED, harmonic);
        }
        if (mean >= QUALITY_THRESHOLD) return new Score(Stage.BROAD, mean);

        double rms = Math.sqrt(squares / values.length);
        if (max >= QUALITY_THRESHOLD) return new Score(Stage.ALTERNATIVE, rms);
        return new Score(Stage.FILLER, rms);
    }

    // 선언 순서가 후보 단계의 우선순위다.
    //BALANCED      : 모든 점수 ≥ 50, 조화평균 ≥ 75	    조화평균
    //BROAD         : 앞 단계는 아니고, 평균 ≥ 75	    산술평균
    //ALTERNATIVE   : 앞 단계는 아니고, 최고 점수 ≥ 75	제곱평균제곱근
    //FILLER        : 나머지	                        제곱평균제곱근
    enum Stage { BALANCED, BROAD, ALTERNATIVE, FILLER }
    record Score(Stage stage, double value) {}
    private record Candidate(Long areaId, Integer areaRank, Score score) {}
}
