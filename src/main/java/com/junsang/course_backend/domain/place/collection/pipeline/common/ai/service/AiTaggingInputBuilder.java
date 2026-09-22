package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.entity.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 단건·배치·재태깅에서 동일한 근거와 최신 규칙을 사용하도록 입력을 생성한다.
@Service
@RequiredArgsConstructor
public class AiTaggingInputBuilder {

    private final PlaceTypeClassifier classifier;

    // 단건·Batch 결과 저장까지 같은 활성 규칙을 공유하도록 규칙 목록을 제공한다.
    public List<PlaceCategoryRule> findActiveRules() {
        return classifier.findActiveRules();
    }

    // 제출마다 규칙을 한 번 조회하고 과거 AI Type도 최신 이름·카테고리 규칙으로 재검토한다.
    public Map<String, Object> create(List<PlaceCollectionTemp> targets, List<Tag> tags) {
        return create(targets, tags, findActiveRules());
    }

    // 요청 생성과 결과 저장에 사용할 동일 규칙 목록으로 AI 입력을 만든다.
    public Map<String, Object> create(
            List<PlaceCollectionTemp> targets,
            List<Tag> tags,
            List<PlaceCategoryRule> rules
    ) {
        return Map.of(
                "availableTags", tags.stream()
                        .map(tag -> Map.of("code", tag.getCode(), "displayName", tag.getDisplayName()))
                        .toList(),
                "typeRules", rules.stream()
                        .map(rule -> Map.of(
                                "keyword", rule.getKeyword(),
                                "targetPlaceType", rule.getTargetPlaceType().name(),
                                "priority", rule.getPriority()
                        ))
                        .toList(),
                "places", targets.stream()
                        .map(temp -> createPlace(temp, rules))
                        .toList()
        );
    }

    // 전체 규칙표와 별개로 이 장소에 실제 적용된 규칙을 보내 AI가 후보 Type의 근거를 확인하게 한다.
    private Map<String, Object> createPlace(
            PlaceCollectionTemp temp,
            List<PlaceCategoryRule> rules
    ) {
        var matchedRule = classifier.findMatchingRuleWithRelevantBlog(
                temp.getName(),
                temp.getNaverTitle(),
                temp.getKakaoCategoryName(),
                temp.getNaverCategoryName(),
                temp.getRoadAddressName(),
                temp.getNaverRoadAddressName(),
                temp.getNaverBlogEvidence(),
                rules
        );
        var place = new LinkedHashMap<String, Object>();
        place.put("tempId", temp.getId());
        place.put(
                "placeType",
                matchedRule.map(rule -> rule.getTargetPlaceType().name())
                        .orElse(temp.getDefaultPlaceType().name())
        );
        matchedRule.ifPresent(rule -> place.put("matchedTypeRule", Map.of(
                "keyword", rule.getKeyword(),
                "targetPlaceType", rule.getTargetPlaceType().name(),
                "priority", rule.getPriority()
        )));
        place.put("evidence", Map.of(
                "name", value(temp.getName()),
                "naverName", value(temp.getNaverTitle()),
                "address", value(temp.getAddressName()),
                "roadAddress", value(temp.getRoadAddressName()),
                "naverAddress", value(temp.getNaverAddressName()),
                "naverRoadAddress", value(temp.getNaverRoadAddressName()),
                "kakaoCategory", value(temp.getKakaoCategoryName()),
                "naverCategory", value(temp.getNaverCategoryName()),
                "blogEvidence", value(temp.getNaverBlogEvidence())
        ));
        return place;
    }

    // Map.of는 null을 허용하지 않으므로 누락된 외부 정보는 빈 문자열로 전달한다.
    private String value(String value) {
        return value == null ? "" : value;
    }
}
