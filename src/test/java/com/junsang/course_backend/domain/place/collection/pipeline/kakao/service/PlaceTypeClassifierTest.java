package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCategoryRuleRepository;
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
}
