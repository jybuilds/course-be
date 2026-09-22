package com.junsang.course_backend.domain.place.collection.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaceCollectionTempTest {

    @Test
    void movesFromNaverEnrichmentToAiTagging() {
        PlaceCollectionTemp temp = createTemp();

        assertThat(temp.getProcessingStep()).isEqualTo(PlaceCollectionStep.NAVER_ENRICHMENT);
        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PENDING);
        assertThat(temp.getAttemptCount()).isZero();

        temp.startProcessing();
        temp.completeNaverEnrichment(
                PlaceType.CAFE,
                "성심당 본점",
                "https://example.com",
                "카페,디저트>베이커리",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15",
                "",
                NaverBlogCollectionStatus.EMPTY,
                null
        );

        assertThat(temp.getAttemptCount()).isEqualTo(1);
        assertThat(temp.getProcessingStep()).isEqualTo(PlaceCollectionStep.AI_TAGGING);
        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PENDING);
    }

    @Test
    void recordsFailureAndReturnsToPendingForRetry() {
        PlaceCollectionTemp temp = createTemp();
        temp.startProcessing();
        temp.fail(PlaceRefinementErrorCode.NAVER_RESULT_NOT_FOUND, "검색 결과 없음");

        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.FAILED);
        assertThat(temp.getErrorCode()).isEqualTo(PlaceRefinementErrorCode.NAVER_RESULT_NOT_FOUND);

        temp.retry();

        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PENDING);
        assertThat(temp.getErrorCode()).isNull();
    }

    @Test
    void completesAiTagging() {
        PlaceCollectionTemp temp = createTemp();
        temp.startProcessing();
        temp.completeNaverEnrichment(
                PlaceType.CAFE,
                "성심당 본점",
                "https://example.com",
                "카페,디저트>베이커리",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15",
                "",
                NaverBlogCollectionStatus.EMPTY,
                null
        );

        temp.startAiTagging();
        temp.completeAiTagging();

        assertThat(temp.getProcessingStep()).isEqualTo(PlaceCollectionStep.AI_TAGGING);
        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.COMPLETED);
        assertThat(temp.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void restartsCompletedAiTaggingForRetagging() {
        PlaceCollectionTemp temp = createTemp();
        temp.startProcessing();
        temp.completeNaverEnrichment(
                PlaceType.CAFE,
                "성심당 본점",
                "https://example.com",
                "카페,디저트>베이커리",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15",
                "",
                NaverBlogCollectionStatus.EMPTY,
                null
        );
        temp.startAiTagging();
        temp.completeAiTagging();

        temp.restartAiBatchTagging(1L);

        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PROCESSING);
        assertThat(temp.getAiBatchJobId()).isEqualTo(1L);
    }

    @Test
    void replacesTheFirstTypeCandidateWithTheAiResult() {
        PlaceCollectionTemp temp = createTemp();

        PlaceType placeType = temp.applyAiPlaceType(PlaceType.MEAL);

        assertThat(placeType).isEqualTo(PlaceType.MEAL);
        assertThat(temp.getDefaultPlaceType()).isEqualTo(PlaceType.MEAL);
    }

    private PlaceCollectionTemp createTemp() {
        return PlaceCollectionTemp.create(
                Mockito.mock(Area.class),
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
    }
}
