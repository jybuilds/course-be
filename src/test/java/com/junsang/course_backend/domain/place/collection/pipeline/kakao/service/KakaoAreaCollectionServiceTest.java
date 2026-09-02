package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionSearchType;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionProfile;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionProfileRepository;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoSearchMeta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KakaoAreaCollectionServiceTest {

    @Test
    void storesCollectedKakaoPlaceInTemp() {
        CityRepository cityRepository = mock(CityRepository.class);
        AreaRepository areaRepository = mock(AreaRepository.class);
        PlaceCollectionTempRepository tempRepository = mock(PlaceCollectionTempRepository.class);
        PlaceCollectionProfileRepository profileRepository = mock(PlaceCollectionProfileRepository.class);
        PlaceCollectionJobRepository jobRepository = mock(PlaceCollectionJobRepository.class);
        KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
        LegalDongAreaResolver areaResolver = mock(LegalDongAreaResolver.class);
        PlaceTypeClassifier classifier = mock(PlaceTypeClassifier.class);
        KakaoAreaCollectionService service = new KakaoAreaCollectionService(
                cityRepository,
                areaRepository,
                tempRepository,
                profileRepository,
                jobRepository,
                kakaoLocalClient,
                areaResolver,
                classifier
        );
        Area area = mock(Area.class);
        PlaceCollectionProfile profile = mock(PlaceCollectionProfile.class);
        when(areaRepository.findById(4L)).thenReturn(Optional.of(area));
        when(areaRepository.findByCode("SEOUL_SINCHON_EWHA")).thenReturn(Optional.of(area));
        when(profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO))
                .thenReturn(List.of(profile));
        when(profile.getId()).thenReturn(1L);
        when(profile.getCode()).thenReturn("CAFE");
        when(profile.getSearchType()).thenReturn(CollectionSearchType.CATEGORY);
        when(profile.getCategoryGroupCode()).thenReturn("CE7");
        when(profile.getPlaceType()).thenReturn(PlaceType.CAFE);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classifier.findActiveRules()).thenReturn(List.of());
        when(classifier.classify(any(), any(), any(), any())).thenReturn(PlaceType.CAFE);
        when(areaResolver.resolveAreaCode("서울 서대문구 창천동 18-11"))
                .thenReturn("SEOUL_SINCHON_EWHA");
        when(tempRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, "27329834"))
                .thenReturn(Optional.empty());
        when(kakaoLocalClient.searchCategory(any())).thenReturn(new KakaoKeywordSearchResponse(
                new KakaoSearchMeta(1, 1, true),
                List.of(new KakaoPlaceDocument(
                        "27329834",
                        "룸익스케이프 블랙점",
                        "가정,생활 > 여가시설 > 방탈출카페",
                        "서울 서대문구 창천동 18-11",
                        "서울 서대문구 연세로4길 6",
                        "010-2940-5680",
                        "",
                        "",
                        new BigDecimal("126.9373596"),
                        new BigDecimal("37.5569184"),
                        "https://place.map.kakao.com/27329834",
                        ""
                ))
        ));

        service.collectArea(4L);

        verify(tempRepository).save(any(PlaceCollectionTemp.class));
    }
}
