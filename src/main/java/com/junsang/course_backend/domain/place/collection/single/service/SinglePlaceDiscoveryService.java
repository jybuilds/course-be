package com.junsang.course_backend.domain.place.collection.single.service;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.LegalDongAreaResolver;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.single.dto.SingleAnchorRequest;
import com.junsang.course_backend.domain.place.collection.single.dto.SingleAnchorResponse;
import com.junsang.course_backend.domain.place.collection.single.dto.SinglePlaceCandidateResponse;
import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 직접 코스 생성에서 DB 우선 검색과 카카오 예외 조회를 분리해 처리한다.
@Service
@RequiredArgsConstructor
public class SinglePlaceDiscoveryService {

    private static final int SEARCH_RADIUS_METERS = 3_000;
    private static final int SEARCH_SIZE = 15;

    private final PlaceRepository placeRepository;
    private final AreaRepository areaRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final LegalDongAreaResolver legalDongAreaResolver;
    private final PlaceTypeClassifier placeTypeClassifier;
    private final SingleAnchorWriter writer;

    // DB에서 먼저 찾고 결과가 없을 때만 카카오를 호출해 후보를 반환한다.
    public List<SinglePlaceCandidateResponse> search(
            String query,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        List<SinglePlaceCandidateResponse> databaseCandidates = placeRepository
                .findTop20ByIsActiveTrueAndNameContainingIgnoreCaseOrderByIdAsc(query)
                .stream()
                .filter(place -> distanceMeters(place, latitude, longitude) <= SEARCH_RADIUS_METERS)
                .sorted(Comparator.comparingDouble(place -> distanceMeters(place, latitude, longitude)))
                .map(SinglePlaceCandidateResponse::from)
                .toList();
        if (!databaseCandidates.isEmpty()) {
            return databaseCandidates;
        }

        // DB 미존재 후보만 외부 검색으로 보완한다.
        return searchKakao(query, latitude, longitude).stream()
                .map(SinglePlaceCandidateResponse::from)
                .toList();
    }

    // 사용자가 선택한 카카오 후보를 다시 조회·검증한 뒤 즉시 앵커로 저장한다.
    public SingleAnchorResponse selectAnchor(SingleAnchorRequest request) {
        Place existing = placeRepository.findByProviderAndProviderPlaceId(
                        PlaceProvider.KAKAO,
                        request.providerPlaceId()
                )
                .orElse(null);
        if (existing != null) {
            return new SingleAnchorResponse(
                    existing.getId(),
                    false,
                    SinglePlaceCandidateResponse.from(existing)
            );
        }

        // 프론트가 전달한 후보 정보를 신뢰하지 않고 카카오 응답의 providerPlaceId로 다시 확인한다.
        KakaoPlaceDocument document = searchKakao(request.query(), request.latitude(), request.longitude()).stream()
                .filter(candidate -> request.providerPlaceId().equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 카카오 장소를 다시 찾을 수 없습니다."));
        Area area = resolveArea(document);
        PlaceType placeType = placeTypeClassifier.classify(
                defaultPlaceType(document),
                document.categoryName(),
                document.placeName(),
                placeTypeClassifier.findActiveRules()
        );
        SingleAnchorWriter.Result result = writer.save(area, document, placeType);
        return new SingleAnchorResponse(
                result.place().getId(),
                result.enrichmentQueued(),
                SinglePlaceCandidateResponse.from(result.place())
        );
    }

    // 사용자 위치를 중심으로 만든 작은 사각형에서 카카오 장소를 조회한다.
    private List<KakaoPlaceDocument> searchKakao(
            String query,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        Bounds bounds = bounds(latitude, longitude, SEARCH_RADIUS_METERS);
        return kakaoLocalClient.searchKeyword(new KakaoKeywordSearchRequest(
                query,
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
                SEARCH_SIZE,
                1
        )).documents();
    }

    // 카카오 지번 주소로 서울 서비스 Area를 결정한다.
    private Area resolveArea(KakaoPlaceDocument document) {
        String areaCode = legalDongAreaResolver.resolveAreaCode(document.addressName());
        if (areaCode == null) {
            throw new IllegalArgumentException("현재 서비스 Area에 속하지 않는 장소입니다.");
        }
        return areaRepository.findByCode(areaCode).orElseThrow();
    }

    // 카카오 그룹 코드만으로 명확한 경우만 기본 타입을 정하고 나머지는 활동으로 처리한다.
    private PlaceType defaultPlaceType(KakaoPlaceDocument document) {
        if ("CE7".equals(document.categoryGroupCode())) {
            return PlaceType.CAFE;
        }
        if ("FD6".equals(document.categoryGroupCode())) {
            return PlaceType.MEAL;
        }
        return PlaceType.ACTIVITY;
    }

    // 위도·경도 기반 직선거리로 가까운 DB 후보만 남긴다.
    private double distanceMeters(Place place, BigDecimal latitude, BigDecimal longitude) {
        double latitudeDistance = Math.toRadians(place.getLatitude().subtract(latitude).doubleValue());
        double longitudeDistance = Math.toRadians(place.getLongitude().subtract(longitude).doubleValue());
        double latitudeRadian = Math.toRadians(latitude.doubleValue());
        double placeLatitudeRadian = Math.toRadians(place.getLatitude().doubleValue());
        double haversine = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(latitudeRadian) * Math.cos(placeLatitudeRadian)
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    // 미터 기준 반경을 카카오 rect 파라미터용 위도·경도 사각형으로 변환한다.
    private Bounds bounds(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        double latitudeOffset = radiusMeters / 111_000d;
        double longitudeOffset = radiusMeters / (111_000d * Math.cos(Math.toRadians(latitude.doubleValue())));
        return new Bounds(
                latitude.subtract(BigDecimal.valueOf(latitudeOffset)).setScale(7, RoundingMode.HALF_UP),
                latitude.add(BigDecimal.valueOf(latitudeOffset)).setScale(7, RoundingMode.HALF_UP),
                longitude.subtract(BigDecimal.valueOf(longitudeOffset)).setScale(7, RoundingMode.HALF_UP),
                longitude.add(BigDecimal.valueOf(longitudeOffset)).setScale(7, RoundingMode.HALF_UP)
        );
    }

    private record Bounds(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
    }
}
