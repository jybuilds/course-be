package com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service;

import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 수집 원본과 네이버 정제 정보를 이용해 PlaceType을 보정한다.
@Service
@RequiredArgsConstructor
public class PlaceTypeClassifier {

    private static final Pattern ROAD_ADDRESS = Pattern.compile("[가-힣A-Za-z0-9]+(?:로|길)\\s*\\d+(?:\\s*-\\s*\\d+)?");

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

        return findMatchingRule(placeName, categoryName, null, rules)
                .map(PlaceCategoryRule::getTargetPlaceType)
                .orElse(defaultPlaceType);
    }

    // 수집 프로필 Type을 1차 후보로 사용하되, 명확한 규칙이 있으면 먼저 보정한다.
    public PlaceType classifyForCollection(
            PlaceType defaultPlaceType,
            String categoryName,
            String placeName,
            List<PlaceCategoryRule> rules
    ) {
        return findMatchingRule(placeName, categoryName, null, rules)
                .map(PlaceCategoryRule::getTargetPlaceType)
                .orElse(defaultPlaceType);
    }

    // 네이버 카테고리까지 확인해 1차 Type 후보를 보정한다. 최종 판단은 AI가 수행한다.
    public PlaceType classifyAfterNaver(
            PlaceType collectedPlaceType,
            String placeName,
            String kakaoCategoryName,
            String naverCategoryName,
            List<PlaceCategoryRule> rules
    ) {
        return findMatchingRule(
                placeName,
                kakaoCategoryName,
                naverCategoryName,
                rules
        ).map(PlaceCategoryRule::getTargetPlaceType)
                .orElse(collectedPlaceType);
    }

    // AI 입력에도 같은 규칙 근거를 전달할 수 있도록 가장 높은 우선순위의 일치 규칙을 찾는다.
    public Optional<PlaceCategoryRule> findMatchingRule(
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
                .findFirst();
    }

    // 한 블로그 글 안에 장소 식별 정보와 규칙 키워드가 함께 있을 때만 보조 규칙 근거로 인정한다.
    public Optional<PlaceCategoryRule> findMatchingRuleWithRelevantBlog(
            String placeName,
            String naverTitle,
            String kakaoCategoryName,
            String naverCategoryName,
            String roadAddressName,
            String naverRoadAddressName,
            String blogEvidence,
            List<PlaceCategoryRule> rules
    ) {
        Optional<PlaceCategoryRule> directRule = findMatchingRule(
                placeName + " " + naverTitle,
                kakaoCategoryName,
                naverCategoryName,
                rules
        );
        if (directRule.isPresent()) {
            return directRule;
        }

        List<String> identities = List.of(
                normalizedIdentity(placeName),
                normalizedIdentity(naverTitle),
                roadAddressIdentity(roadAddressName),
                roadAddressIdentity(naverRoadAddressName)
        ).stream()
                .filter(identity -> identity.length() >= 3)
                .distinct()
                .toList();
        if (identities.isEmpty() || blogEvidence == null || blogEvidence.isBlank()) {
            return Optional.empty();
        }

        List<String> relevantEntries = List.of(blogEvidence.split("(?m)(?=^\\d+\\. )")).stream()
                .map(this::normalizedIdentity)
                .filter(entry -> identities.stream().anyMatch(entry::contains))
                .toList();
        return rules.stream()
                .filter(rule -> relevantEntries.stream()
                        .anyMatch(entry -> entry.contains(normalizedIdentity(rule.getKeyword()))))
                .findFirst();
    }

    // 공백·괄호·특수문자가 달라도 상호명과 도로명 주소를 비교할 수 있게 정규화한다.
    private String normalizedIdentity(String value) {
        return normalize(value).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    // 도로명과 건물번호만 추려 블로그의 축약 주소와도 비교한다.
    private String roadAddressIdentity(String address) {
        var matcher = ROAD_ADDRESS.matcher(address == null ? "" : address);
        return matcher.find() ? normalizedIdentity(matcher.group()) : "";
    }

    // 카카오 영문·한글 표기의 대소문자 차이를 무시한다.
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
