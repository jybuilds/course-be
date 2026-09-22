package com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaceTypeClassifierTest {

    @Test
    void categoryRuleOverridesCollectionProfilePlaceType() {
        PlaceCategoryRuleRepository repository = Mockito.mock(PlaceCategoryRuleRepository.class);
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(repository);
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("방탈출카페", PlaceType.ACTIVITY, 100)
        );

        when(repository.findByIsActiveTrueOrderByPriorityDescIdAsc()).thenReturn(rules);

        assertThat(classifier.classify(
                PlaceType.CAFE,
                "가정,생활 > 여가시설 > 방탈출카페",
                "룸익스케이프 블랙점",
                classifier.findActiveRules()
        )).isEqualTo(PlaceType.ACTIVITY);
    }

    @Test
    void naverRefinementUsesRulesBeforeTheDefaultCategoryPolicy() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("방탈출", PlaceType.ACTIVITY, 100),
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        );

        assertThat(classifier.classifyAfterNaver(
                PlaceType.CAFE,
                "히포 브런치하우스",
                "음식점 > 카페",
                "음식점>카페,디저트",
                rules
        )).isEqualTo(PlaceType.MEAL);

        assertThat(classifier.classifyAfterNaver(
                PlaceType.CAFE,
                "룸익스케이프",
                "음식점 > 카페",
                "가정,생활>여가시설>방탈출카페",
                rules
        )).isEqualTo(PlaceType.ACTIVITY);
    }

    @Test
    void naverCategoryRuleChangesTheFirstTypeCandidate() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        );

        PlaceType result = classifier.classifyAfterNaver(
                PlaceType.CAFE,
                "셉트",
                "음식점 > 카페",
                "브런치카페",
                rules
        );

        assertThat(result).isEqualTo(PlaceType.MEAL);
    }

    @Test
    void ignoresBlogEvidenceWhenCreatingTheFirstTypeCandidate() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        );

        PlaceType result = classifier.classifyAfterNaver(
                PlaceType.CAFE,
                "일반 카페",
                "음식점 > 카페",
                "카페,디저트>카페",
                rules
        );

        assertThat(result).isEqualTo(PlaceType.CAFE);
    }

    @Test
    void appliesRuleWhenTheSameBlogEntryContainsPlaceNameAndKeyword() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        );

        assertThat(classifier.findMatchingRuleWithRelevantBlog(
                "유주얼하우스",
                "유주얼하우스",
                "음식점 > 카페",
                "음식점>카페,디저트",
                "서울 마포구 양화로7길 48",
                "서울특별시 마포구 양화로7길 48 1층",
                "1. 제목: 다른 카페 | 내용: 인천의 브런치카페\n"
                        + "2. 제목: 유주얼하우스 후기 | 내용: 유주얼하우스는 합정 브런치 카페다.",
                rules
        )).contains(rules.getFirst());
    }

    @Test
    void ignoresBlogKeywordWhenTheSameEntryHasNoPlaceIdentity() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));
        List<PlaceCategoryRule> rules = List.of(
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        );

        assertThat(classifier.findMatchingRuleWithRelevantBlog(
                "카페 클로버",
                "카페 클로버",
                "음식점 > 카페",
                "음식점>카페,디저트",
                "서울 마포구 양화로8길 38",
                "서울특별시 마포구 양화로8길 38 2층",
                "1. 제목: 송도 카페 | 내용: 인천의 브런치카페를 소개한다.",
                rules
        )).isEmpty();
    }

    @Test
    void keepsCollectionProfileAsFirstCandidateUnlessARuleMatches() {
        PlaceTypeClassifier classifier = new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class));

        PlaceType category = classifier.classifyForCollection(
                PlaceType.CAFE,
                "음식점 > 카페",
                "일반 카페",
                List.of()
        );
        PlaceType keyword = classifier.classifyForCollection(
                PlaceType.ACTIVITY,
                "가정,생활 > 여가시설",
                "방탈출",
                List.of()
        );

        assertThat(category).isEqualTo(PlaceType.CAFE);
        assertThat(keyword).isEqualTo(PlaceType.ACTIVITY);
    }
}
