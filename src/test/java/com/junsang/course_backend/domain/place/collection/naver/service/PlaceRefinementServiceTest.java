package com.junsang.course_backend.domain.place.collection.naver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.naver.dto.PlaceRefinementResponse;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.infra.naver.NaverLocalClient;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalItem;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceRefinementServiceTest {

    @Test
    void enrichesTempWithMatchedNaverPlace() {
        PlaceCollectionTempRepository repository = mock(PlaceCollectionTempRepository.class);
        NaverLocalClient client = mock(NaverLocalClient.class);
        PlaceRefinementService service = createService(repository, client);
        PlaceCollectionTemp temp = createTemp();
        NaverLocalItem matched = new NaverLocalItem(
                "<b>성심당</b> 본점",
                "https://example.com",
                "카페,디저트>베이커리",
                "",
                "",
                "대전광역시 중구 은행동 145",
                "대전광역시 중구 대종로480번길 15",
                "1274277000",
                "363275000"
        );
        NaverLocalItem differentAddress = new NaverLocalItem(
                "성심당 본점",
                "",
                "카페,디저트>베이커리",
                "",
                "",
                "대전광역시 중구 오류동 1",
                "대전광역시 중구 오류로 1",
                "1274277000",
                "363275000"
        );
        when(repository.findById(1L)).thenReturn(Optional.of(temp));
        when(repository.save(temp)).thenReturn(temp);
        when(client.search("성심당 본점 은행"))
                .thenReturn(new NaverLocalSearchResponse("", 1, 1, 1, List.of(differentAddress)));
        when(client.search("성심당 본점 대전 중구 대종로480번길 15"))
                .thenReturn(new NaverLocalSearchResponse("", 1, 1, 1, List.of(matched)));

        PlaceRefinementResponse response = service.refine(1L);

        assertThat(response.success()).isTrue();
        assertThat(temp.getNaverTitle()).isEqualTo("성심당 본점");
        assertThat(temp.getNaverSearchUrl())
                .isEqualTo("https://map.naver.com/p/search/%EC%84%B1%EC%8B%AC%EB%8B%B9%20%EB%B3%B8%EC%A0%90%20%EC%9D%80%ED%96%89");
        assertThat(temp.getProcessingStep()).isEqualTo(PlaceCollectionStep.AI_TAGGING);
        assertThat(temp.getStatus()).isEqualTo(PlaceCollectionTempStatus.PENDING);
        verify(client).search("성심당 본점 은행");
        verify(client).search("성심당 본점 대전 중구 대종로480번길 15");
    }

    @Test
    void recordsPossibleRelocationWhenNameAndCategoryMatchButAddressDiffers() {
        PlaceCollectionTempRepository repository = mock(PlaceCollectionTempRepository.class);
        NaverLocalClient client = mock(NaverLocalClient.class);
        PlaceCollectionTemp temp = createTemp();
        NaverLocalItem differentAddress = new NaverLocalItem(
                "성심당 본점",
                "",
                "카페,디저트>베이커리",
                "",
                "",
                "대전광역시 중구 오류동 1",
                "대전광역시 중구 오류로 1",
                "1274277000",
                "363275000"
        );
        when(repository.findById(1L)).thenReturn(Optional.of(temp));
        when(repository.save(temp)).thenReturn(temp);
        when(client.search("성심당 본점 은행"))
                .thenReturn(new NaverLocalSearchResponse("", 1, 1, 1, List.of(differentAddress)));
        when(client.search("성심당 본점 대전 중구 대종로480번길 15"))
                .thenReturn(new NaverLocalSearchResponse("", 0, 1, 1, List.of()));

        PlaceRefinementResponse response = createService(repository, client).refine(1L);

        assertThat(response.errorCode()).isEqualTo(PlaceRefinementErrorCode.NAVER_POSSIBLE_RELOCATION);
        assertThat(temp.getNaverRoadAddressName()).isEqualTo("대전광역시 중구 오류로 1");
    }

    private PlaceRefinementService createService(
            PlaceCollectionTempRepository repository,
            NaverLocalClient client
    ) {
        NaverPlaceNormalizer normalizer = new NaverPlaceNormalizer();
        return new PlaceRefinementService(
                repository,
                client,
                new NaverPlaceMatcher(normalizer),
                normalizer,
                new NaverSearchUrlCreator()
        );
    }

    private PlaceCollectionTemp createTemp() {
        return PlaceCollectionTemp.create(
                mock(Area.class),
                PlaceProvider.KAKAO,
                "1",
                PlaceType.CAFE,
                "성심당 본점",
                "대전 중구 은행동 145",
                "대전 중구 대종로480번길 15",
                "카페",
                "CE7",
                new BigDecimal("36.3275000"),
                new BigDecimal("127.4277000"),
                "https://place.map.kakao.com/1",
                "042-000-0000"
        );
    }
}
