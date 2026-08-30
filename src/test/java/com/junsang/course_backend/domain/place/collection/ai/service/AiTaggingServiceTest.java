package com.junsang.course_backend.domain.place.collection.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.ai.dto.AiTaggingResponse;
import com.junsang.course_backend.domain.place.collection.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.infra.openai.OpenAiClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiTaggingServiceTest {

    @Test
    void tagsTempAndDelegatesFinalSave() {
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        AiTaggingWriter writer = mock(AiTaggingWriter.class);
        AiTaggingService service = new AiTaggingService(
                tempRepository,
                tagRepository,
                openAiClient,
                new ObjectMapper(),
                writer
        );
        PlaceCollectionTemp temp = createAiPendingTemp();
        Tag bread = Tag.create("BREAD", "빵", 1);
        AiTaggingResponse completed = new AiTaggingResponse(
                1L,
                true,
                10L,
                PlaceCollectionTempStatus.COMPLETED,
                null,
                null
        );
        when(tempRepository.findById(1L)).thenReturn(Optional.of(temp));
        when(tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(bread));
        when(openAiClient.createStructuredResponse(anyString(), anyString(), anyMap()))
                .thenReturn("""
                        {"results":[{"tempId":1,"placeType":"CAFE","tags":[{"code":"BREAD","weight":95}]}]}
                        """);
        when(writer.complete(eq(1L), any(AiTaggingResult.class), anyMap()))
                .thenReturn(completed);

        AiTaggingResponse response = service.tag(1L);

        assertThat(response).isEqualTo(completed);
        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PROCESSING);
        verify(openAiClient).createStructuredResponse(anyString(), anyString(), anyMap());
        verify(writer).complete(eq(1L), any(AiTaggingResult.class), anyMap());
    }

    private PlaceCollectionTemp createAiPendingTemp() {
        PlaceCollectionTemp temp = PlaceCollectionTemp.create(
                mock(Area.class),
                PlaceProvider.KAKAO,
                "1",
                PlaceType.CAFE,
                "성심당 본점",
                "대전 중구 은행동 145",
                "대전 중구 대종로480번길 15",
                "음식점 > 카페",
                "CE7",
                new BigDecimal("36.3275000"),
                new BigDecimal("127.4277000"),
                "https://place.map.kakao.com/1",
                "042-000-0000"
        );
        ReflectionTestUtils.setField(temp, "id", 1L);
        temp.startProcessing();
        temp.completeNaverEnrichment(
                "성심당 본점",
                "https://example.com",
                "카페,디저트>베이커리",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15"
        );
        return temp;
    }
}
