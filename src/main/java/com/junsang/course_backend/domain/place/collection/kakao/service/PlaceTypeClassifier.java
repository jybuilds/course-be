package com.junsang.course_backend.domain.place.collection.kakao.service;

import com.junsang.course_backend.domain.place.collection.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.kakao.repository.PlaceCategoryRuleRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 카카오 원본 카테고리와 장소명에 운영 분류 규칙을 적용한다.
@Service
@RequiredArgsConstructor
public class PlaceTypeClassifier {

    private final PlaceCategoryRuleRepository placeCategoryRuleRepository;

    // 초기 수집 한 번에 사용할 활성 규칙을 우선순위 순으로 조회한다.
    public List<PlaceCategoryRule> findActiveRules() {
        return placeCategoryRuleRepository.findByIsActiveTrueOrderByPriorityDescIdAsc();
    }

    // 규칙이 일치하면 규칙 타입을, 없으면 수집 프로필의 기본 타입을 사용한다.
    public PlaceType classify(
            PlaceType defaultPlaceType,
            String categoryName,
            String placeName,
            List<PlaceCategoryRule> rules
    ) {
        String source = normalize(categoryName) + " " + normalize(placeName);

        return rules.stream()
                .filter(rule -> source.contains(normalize(rule.getKeyword())))
                .map(PlaceCategoryRule::getTargetPlaceType)
                .findFirst()
                .orElse(defaultPlaceType);
    }

    // 카카오 영문·한글 표기의 대소문자 차이를 무시한다.
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
