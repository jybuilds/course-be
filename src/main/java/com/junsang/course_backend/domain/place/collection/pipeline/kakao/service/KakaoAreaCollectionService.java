package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionJobStatus;
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
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// City의 Area를 순회하며 카카오 장소를 수집하고 중복 없이 갱신한다.
@Service
@RequiredArgsConstructor
public class KakaoAreaCollectionService {
    private static final int PAGE_SIZE = 15;
    private static final int BINARY_SPLIT_THRESHOLD_METERS = 500;
    // 기본 Area 검색은 유지하고, 포화 셀만 약 50m까지 분할한다.
    private static final int MIN_RECT_METERS = 50;
    private final CityRepository cityRepository;
    private final AreaRepository areaRepository;
    private final PlaceCollectionTempRepository tempRepository;
    private final PlaceCollectionProfileRepository profileRepository;
    private final PlaceCollectionJobRepository jobRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final LegalDongAreaResolver legalDongAreaResolver;
    private final PlaceTypeClassifier placeTypeClassifier;

    // City의 모든 Area와 활성 프로필 조합으로 초기 Job을 만들고 즉시 처리한다.
    public void collectCity(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new IllegalArgumentException("도시를 찾을 수 없습니다: " + cityId);
        }

        List<PlaceCollectionProfile> profiles = profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        List<PlaceCategoryRule> rules = placeTypeClassifier.findActiveRules();
        for (Area area : areaRepository.findByCityIdOrderById(cityId)) {
            collectArea(area, profiles, rules);
        }
    }

    // 지정한 Area의 활성 카카오 수집 프로필을 순서대로 실행한다.
    public void collectArea(Long areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 찾을 수 없습니다: " + areaId));
        List<PlaceCollectionProfile> profiles = profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        List<PlaceCategoryRule> rules = placeTypeClassifier.findActiveRules();

        collectArea(area, profiles, rules);
    }

    // 저장하지 않고 지정한 Area에서 카카오 키워드 검색 원본을 반환한다.
    public KakaoKeywordSearchResponse searchKeyword(Long areaId, String query, int page) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 찾을 수 없습니다: " + areaId));
        return kakaoLocalClient.searchKeyword(new KakaoKeywordSearchRequest(
                query,
                area.getCollectionMinLongitude(),
                area.getCollectionMinLatitude(),
                area.getCollectionMaxLongitude(),
                area.getCollectionMaxLatitude(),
                PAGE_SIZE,
                page
        ));
    }

    // 저장하지 않고 지정한 Area에서 카카오 카테고리 검색 원본을 반환한다.
    public KakaoKeywordSearchResponse searchCategory(Long areaId, String categoryGroupCode, int page) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 찾을 수 없습니다: " + areaId));
        return kakaoLocalClient.searchCategory(new KakaoCategorySearchRequest(
                categoryGroupCode,
                area.getCollectionMinLongitude(),
                area.getCollectionMinLatitude(),
                area.getCollectionMaxLongitude(),
                area.getCollectionMaxLatitude(),
                PAGE_SIZE,
                page
        ));
    }

    // 이미 실행 중인 Job은 건너뛰고 새 초기 Job만 생성한다.
    private void collectArea(
            Area area,
            List<PlaceCollectionProfile> profiles,
            List<PlaceCategoryRule> rules
    ) {
        for (PlaceCollectionProfile profile : profiles) {
            if (jobRepository.existsByAreaIdAndCollectionProfileIdAndStatusIn(
                    area.getId(), profile.getId(), List.of(CollectionJobStatus.READY, CollectionJobStatus.RUNNING))) {
                continue;
            }

            process(jobRepository.save(PlaceCollectionJob.initial(area, profile)), rules);
        }
    }

    // Job 하나를 끝까지 처리하며 포화된 경우에만 자식 Job으로 내려간다.
    private void process(PlaceCollectionJob job, List<PlaceCategoryRule> rules) {
        try {
            job.start();
            jobRepository.save(job);
            KakaoKeywordSearchResponse first = search(job, 1);
            job.recordSearchMeta(first.meta().totalCount(), first.meta().pageableCount());
            jobRepository.save(job);

            // 포화 상태.
            if (first.meta().totalCount() > first.meta().pageableCount()) {
                if (isMinimum(job)) {
                    saveDocuments(first.documents(), job, rules);
                    job.partial("RESULT_LIMIT_EXCEEDED_AT_MIN_RECT");
                    jobRepository.save(job);
                    return;
                }

                // 500m 초과는 4등분하고, 그 이하부터는 긴 축 기준으로 2등분한다.
                for (PlaceCollectionJob child : job.split(isAtMost(job, BINARY_SPLIT_THRESHOLD_METERS))) {
                    process(jobRepository.save(child), rules);
                }
                jobRepository.save(job);
                return;
            }
            saveDocuments(first.documents(), job, rules);
            for (int page = 2; !first.meta().isEnd() && page <= 45; page++) {
                KakaoKeywordSearchResponse response = search(job, page);
                saveDocuments(response.documents(), job, rules);
                if (response.meta().isEnd()) {
                    break;
                }
            }
            job.complete();
            jobRepository.save(job);
        } catch (RuntimeException exception) {
            job.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            jobRepository.save(job);
        }
    }

    // Job 생성 시점에 저장한 검색 방식과 조건 스냅샷으로 카카오 API를 호출한다.
    private KakaoKeywordSearchResponse search(PlaceCollectionJob job, int page) {
        // 카테고리 검색
        if (job.getSearchType() == CollectionSearchType.CATEGORY) {
            return kakaoLocalClient.searchCategory(new KakaoCategorySearchRequest(
                    job.getCategoryGroupCode(), job.getMinLongitude(), job.getMinLatitude(),
                    job.getMaxLongitude(), job.getMaxLatitude(), PAGE_SIZE, page
            ));
        }

        // 키워드 검색
        return kakaoLocalClient.searchKeyword(new KakaoKeywordSearchRequest(
                job.getQuery(), job.getMinLongitude(), job.getMinLatitude(),
                job.getMaxLongitude(), job.getMaxLatitude(), PAGE_SIZE, page
        ));
    }

    // 카카오 응답을 법정동 기준 Area에 연결하고 Temp에 생성 또는 갱신한다.
    private void saveDocuments(
            List<KakaoPlaceDocument> documents,
            PlaceCollectionJob job,
            List<PlaceCategoryRule> rules
    ) {
        for (KakaoPlaceDocument document : documents) {
            String areaCode = legalDongAreaResolver.resolveAreaCode(document.addressName());
            if (areaCode == null || document.id() == null || document.x() == null || document.y() == null) {
                continue;
            }
            Area area = areaRepository.findByCode(areaCode).orElseThrow();
            PlaceType placeType = placeTypeClassifier.classify(
                    job.getPlaceType(),
                    document.categoryName(),
                    document.placeName(),
                    rules
            );
            tempRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, document.id()).ifPresentOrElse(
                    temp -> {
                        temp.refreshKakao(
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
                                document.phone()
                        );
                        tempRepository.save(temp);
                    },
                    () -> tempRepository.save(PlaceCollectionTemp.create(
                            area,
                            PlaceProvider.KAKAO,
                            document.id(),
                            placeType,
                            document.placeName(),
                            document.addressName(),
                            document.roadAddressName(),
                            document.categoryName(),
                            document.categoryGroupCode(),
                            document.y(),
                            document.x(),
                            document.placeUrl(),
                            document.phone()
                    ))
            );
        }
    }

    // 좌표로 가로, 세로 길이 계산후 MIN_RECT_METERS 보다 작은지 판단.
    private boolean isMinimum(PlaceCollectionJob job) {
        return isAtMost(job, MIN_RECT_METERS);
    }

    // 현재 rect의 긴 변이 지정한 미터 이하인지 판단한다.
    private boolean isAtMost(PlaceCollectionJob job, int meters) {
        double height = job.getMaxLatitude().subtract(job.getMinLatitude()).doubleValue() * 111_000;
        double width = job.getMaxLongitude().subtract(job.getMinLongitude()).doubleValue() * 111_000 * Math.cos(Math.toRadians(job.getMinLatitude().add(job.getMaxLatitude()).doubleValue() / 2));

        return Math.max(width, height) <= meters;
    }
}
