package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.AreaTag;
import com.junsang.course_backend.domain.place.entity.AreaTier;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.AreaTagRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.domain.stats.entity.AreaStats;
import com.junsang.course_backend.domain.stats.repository.AreaStatsRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.RecommendationAreaResponse;
import com.junsang.course_backend.recommendation.dto.response.CompanionTypeOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.CityOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.dto.response.TagResponse;
import com.junsang.course_backend.recommendation.dto.response.TimeSlotOptionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 추천 입력 화면에 필요한 선택지를 조합한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int RECOMMENDATION_LIMIT = 5;
    private static final int PRIMARY_RECOMMENDATION_LIMIT = RECOMMENDATION_LIMIT - 1;
    private static final int EXPLORATION_MINIMUM_TAG_WEIGHT = 50;
    private static final double TAG_SCORE_MAX = 75.0;
    private static final double POPULARITY_SCORE_MAX = 25.0;
    private static final double POPULARITY_HALF_SCORE_COUNT = 20.0;

    private final CityRepository cityRepository;
    private final TagRepository tagRepository;
    private final AreaRepository areaRepository;
    private final AreaTagRepository areaTagRepository;
    private final AreaStatsRepository areaStatsRepository;
    private final AreaRecommendationStatsWriter areaRecommendationStatsWriter;

    // 도시, 태그, 시간대 선택지를 반환한다.
    public RecommendationOptionsResponse getOptions() {
        return new RecommendationOptionsResponse(
                cityRepository.findAll().stream().map(CityOptionResponse::from).toList(),
                java.util.Arrays.stream(CompanionType.values()).map(CompanionTypeOptionResponse::from).toList(),
                java.util.Arrays.stream(TimeSlot.values()).map(TimeSlotOptionResponse::from).toList(),
                tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(TagResponse::from)
                        .toList(),
                com.junsang.course_backend.recommendation.dto.response.SelectionRulesResponse.defaults()
        );
    }

    // 선택 태그, 인기도, 운영 Tier를 기준으로 Area를 추천한다.
    public RecommendationAreaResponse recommendAreas(RecommendationRequest request) {
        Set<String> requestedTagCodes = Set.copyOf(request.tagCodes());
        validateRequestedTags(requestedTagCodes);

        List<Area> areas = areaRepository.findByCityIdOrderById(request.cityId());
        if (areas.isEmpty()) return new RecommendationAreaResponse(List.of());

        Set<Long> areaIds = areas.stream().map(Area::getId).collect(Collectors.toSet());
        Map<Long, Map<String, Integer>> tagWeightsByAreaId = groupTagWeightsByArea(
                areaTagRepository.findWithTagByAreaIdIn(areaIds)
        );
        Map<Long, AreaStats> statsByAreaId = areaStatsRepository.findByAreaIdIn(areaIds).stream()
                .collect(Collectors.toMap(stats -> stats.getArea().getId(), Function.identity()));

        List<AreaScore> scores = areas.stream()
                .map(area -> calculateAreaScore(area, requestedTagCodes, tagWeightsByAreaId, statsByAreaId))
                .sorted(Comparator.comparingDouble(AreaScore::totalScore).reversed()
                        .thenComparing(score -> score.area().getId()))
                .toList();
        List<AreaScore> recommended = selectRecommendations(scores);

        areaRecommendationStatsWriter.recordImpressions(
                recommended.stream().map(AreaScore::area).toList()
        );

        return new RecommendationAreaResponse(recommended.stream()
                .map(score -> new RecommendationAreaResponse.AreaResponse(
                        score.area().getId(),
                        score.area().getName(),
                        score.area().getRecommendationTier().name(),
                        round(score.totalScore())
                ))
                .toList());
    }

    // 요청한 태그가 현재 사용 가능한 선택지인지 검증한다.
    private void validateRequestedTags(Set<String> requestedTagCodes) {
        if (tagRepository.countByCodeInAndIsActiveTrue(requestedTagCodes) != requestedTagCodes.size()) {
            throw new BusinessException(ErrorCode.PREFERENCE_OPTION_NOT_FOUND);
        }
    }

    // Area별 태그 가중치를 메모리에서 바로 점수 계산할 수 있게 묶는다.
    private Map<Long, Map<String, Integer>> groupTagWeightsByArea(List<AreaTag> areaTags) {
        return areaTags.stream().collect(Collectors.groupingBy(
                areaTag -> areaTag.getArea().getId(),
                Collectors.toMap(areaTag -> areaTag.getTag().getCode(), AreaTag::getWeight)
        ));
    }

    // 태그 적합도 75점, 선택 수 기반 인기도 25점, Tier 보정 점수를 계산한다.
    private AreaScore calculateAreaScore(
            Area area,
            Set<String> requestedTagCodes,
            Map<Long, Map<String, Integer>> tagWeightsByAreaId,
            Map<Long, AreaStats> statsByAreaId
    ) {
        Map<String, Integer> tagWeights = tagWeightsByAreaId.getOrDefault(area.getId(), Map.of());
        double tagWeightSum = requestedTagCodes.stream()
                .mapToInt(code -> Math.clamp(tagWeights.getOrDefault(code, 0), 0, 100))
                .sum();
        double tagRelevance = tagWeightSum / requestedTagCodes.size();
        double tagScore = tagRelevance / 100.0 * TAG_SCORE_MAX;

        AreaStats stats = statsByAreaId.get(area.getId());
        long selectionCount = stats == null ? 0 : stats.getSelectionCount();
        long impressionCount = stats == null ? 0 : stats.getImpressionCount();
        double popularityScore = selectionCount / (selectionCount + POPULARITY_HALF_SCORE_COUNT)
                * POPULARITY_SCORE_MAX;
        double totalScore = tagScore + popularityScore + tierBonus(area.getRecommendationTier());

        return new AreaScore(area, tagRelevance, impressionCount, totalScore);
    }

    // 운영자가 지정한 Tier는 사용자 태그 적합도를 넘지 않는 작은 보정으로 사용한다.
    private double tierBonus(AreaTier tier) {
        return switch (tier) {
            case S -> 4.0;
            case A -> 2.0;
            case B -> 1.0;
            case C -> 0.0;
        };
    }

    // 상위 4개와 저노출·고적합 Area 1개를 결합해 탐색 기회를 남긴다.
    private List<AreaScore> selectRecommendations(List<AreaScore> scores) {
        List<AreaScore> selected = new ArrayList<>(scores.subList(
                0,
                Math.min(PRIMARY_RECOMMENDATION_LIMIT, scores.size())
        ));
        Set<Long> selectedAreaIds = selected.stream()
                .map(score -> score.area().getId())
                .collect(Collectors.toCollection(HashSet::new));

        findExplorationCandidate(scores, selectedAreaIds).ifPresent(selected::add);
        return selected;
    }

    // 충분한 태그 적합도를 가진 후보 중 가장 적게 노출된 Area를 하나 선택한다.
    private Optional<AreaScore> findExplorationCandidate(List<AreaScore> scores, Set<Long> selectedAreaIds) {
        List<AreaScore> remaining = scores.stream()
                .filter(score -> !selectedAreaIds.contains(score.area().getId()))
                .toList();
        List<AreaScore> eligible = remaining.stream()
                .filter(score -> score.tagRelevance() >= EXPLORATION_MINIMUM_TAG_WEIGHT)
                .toList();
        List<AreaScore> candidates = eligible.isEmpty() ? remaining : eligible;

        return candidates.stream()
                .min(Comparator.comparingLong(AreaScore::impressionCount)
                        .thenComparing(Comparator.comparingDouble(AreaScore::totalScore).reversed())
                        .thenComparing(score -> score.area().getId()));
    }

    private double round(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private record AreaScore(Area area, double tagRelevance, long impressionCount, double totalScore) {
    }
}
