package com.junsang.course_backend.domain.course.creation.service;

import com.junsang.course_backend.domain.course.creation.dto.response.CourseCandidateResponse;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import com.junsang.course_backend.domain.place.service.PlaceDistanceCalculator;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// 직전 장소를 기준으로 다음 일정에 넣을 장소 후보를 찾는다.
@Service
@RequiredArgsConstructor
public class CourseCandidateService {
    // 단건 장소 검색과 같은 반경을 사용해 직접 코스 생성 흐름의 거리 감각을 맞춘다.
    static final int SEARCH_RADIUS_METERS = 3_000;
    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 30;

    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;

    /// 앵커가 속한 Area 안에서 직전 장소와 가까운 후보를 점수순으로 돌려준다.
    @Transactional(readOnly = true)
    public List<CourseCandidateResponse> findCandidates(
            Long anchorPlaceId,
            CourseItemRole role,
            Long fromPlaceId,
            List<Long> excludePlaceIds,
            List<String> tagCodes,
            Integer size
    ) {
        Place anchor = findActivePlace(anchorPlaceId, ErrorCode.COURSE_ANCHOR_NOT_FOUND);
        // 직전 장소를 주지 않으면 첫 일정이므로 앵커를 기준점으로 삼는다.
        Place origin = fromPlaceId == null
                ? anchor
                : findActivePlace(fromPlaceId, ErrorCode.COURSE_PLACE_NOT_FOUND);

        Set<Long> excluded = new HashSet<>();
        excluded.add(anchor.getId());
        excluded.add(origin.getId());
        if (excludePlaceIds != null) excluded.addAll(excludePlaceIds);

        List<Place> slotPlaces = placeRepository
                .findByAreaIdAndPlaceTypeAndIsActiveTrue(anchor.getArea().getId(), toPlaceType(role));
        List<Candidate> candidates = slotPlaces.stream()
                .filter(place -> !excluded.contains(place.getId()))
                .map(place -> new Candidate(place, PlaceDistanceCalculator.between(origin, place)))
                .filter(candidate -> candidate.distanceMeters() <= SEARCH_RADIUS_METERS)
                .toList();
        // 후보가 없으면 프론트가 해당 단계를 건너뛸 수 있도록 빈 목록을 그대로 돌려준다.
        if (candidates.isEmpty()) return List.of();

        Map<Long, Map<String, Integer>> tagWeights = loadTagWeights(slotPlaces);
        Set<String> scoredTagCodes = scoredTagCodes(tagCodes, tagWeights);

        return candidates.stream()
                .map(candidate -> toResponse(candidate, scoredTagCodes, tagWeights))
                .sorted(Comparator.comparingDouble(CourseCandidateResponse::score).reversed()
                        .thenComparingInt(CourseCandidateResponse::distanceMeters)
                        .thenComparing(CourseCandidateResponse::placeId))
                .limit(resolveSize(size))
                .toList();
    }

    // 요청 태그 중 같은 Area·유형의 장소가 한 곳이라도 가진 태그만 채점한다.
    // 카페 칸에서 '전시'처럼 카페가 갖지 않는 태그까지 평균에 넣으면 잘 맞는 카페의 점수가 희석된다.
    private Set<String> scoredTagCodes(List<String> tagCodes, Map<Long, Map<String, Integer>> tagWeights) {
        if (tagCodes == null || tagCodes.isEmpty()) return Set.of();
        Set<String> presentTagCodes = tagWeights.values().stream()
                .flatMap(weights -> weights.keySet().stream())
                .collect(Collectors.toSet());
        return tagCodes.stream()
                .filter(presentTagCodes::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // 후보 하나를 점수와 매칭 근거가 붙은 응답으로 변환한다.
    private CourseCandidateResponse toResponse(
            Candidate candidate,
            Set<String> scoredTagCodes,
            Map<Long, Map<String, Integer>> tagWeights
    ) {
        CandidateScoreCalculator.Result result = CandidateScoreCalculator.calculate(
                scoredTagCodes,
                tagWeights.getOrDefault(candidate.place().getId(), Map.of()),
                candidate.distanceMeters()
        );
        return CourseCandidateResponse.of(
                candidate.place(),
                candidate.distanceMeters(),
                result.totalScore(),
                result.matchedTags(),
                result.unmatchedTags(),
                new CourseCandidateResponse.ScoreBreakdown(
                        result.breakdown().tagScore(),
                        result.breakdown().proximityScore()
                )
        );
    }

    // 장소들의 태그를 한 번에 조회해 장소마다 태그 코드별 가중치로 정리한다.
    private Map<Long, Map<String, Integer>> loadTagWeights(List<Place> places) {
        Set<Long> placeIds = places.stream()
                .map(Place::getId)
                .collect(Collectors.toSet());
        return placeTagRepository.findWithTagByPlaceIdIn(placeIds).stream()
                .filter(placeTag -> placeTag.getTag().isActive())
                .collect(Collectors.groupingBy(
                        placeTag -> placeTag.getPlace().getId(),
                        Collectors.toMap(
                                placeTag -> placeTag.getTag().getCode(),
                                PlaceTag::getWeight,
                                Integer::max
                        )
                ));
    }

    private Place findActivePlace(Long placeId, ErrorCode notFoundCode) {
        return placeRepository.findById(placeId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(notFoundCode));
    }

    // 일정 유형과 장소 유형은 같은 값을 쓰므로 이름으로 변환한다.
    private PlaceType toPlaceType(CourseItemRole role) {
        return PlaceType.valueOf(role.name());
    }

    private int resolveSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        return Math.clamp(size, 1, MAX_SIZE);
    }

    private record Candidate(Place place, double distanceMeters) {
    }
}
