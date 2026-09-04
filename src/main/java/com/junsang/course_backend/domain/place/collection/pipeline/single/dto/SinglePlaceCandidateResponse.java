package com.junsang.course_backend.domain.place.collection.pipeline.single.dto;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import java.math.BigDecimal;

/// 직접 코스 생성 화면에 표시하는 내부 DB 또는 카카오 장소 후보다.
public record SinglePlaceCandidateResponse(
        Long placeId,
        PlaceProvider provider,
        String providerPlaceId,
        String name,
        String addressName,
        String roadAddressName,
        String categoryName,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean collected
) {

    // 이미 정제되어 DB에 저장된 Place를 후보 응답으로 변환한다.
    public static SinglePlaceCandidateResponse from(Place place) {
        return new SinglePlaceCandidateResponse(
                place.getId(),
                place.getProvider(),
                place.getProviderPlaceId(),
                place.getName(),
                place.getAddressName(),
                place.getRoadAddressName(),
                place.getSourceCategoryName(),
                place.getLatitude(),
                place.getLongitude(),
                true
        );
    }

    // DB에 없는 카카오 검색 결과를 선택 전 후보로 변환한다.
    public static SinglePlaceCandidateResponse from(KakaoPlaceDocument document) {
        return new SinglePlaceCandidateResponse(
                null,
                PlaceProvider.KAKAO,
                document.id(),
                document.placeName(),
                document.addressName(),
                document.roadAddressName(),
                document.categoryName(),
                document.y(),
                document.x(),
                false
        );
    }
}
