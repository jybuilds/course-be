package com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service;

import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.CollectionSearchType;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 수집 원본과 네이버 정제 정보를 이용해 PlaceType을 보정한다.
@Service
@RequiredArgsConstructor
public class PlaceTypeClassifier {

    private final PlaceCategoryRuleRepository placeCategoryRuleRepository;

    // 초기 수집 한 번에 사용할 활성 규칙을 우선순위 순으로 조회한다.
    public List<PlaceCategoryRule> findActiveRules() {
        return placeCategoryRuleRepository.findByIsActiveTrueOrderByPriorityDescIdAsc();
    }

    // 카카오 수집 단계에서는 ACTIVITY를 유지하고, 나머지 타입에만 규칙을 적용한다.
    public PlaceType classify(
            PlaceType defaultPlaceType,
            String categoryName,
            String placeName,
            List<PlaceCategoryRule> rules
    ) {
        if (defaultPlaceType == PlaceType.ACTIVITY) {
            return PlaceType.ACTIVITY;
        }

        return findRule(placeName, categoryName, null, rules)
                .orElse(defaultPlaceType);
    }

    // 수집 프로필과 규칙을 이용해 AI 이전에 타입이 확정됐는지 함께 결정한다.
    public PlaceTypeClassification classifyForCollection(
            CollectionSearchType searchType,
            PlaceType defaultPlaceType,
            String categoryName,
            String placeName,
            List<PlaceCategoryRule> rules
    ) {
        if (searchType == CollectionSearchType.KEYWORD) {
            return new PlaceTypeClassification(defaultPlaceType, true);
        }

        return findRule(placeName, categoryName, null, rules)
                .map(placeType -> new PlaceTypeClassification(placeType, true))
                .orElseGet(() -> new PlaceTypeClassification(defaultPlaceType, false));
    }

    // 네이버 정보에서 새 규칙이 확인된 경우만 타입을 확정하고 나머지는 AI 판단 대상으로 남긴다.
    public PlaceType classifyAfterNaver(
            PlaceType collectedPlaceType,
            String placeName,
            String kakaoCategoryName,
            String naverCategoryName,
            List<PlaceCategoryRule> rules
    ) {
        return classifyAfterNaver(
                collectedPlaceType,
                false,
                placeName,
                kakaoCategoryName,
                naverCategoryName,
                rules
        ).placeType();
    }

    // 네이버 정보에서 새 규칙이 확인된 경우만 타입을 확정하고 나머지는 AI 판단 대상으로 남긴다.
    public PlaceTypeClassification classifyAfterNaver(
            PlaceType collectedPlaceType,
            boolean placeTypeFinalized,
            String placeName,
            String kakaoCategoryName,
            String naverCategoryName,
            List<PlaceCategoryRule> rules
    ) {
        if (placeTypeFinalized) {
            return new PlaceTypeClassification(collectedPlaceType, true);
        }

        Optional<PlaceType> ruleType = findRule(
                placeName,
                kakaoCategoryName,
                naverCategoryName,
                rules
        );
        return ruleType
                .map(placeType -> new PlaceTypeClassification(placeType, true))
                .orElseGet(() -> new PlaceTypeClassification(collectedPlaceType, false));
    }

    // 장소명과 카카오·네이버 카테고리 중 하나에 규칙 키워드가 있으면 우선 적용한다.
    private Optional<PlaceType> findRule(
            String placeName,
            String kakaoCategoryName,
            String naverCategoryName,
            List<PlaceCategoryRule> rules
    ) {
        String source = String.join(
                " ",
                normalize(placeName),
                normalize(kakaoCategoryName),
                normalize(naverCategoryName)
        );

        return rules.stream()
                .filter(rule -> source.contains(normalize(rule.getKeyword())))
                .map(PlaceCategoryRule::getTargetPlaceType)
                .findFirst();
    }

    // 카카오 영문·한글 표기의 대소문자 차이를 무시한다.
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record PlaceTypeClassification(
            PlaceType placeType,
            boolean finalized
    ) {
    }
}
