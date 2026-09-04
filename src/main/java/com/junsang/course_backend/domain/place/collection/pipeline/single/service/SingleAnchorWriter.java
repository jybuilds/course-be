package com.junsang.course_backend.domain.place.collection.pipeline.single.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 사용자가 확정한 카카오 장소를 즉시 앵커로 사용할 수 있게 저장한다.
@Service
@RequiredArgsConstructor
public class SingleAnchorWriter {

    private final PlaceRepository placeRepository;
    private final PlaceCollectionTempRepository tempRepository;

    // 최종 Place를 최소 정보로 upsert하고 정제용 Temp도 없을 때만 생성한다.
    @Transactional
    public Result save(Area area, KakaoPlaceDocument document, PlaceType placeType) {
        Place place = placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, document.id())
                .map(existing -> refresh(existing, area, document, placeType))
                .orElseGet(() -> create(area, document, placeType));
        placeRepository.save(place);

        // 이미 완료된 Temp를 다시 초기화하지 않아 재태깅 작업을 불필요하게 만들지 않는다.
        boolean enrichmentQueued = tempRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, document.id())
                .isEmpty();
        if (enrichmentQueued) {
            tempRepository.save(PlaceCollectionTemp.create(
                    area,
                    PlaceProvider.KAKAO,
                    document.id(),
                    placeType,
                    false,
                    document.placeName(),
                    document.addressName(),
                    document.roadAddressName(),
                    document.categoryName(),
                    document.categoryGroupCode(),
                    document.y(),
                    document.x(),
                    document.placeUrl(),
                    document.phone()
            ));
        }
        return new Result(place, enrichmentQueued);
    }

    // 기존 Place는 사용자가 선택한 최신 카카오 원본 정보로 갱신한다.
    private Place refresh(Place place, Area area, KakaoPlaceDocument document, PlaceType placeType) {
        place.refresh(
                area,
                placeType,
                document.placeName(),
                document.addressName(),
                document.roadAddressName(),
                document.categoryName(),
                document.categoryGroupCode(),
                document.y(),
                document.x(),
                document.placeUrl(),
                null,
                document.phone()
        );
        return place;
    }

    // 정제가 끝나지 않은 장소도 좌표 기반 코스 생성에 사용할 수 있도록 즉시 생성한다.
    private Place create(Area area, KakaoPlaceDocument document, PlaceType placeType) {
        return Place.create(
                PlaceProvider.KAKAO,
                document.id(),
                area,
                placeType,
                document.placeName(),
                document.addressName(),
                document.roadAddressName(),
                document.categoryName(),
                document.categoryGroupCode(),
                document.y(),
                document.x(),
                document.placeUrl(),
                null,
                document.phone(),
                true,
                null,
                null
        );
    }

    public record Result(Place place, boolean enrichmentQueued) {
    }
}
