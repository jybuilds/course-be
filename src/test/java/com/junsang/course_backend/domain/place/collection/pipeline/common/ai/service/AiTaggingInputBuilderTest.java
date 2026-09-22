package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTaggingInputBuilderTest {

    @Test
    void rechecksStoredAiTypeAndKeepsBlogOnlyAsEvidence() throws Exception {
        var repository = mock(PlaceCategoryRuleRepository.class);
        when(repository.findByIsActiveTrueOrderByPriorityDescIdAsc()).thenReturn(List.of(
                PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90)
        ));
        var sept = place(47L, "셉트", "브런치카페", "디저트 카페 방문 후기");
        var clover = place(77L, "카페 클로버", "음식점>카페,디저트", "인천의 다른 브런치카페");
        var input = new AiTaggingInputBuilder(new PlaceTypeClassifier(repository))
                .create(List.of(sept, clover), List.of());
        var json = new ObjectMapper().valueToTree(input);

        assertThat(json.path("places").get(0).path("placeType").asText()).isEqualTo("MEAL");
        assertThat(json.path("places").get(1).path("placeType").asText()).isEqualTo("CAFE");
        assertThat(json.path("places").get(0).path("matchedTypeRule").path("keyword").asText())
                .isEqualTo("브런치");
        assertThat(json.path("places").get(1).has("matchedTypeRule")).isFalse();
        assertThat(json.findValues("blogEvidence")).hasSize(2);
        assertThat(json.path("places").get(0).path("evidence").path("roadAddress").asText())
                .isEqualTo("서울 마포구 양화로11길 41");
        assertThat(json.path("typeRules").get(0).path("keyword").asText()).isEqualTo("브런치");
        verify(repository, times(1)).findByIsActiveTrueOrderByPriorityDescIdAsc();
        verify(sept, never()).applyAiPlaceType(any());
    }

    private PlaceCollectionTemp place(Long id, String name, String category, String blog) {
        var temp = mock(PlaceCollectionTemp.class);
        when(temp.getId()).thenReturn(id);
        when(temp.getName()).thenReturn(name);
        when(temp.getDefaultPlaceType()).thenReturn(PlaceType.CAFE);
        when(temp.getKakaoCategoryName()).thenReturn("음식점 > 카페");
        when(temp.getNaverCategoryName()).thenReturn(category);
        when(temp.getNaverBlogEvidence()).thenReturn(blog);
        when(temp.getRoadAddressName()).thenReturn("서울 마포구 양화로11길 41");
        return temp;
    }
}
