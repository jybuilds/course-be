package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.AreaTag;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.AreaTagRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.recommendation.calculator.AreaRecommendationCalculator;
import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.recommendationAreas.RecommendationAreasResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationAreas.RecommendationAreasResponse.RecommendedVisitPlace;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.CompanionTypeOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.CityOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.TagResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.TimeSlotOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.SelectionRulesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// 추천 입력 화면에 필요한 선택지를 조합한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int CANDIDATE_LIMIT = 10;

    private static final Set<String> AREA_RECOMMENDATION_TAG_CODES = Set.of(
            "CAFE",
            "SHOPPING",
            "MARKET",
            "POPUP",
            "EXHIBITION",
            "WALK",
            "PARK",
            "PERFORMANCE",
            "BOOKSTORE",
            "WORKSHOP",
            "TRENDY",
            "LIVELY",
            "SCENIC_VIEW"
    );

    private final CityRepository cityRepository;
    private final TagRepository tagRepository;
    private final AreaTagRepository areaTagRepository;
    private final AreaRepository areaRepository;
    private final AreaRecommendationCalculator areaRecommendationCalculator;

    // 도시, 태그, 시간대 선택지를 반환한다.
    public RecommendationOptionsResponse getOptions() {
        return new RecommendationOptionsResponse(
                cityRepository.findAll().stream().map(CityOptionResponse::from).toList(),
                java.util.Arrays.stream(CompanionType.values()).map(CompanionTypeOptionResponse::from).toList(),
                java.util.Arrays.stream(TimeSlot.values()).map(TimeSlotOptionResponse::from).toList(),
                tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(TagResponse::from)
                        .toList(),
                SelectionRulesResponse.defaults()
        );
    }

    // 사용자 입력 기반 Area 추천을 반환한다.
    public RecommendationAreasResponse recommendationAreas(RecommendationRequest request){
        List<String> selectedTags = request.tagCodes().stream()
                .filter(AREA_RECOMMENDATION_TAG_CODES::contains)
                .toList();

        /// 사용자 선택 없는 경우
        if (selectedTags.isEmpty()) {
            // 태그 미선택 시 해당 도시의 Area 순위 상위 10개와 각 Area의 모든 태그를 반환한다.
            List<Area> rankedAreas = listByAreaRank(request.cityId());
            if (rankedAreas.isEmpty()) {
                return new RecommendationAreasResponse(List.of());
            }

            List<Long> areaIds = rankedAreas.stream()
                    .map(Area::getId)
                    .toList();
            return toAreaResponse(rankedAreas, groupByArea(areaTagRepository.findAllByAreaIds(areaIds)));
        }

        /// 사용자 태그 1개 고른경우
        if (selectedTags.size() == 1) {
            // 해당 태그의 랭킹 상위 10개 AreaTag를 각각 응답 Area로 변환한다.
            List<AreaTag> rankedTags = listByTagRank(request.cityId(), selectedTags.getFirst());
            return toAreaResponse(rankedTags.stream().map(AreaTag::getArea).toList(), groupByArea(rankedTags));
        }

        ///  사용자 태그 2개 이상 고른 경우
        // 해당지역의 해당태그 전체를 한 번에 조회하고 Calculator로 DB 접근 없이 후보 순서를 결정
        List<AreaTag> areaTags = areaTagRepository.findRankedByCityAndTagCodes(
                request.cityId(), selectedTags);
        List<Long> recommendedIds = areaRecommendationCalculator.recommend(
                areaTags, selectedTags, CANDIDATE_LIMIT);

        Map<Long, List<AreaTag>> tagsByArea = groupByArea(areaTags);
        List<Area> recommendations = new ArrayList<>(recommendedIds.stream()
                .map(id -> tagsByArea.get(id).getFirst().getArea())
                .toList());

        // 태그 기반 후보가 부족할 때 도시 순위로 보충한다.
        if (recommendations.size() < CANDIDATE_LIMIT) {
            Set<Long> included = Set.copyOf(recommendedIds);
            List<Area> fallbackAreas = listByAreaRank(request.cityId()).stream()
                    .filter(area -> !included.contains(area.getId()))
                    .limit(CANDIDATE_LIMIT - recommendations.size())
                    .toList();
            if (!fallbackAreas.isEmpty()) {
                tagsByArea.putAll(groupByArea(areaTagRepository.findAllForAreasAndTags(
                        fallbackAreas.stream().map(Area::getId).toList(), selectedTags)));
                recommendations.addAll(fallbackAreas);
            }
        }

        return toAreaResponse(recommendations, tagsByArea);
    }

    private Map<Long, List<AreaTag>> groupByArea(List<AreaTag> tags) {
        return tags.stream().collect(Collectors.groupingBy(tag -> tag.getArea().getId()));
    }

    private RecommendationAreasResponse toAreaResponse(List<Area> areas, Map<Long, List<AreaTag>> tagsByArea) {
        Set<Long> areaTagIds = areas.stream()
                .flatMap(area -> tagsByArea.getOrDefault(area.getId(), List.of()).stream())
                .map(AreaTag::getId)
                .collect(Collectors.toSet());
        Map<Long, List<RecommendedVisitPlace>> anchorsByAreaTag = areaTagIds.isEmpty() ? Map.of()
                : areaTagRepository.findRecommendedAnchors(areaTagIds).stream()
                        .collect(Collectors.groupingBy(AreaTagRepository.RecommendedAnchor::getAreaTagId,
                                Collectors.mapping(anchor -> new RecommendedVisitPlace(
                                        anchor.getPlaceId(), anchor.getName()), Collectors.toList())));
        return new RecommendationAreasResponse(areas.stream()
                .map(area -> new RecommendationAreasResponse.areaResponse(
                        area.getId(), area.getName(),
                        tagsByArea.getOrDefault(area.getId(), List.of()).stream()
                                .map(tag -> toAreaTagResponse(tag, anchorsByAreaTag.getOrDefault(tag.getId(), List.of())))
                                .toList()))
                .toList());
    }

    // 지정한 태그의 랭킹 순으로 최대 10개 AreaTag를 조회
    private List<AreaTag> listByTagRank(Long cityId, String tagCode) {
        return areaTagRepository.findRankedAreaTagsByTagCode(
                tagCode,
                cityId,
                PageRequest.of(0, CANDIDATE_LIMIT)
        );
    }

    // 도시별 Area 순위 상위 후보를 조회한다.
    private List<Area> listByAreaRank(Long cityId) {
        return areaRepository.findByCity_IdAndRankIsNotNullOrderByRankAscIdAsc(
                cityId,
                PageRequest.of(0, CANDIDATE_LIMIT)
        );
    }

    private RecommendationAreasResponse.AreaTagResponse toAreaTagResponse(
            AreaTag areaTag, List<RecommendedVisitPlace> anchors
    ) {
        return new RecommendationAreasResponse.AreaTagResponse(
                areaTag.getTag().getDisplayName(),
                anchors,
                areaTag.getAttractionFeatures(),
                areaTag.getDifferentiators()
        );
    }
}
