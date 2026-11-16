package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.entity.NaverBlogCollectionStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.PriorityScores;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.TagWeight;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCategoryRuleRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.NaverSearchUrlCreator;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AiBatchResultWriterTest {

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
        AiBatchResultWriter writer = new AiBatchResultWriter(
                tempRepository,
                placeRepository,
                placeTagRepository,
                mock(NaverSearchUrlCreator.class),
                new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)), resultJdbc()
        );

        when(tempRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(temp));
        when(placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, "47"))
                .thenReturn(Optional.of(place));
        when(place.getId()).thenReturn(10L);
        when(placeTagRepository.findByPlaceId(10L)).thenReturn(List.of());

        var priorityScores = new PriorityScores(4, -2);
        writer.complete(
                1L, "owner",
                new AiTaggingResult(1L, PlaceType.CAFE, List.of("브런치", "샌드위치", " 브런치 "), priorityScores, List.of(new TagWeight("CAFE", 100))),
                Map.of("CAFE", cafe, "BRUNCH", brunch, "RESTAURANT", Tag.create("RESTAURANT", "음식점", 30)),
                List.of(brunchRule)
        );

        assertThat(temp.getDefaultPlaceType()).isEqualTo(PlaceType.MEAL);
        ArgumentCaptor<List<PlaceTag>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(placeTagRepository).saveAll(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).extracting(tag -> tag.getTag().getCode())
                .containsExactlyInAnyOrder("CAFE", "BRUNCH", "RESTAURANT");
        verify(place).updateKeywords(List.of("브런치", "샌드위치"));
        verify(place).updatePriorityScores((short) 29, (short) 23);
        verify(place).refresh(any(), eq(PlaceType.MEAL), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void calculatesPriorityScoreFromStoredComponents() {
        Place place = mock(Place.class, CALLS_REAL_METHODS);

        assertThat(place.getPriorityScore()).isZero();
        place.updatePriorityScores((short) 29, (short) 23);
        assertThat(place.getPriorityScore()).isEqualTo(52);
    }

    @ParameterizedTest
    @CsvSource({
            "CAFE, MARKET, true", "CAFE, BOARD_GAME, true", "CAFE, ESCAPE_ROOM, true",
            "CAFE, KOREAN_FOOD, true", "CAFE, JAPANESE_FOOD, true",
            "CAFE, WESTERN_FOOD, true", "CAFE, ASIAN_FOOD, true",
            "MEAL, CAFE, true", "MEAL, BOARD_GAME, true", "MEAL, ESCAPE_ROOM, true",
            "ACTIVITY, CAFE, true", "ACTIVITY, KOREAN_FOOD, true",
            "ACTIVITY, JAPANESE_FOOD, true", "ACTIVITY, WESTERN_FOOD, true",
            "ACTIVITY, ASIAN_FOOD, true", "ACTIVITY, RESTAURANT, true",
            "CAFE, CAFE, false", "CAFE, BRUNCH, false", "CAFE, RESTAURANT, false",
            "MEAL, KOREAN_FOOD, false", "MEAL, RESTAURANT, false",
            "ACTIVITY, BOARD_GAME, false", "ACTIVITY, PARK, false"
    })
    @SuppressWarnings("unchecked")
    void savesReviewFlagAndPreservesConflictingTags(PlaceType type, String code, boolean needsReview) {
        var tempRepository = mock(PlaceCollectionTempRepository.class);
        var placeRepository = mock(PlaceRepository.class);
        var placeTagRepository = mock(PlaceTagRepository.class);
        Place place = mock(Place.class);
        when(tempRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(brunchTemp()));
        when(placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, "47"))
                .thenReturn(Optional.of(place));
        when(place.getId()).thenReturn(10L);
        when(placeTagRepository.findByPlaceId(10L)).thenReturn(List.of());
        var writer = new AiBatchResultWriter(tempRepository, placeRepository, placeTagRepository,
                mock(NaverSearchUrlCreator.class),
                new PlaceTypeClassifier(mock(PlaceCategoryRuleRepository.class)), resultJdbc());

        writer.complete(1L, "owner",
                new AiTaggingResult(1L, type, List.of(), null,
                        List.of(new TagWeight(code, 80), new TagWeight("QUIET", 70))),
                Map.of(code, Tag.create(code, code, 1),
                        "QUIET", Tag.create("QUIET", "조용한", 2)), List.of());

        verify(place).updateNeedsReview(needsReview);
        verify(place).refresh(any(), eq(type), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        ArgumentCaptor<List<PlaceTag>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(placeTagRepository).saveAll(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).extracting(tag -> tag.getTag().getCode()).contains(code);
    }

    private org.springframework.jdbc.core.JdbcTemplate resultJdbc() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("collect_token"), eq(String.class), eq(1L))).thenReturn("owner");
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("ai_batch_tagging_items"), eq(String.class), eq(1L), eq(1L))).thenReturn("PENDING");
        return jdbc;
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
                "음식점>브런치 카페",
                "서울특별시 마포구 서교동 386-3",
                "서울특별시 마포구 양화로11길 41",
                "유주얼하우스는 합정 브런치 카페다.",
                NaverBlogCollectionStatus.COLLECTED,
                null
        );
        temp.startAiBatchTagging(1L);
        return temp;
    }
}
