package com.junsang.course_backend.domain.place.collection.pipeline.single.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.LegalDongAreaResolver;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.pipeline.single.dto.SinglePlaceCandidateResponse;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SinglePlaceDiscoveryServiceTest {

    @Test
    void returnsNearbyDatabasePlaceWithoutCallingKakao() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        AreaRepository areaRepository = mock(AreaRepository.class);
        KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
        LegalDongAreaResolver areaResolver = mock(LegalDongAreaResolver.class);
        PlaceTypeClassifier classifier = mock(PlaceTypeClassifier.class);
        SingleAnchorWriter writer = mock(SingleAnchorWriter.class);
        SinglePlaceDiscoveryService service = new SinglePlaceDiscoveryService(
                placeRepository,
                areaRepository,
                kakaoLocalClient,
                areaResolver,
                classifier,
                writer
        );
        Place place = mock(Place.class);
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5550000"));
        when(place.getLongitude()).thenReturn(new BigDecimal("126.9250000"));
        when(placeRepository.findTop20ByIsActiveTrueAndNameContainingIgnoreCaseOrderByIdAsc("카페"))
                .thenReturn(List.of(place));

        List<SinglePlaceCandidateResponse> result = service.search(
                "카페",
                new BigDecimal("37.5550000"),
                new BigDecimal("126.9250000")
        );

        assertEquals(1, result.size());
        verifyNoInteractions(kakaoLocalClient);
    }
}
