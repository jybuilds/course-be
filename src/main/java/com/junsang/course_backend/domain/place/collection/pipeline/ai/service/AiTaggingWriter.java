package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.TagWeight;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.NaverSearchUrlCreator;
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
        // 최신 상태의 Temp를 다시 조회해 외부 요청 이후 변경된 값을 기준으로 저장한다.
        PlaceCollectionTemp temp = findTemp(tempId);
        PlaceType placeType = temp.getDefaultPlaceType();

        // AI 결과의 태그 수·중복·활성 여부·가중치를 저장 전에 검증한다.
        List<PlaceTagValue> tagValues = validateTags(result.tags(), activeTags);

        // 같은 공급자 장소는 새 행을 만들지 않고 네이버 보강 정보로 갱신한다.
        Place place = placeRepository.findByProviderAndProviderPlaceId(
                        temp.getProvider(),
                        temp.getProviderPlaceId()
                )
                .map(existing -> refresh(existing, temp, placeType))
                .orElseGet(() -> create(temp, placeType));
        placeRepository.save(place);

        // 재태깅 시 과거 태그를 남기지 않고 이번 AI 결과로 완전히 교체한다.
        placeTagRepository.deleteAllInBatch(placeTagRepository.findByPlaceId(place.getId()));
        placeTagRepository.saveAll(tagValues.stream()
                .map(value -> PlaceTag.create(place, value.tag(), value.weight()))
                .toList());

        // Place와 태그 저장이 끝난 뒤에만 Temp를 완료 상태로 변경한다.
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
        // 유효하지 않은 AI 출력도 Temp에 남겨 재시도·운영 확인이 가능하도록 한다.
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.fail(errorCode, errorMessage);
        tempRepository.save(temp);
        return AiTaggingResponse.failed(temp);
    }

    // 활성 태그와 공통 태깅 정책을 검증한다.
    private List<PlaceTagValue> validateTags(
            List<TagWeight> results,
            Map<String, Tag> activeTags
    ) {
        if (results == null
                || results.size() < AiTaggingPolicy.MIN_TAG_COUNT
                || results.size() > AiTaggingPolicy.MAX_TAG_COUNT) {
            throw new IllegalArgumentException("AI 태그 개수가 올바르지 않습니다.");
        }
        Set<String> seenCodes = new HashSet<>();
        List<PlaceTagValue> values = results.stream()
                .map(result -> {
                    // 동일 태그가 여러 번 오면 가중치 의미가 모호하므로 거부한다.
                    if (result.code() == null || !seenCodes.add(result.code())) {
                        throw new IllegalArgumentException("AI 태그 코드가 비어 있거나 중복됐습니다.");
                    }
                    Tag tag = activeTags.get(result.code());
                    if (tag == null) {
                        throw new IllegalArgumentException("등록되지 않은 AI 태그입니다: " + result.code());
                    }
                    if (result.weight() == null
                            || result.weight() < 0
                            || result.weight() > AiTaggingPolicy.MAX_TAG_WEIGHT) {
                        throw new IllegalArgumentException("AI 태그 가중치가 올바르지 않습니다.");
                    }
                    return new PlaceTagValue(tag, result.weight());
                })
                .toList();
        return values;
    }

    // 기존 Place를 네이버 우선 정보와 AI 분류 결과로 갱신한다.
    private Place refresh(Place place, PlaceCollectionTemp temp, PlaceType placeType) {
        // 사용자 노출 정보는 네이버 값을 우선하고, 누락된 값만 카카오 원본으로 보완한다.
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
        // 신규 Place도 기존 Place 갱신과 동일한 네이버 우선 정책으로 생성한다.
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
