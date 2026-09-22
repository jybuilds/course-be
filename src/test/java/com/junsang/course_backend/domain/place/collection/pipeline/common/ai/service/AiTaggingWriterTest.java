package com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.entity.NaverBlogCollectionStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResultBatch.TagWeight;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.repository.PlaceCategoryRuleRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service.NaverSearchUrlCreator;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AiTaggingWriterTest {

    @Test
    @SuppressWarnings("unchecked")
    void forcesMatchedRuleTypeAndPreservesSameNamedTag() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceTagRepository placeTagRepository = mock(PlaceTagRepository.class);
        Place place = mock(Place.class);
        PlaceCollectionTemp temp = brunchTemp();
        Tag cafe = Tag.create("CAFE", "카페", 1);
        Tag brunch = Tag.create("BRUNCH", "브런치", 2);
        PlaceCategoryRule brunchRule = PlaceCategoryRule.create("브런치", PlaceType.MEAL, 90);
        AiTaggingWriter writer = new AiTaggingWriter(
                tempRepository,
                placeRepository,
                placeTagRepository,
                mock(NaverSearchUrlCreator.class),
                new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class))
        );

        when(tempRepository.findById(1L)).thenReturn(Optional.of(temp));
        when(placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, "47"))
                .thenReturn(Optional.of(place));
        when(place.getId()).thenReturn(10L);
        when(placeTagRepository.findByPlaceId(10L)).thenReturn(List.of());

        writer.complete(
                1L,
                new AiTaggingResult(1L, PlaceType.CAFE, List.of(new TagWeight("CAFE", 100))),
                Map.of("CAFE", cafe, "BRUNCH", brunch),
                List.of(brunchRule)
        );

        assertThat(temp.getDefaultPlaceType()).isEqualTo(PlaceType.MEAL);
        ArgumentCaptor<List<PlaceTag>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(placeTagRepository).saveAll(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).extracting(tag -> tag.getTag().getCode())
                .containsExactlyInAnyOrder("CAFE", "BRUNCH");
        verify(place).refresh(any(), eq(PlaceType.MEAL), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private PlaceCollectionTemp brunchTemp() {
        PlaceCollectionTemp temp = PlaceCollectionTemp.create(
                mock(Area.class),
                PlaceProvider.KAKAO,
                "47",
                PlaceType.CAFE,
                "유주얼하우스",
                "서울 마포구 서교동 386-3",
                "서울 마포구 양화로11길 41",
                "음식점 > 카페",
                "CE7",
                new BigDecimal("37.5522171"),
                new BigDecimal("126.9145544"),
                "https://place.map.kakao.com/47",
                "070-0000-0000"
        );
        ReflectionTestUtils.setField(temp, "id", 1L);
        temp.startProcessing();
        temp.completeNaverEnrichment(
                PlaceType.CAFE,
                "유주얼하우스",
                "https://map.naver.com/p/search/유주얼하우스",
                "음식점>카페,디저트",
                "서울특별시 마포구 서교동 386-3",
                "서울특별시 마포구 양화로11길 41",
                "유주얼하우스는 합정 브런치 카페다.",
                NaverBlogCollectionStatus.COLLECTED,
                null
        );
        temp.startAiTagging();
        return temp;
    }
}
