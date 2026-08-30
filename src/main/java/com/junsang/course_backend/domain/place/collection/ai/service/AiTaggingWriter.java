package com.junsang.course_backend.domain.place.collection.ai.service;

import com.junsang.course_backend.domain.place.collection.ai.dto.AiTaggingResponse;
import com.junsang.course_backend.domain.place.collection.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.ai.dto.AiTaggingResultBatch.TagWeight;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.naver.service.NaverSearchUrlCreator;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 검증된 AI 결과를 Place와 PlaceTag로 원자적으로 저장한다.
@Service
@RequiredArgsConstructor
public class AiTaggingWriter {

    private static final int MIN_TAG_WEIGHT = 50;

    private final PlaceCollectionTempRepository tempRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;
    private final NaverSearchUrlCreator naverSearchUrlCreator;

    // AI 결과를 검증하고 Place를 upsert한 뒤 기존 태그를 교체한다.
    @Transactional
    public AiTaggingResponse complete(
            Long tempId,
            AiTaggingResult result,
            Map<String, Tag> activeTags
    ) {
        PlaceCollectionTemp temp = findTemp(tempId);
        PlaceType placeType = parsePlaceType(result.placeType());
        List<PlaceTagValue> tagValues = validateTags(result.tags(), activeTags);
        Place place = placeRepository.findByProviderAndProviderPlaceId(
                        temp.getProvider(),
                        temp.getProviderPlaceId()
                )
                .map(existing -> refresh(existing, temp, placeType))
                .orElseGet(() -> create(temp, placeType));
        placeRepository.save(place);

        placeTagRepository.deleteAllInBatch(placeTagRepository.findByPlaceId(place.getId()));
        placeTagRepository.saveAll(tagValues.stream()
                .map(value -> PlaceTag.create(place, value.tag(), value.weight()))
                .toList());

        temp.completeAiTagging();
        tempRepository.save(temp);
        return AiTaggingResponse.completed(temp, place.getId());
    }

    // 처리 중 실패한 Temp에 오류를 기록한다.
    @Transactional
    public AiTaggingResponse fail(
            Long tempId,
            PlaceRefinementErrorCode errorCode,
            String errorMessage
    ) {
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.fail(errorCode, errorMessage);
        tempRepository.save(temp);
        return AiTaggingResponse.failed(temp);
    }

    // AI가 반환한 PlaceType을 서비스 enum으로 검증한다.
    private PlaceType parsePlaceType(String value) {
        try {
            return PlaceType.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("AI가 올바르지 않은 PlaceType을 반환했습니다.");
        }
    }

    // 활성 태그와 가중치 범위를 검증하고 기준 점수 이상의 태그만 남긴다.
    private List<PlaceTagValue> validateTags(
            List<TagWeight> results,
            Map<String, Tag> activeTags
    ) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("AI 태그 결과가 비어 있습니다.");
        }
        Set<String> seenCodes = new HashSet<>();
        List<PlaceTagValue> values = results.stream()
                .map(result -> {
                    if (result.code() == null || !seenCodes.add(result.code())) {
                        throw new IllegalArgumentException("AI 태그 코드가 비어 있거나 중복됐습니다.");
                    }
                    Tag tag = activeTags.get(result.code());
                    if (tag == null) {
                        throw new IllegalArgumentException("등록되지 않은 AI 태그입니다: " + result.code());
                    }
                    if (result.weight() == null || result.weight() < 0 || result.weight() > 100) {
                        throw new IllegalArgumentException("AI 태그 가중치가 올바르지 않습니다.");
                    }
                    return new PlaceTagValue(tag, result.weight());
                })
                .filter(result -> result.weight() >= MIN_TAG_WEIGHT)
                .toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException("기준 점수 이상의 AI 태그가 없습니다.");
        }
        return values;
    }

    // 기존 Place를 네이버 우선 정보와 AI 분류 결과로 갱신한다.
    private Place refresh(Place place, PlaceCollectionTemp temp, PlaceType placeType) {
        place.refresh(
                temp.getArea(),
                placeType,
                firstNonBlank(temp.getNaverTitle(), temp.getName()),
                firstNonBlank(temp.getNaverAddressName(), temp.getAddressName()),
                firstNonBlank(temp.getNaverRoadAddressName(), temp.getRoadAddressName()),
                firstNonBlank(temp.getNaverCategoryName(), temp.getKakaoCategoryName()),
                temp.getKakaoCategoryGroupCode(),
                temp.getLatitude(),
                temp.getLongitude(),
                temp.getKakaoPlaceUrl(),
                createNaverSearchUrl(temp),
                temp.getPhone()
        );
        return place;
    }

    // Temp와 AI 결과로 최종 Place를 생성한다.
    private Place create(PlaceCollectionTemp temp, PlaceType placeType) {
        return Place.create(
                temp.getProvider(),
                temp.getProviderPlaceId(),
                temp.getArea(),
                placeType,
                firstNonBlank(temp.getNaverTitle(), temp.getName()),
                firstNonBlank(temp.getNaverAddressName(), temp.getAddressName()),
                firstNonBlank(temp.getNaverRoadAddressName(), temp.getRoadAddressName()),
                firstNonBlank(temp.getNaverCategoryName(), temp.getKakaoCategoryName()),
                temp.getKakaoCategoryGroupCode(),
                temp.getLatitude(),
                temp.getLongitude(),
                temp.getKakaoPlaceUrl(),
                createNaverSearchUrl(temp),
                temp.getPhone(),
                false,
                null,
                null
        );
    }

    // 첫 번째 값이 비어 있으면 카카오 원본 값을 사용한다.
    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    // 기존 Temp 데이터도 최종 Place 저장 시 최신 형식의 네이버 검색 링크로 보정한다.
    private String createNaverSearchUrl(PlaceCollectionTemp temp) {
        return naverSearchUrlCreator.create(temp.getName(), temp.getAddressName());
    }

    // Temp를 조회하고 없으면 입력 오류로 처리한다.
    private PlaceCollectionTemp findTemp(Long tempId) {
        return tempRepository.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("AI 태깅할 임시 장소를 찾을 수 없습니다: " + tempId));
    }

    private record PlaceTagValue(Tag tag, int weight) {
    }
}
