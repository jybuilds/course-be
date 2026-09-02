package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCategoryRuleRepository;
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

    // 네이버 매칭 후 MEAL·CAFE만 네이버 카테고리를 기준으로 최종 보정한다.
    public PlaceType classifyAfterNaver(
            PlaceType collectedPlaceType,
            String placeName,
            String kakaoCategoryName,
            String naverCategoryName,
            List<PlaceCategoryRule> rules
    ) {
        if (collectedPlaceType == PlaceType.ACTIVITY) {
            return PlaceType.ACTIVITY;
        }

        Optional<PlaceType> ruleType = findRule(
                placeName,
                kakaoCategoryName,
                naverCategoryName,
                rules
        );
        if (ruleType.isPresent()) {
            return ruleType.get();
        }

        String category = normalize(naverCategoryName);
        if (category.isBlank()) {
            return collectedPlaceType;
        }
        if (category.contains("카페") || category.contains("디저트")) {
            return PlaceType.CAFE;
        }
        if (collectedPlaceType == PlaceType.MEAL && category.startsWith("음식점")) {
            return PlaceType.MEAL;
        }
        return PlaceType.ACTIVITY;
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
}
