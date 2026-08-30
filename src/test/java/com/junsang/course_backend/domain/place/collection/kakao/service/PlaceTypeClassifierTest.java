package com.junsang.course_backend.domain.place.collection.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.kakao.repository.PlaceCategoryRuleRepository;
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
}
