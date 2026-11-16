package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionSearchType;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionProfile;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionProfileRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
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
    private final PlaceCollectionProfileRepository profileRepository;
    private final PlaceCollectionJobRepository jobRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final PlaceTypeClassifier placeTypeClassifier;
    private final KakaoPlaceWriter placeWriter;
    private final KakaoCollectionJobWriter jobWriter;
    private final KakaoCollectionErrorHandler errorHandler;

    // City의 모든 Area와 활성 프로필 조합으로 초기 Job을 만들고 즉시 처리한다.
    public void collectCity(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new IllegalArgumentException("도시를 찾을 수 없습니다: " + cityId);
        }

        List<PlaceCollectionProfile> profiles = profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        List<PlaceCategoryRule> rules = placeTypeClassifier.findActiveRules();
        List<PlaceCollectionJob> roots = jobWriter.initialize(areaRepository.findByCityIdOrderById(cityId), profiles);
        roots.forEach(job -> processTree(job, rules));
    }

    // 지정한 Area의 활성 카카오 수집 프로필을 순서대로 실행한다.
    public void collectArea(Long areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 찾을 수 없습니다: " + areaId));
        List<PlaceCollectionProfile> profiles = profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        List<PlaceCategoryRule> rules = placeTypeClassifier.findActiveRules();

        collectArea(area, profiles, rules);
    }

    // 이미 실행 중인 Job은 건너뛰고 새 초기 Job만 생성한다.
    private void collectArea(
            Area area,
            List<PlaceCollectionProfile> profiles,
            List<PlaceCategoryRule> rules
    ) {
        jobWriter.initialize(List.of(area), profiles).forEach(job -> processTree(job, rules));
    }

    private void processTree(PlaceCollectionJob job, List<PlaceCategoryRule> rules) {
        if (job.getStatus() == CollectionJobStatus.SPLIT) {
            jobRepository.findByParentJobIdOrderByIdAsc(job.getId()).forEach(child -> processTree(child, rules));
        } else {
            process(job, rules);
        }
    }

    // 최초 수집 일정과 별개로 이미 생성된 미완료 작업만 회수한다.
    public void resumePendingJobs(int limit) {
        List<PlaceCategoryRule> rules = placeTypeClassifier.findActiveRules();
        jobRepository.findRunnable(java.time.LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, limit))
                .forEach(job -> process(job, rules));
    }

    // Job 하나를 끝까지 처리하며 포화된 경우에만 자식 Job으로 내려간다.
    private void process(PlaceCollectionJob job, List<PlaceCategoryRule> rules) {
        job = jobWriter.tryStart(job.getId());
        if (job == null) return;
        try {
            KakaoKeywordSearchResponse first = search(job, 1);
            job.recordSearchMeta(first.meta().totalCount(), first.meta().pageableCount());
            jobRepository.save(job);

            boolean saturated = first.meta().totalCount() > first.meta().pageableCount();
            if (saturated && !isMinimum(job)) {
                // 500m 초과는 4등분하고, 그 이하부터는 긴 축 기준으로 2등분한다.
                for (PlaceCollectionJob child : jobWriter.split(job.getId(), isAtMost(job, BINARY_SPLIT_THRESHOLD_METERS))) {
                    processTree(child, rules);
                }
                return;
            }
            placeWriter.saveDocuments(first.documents(), job, rules);
            for (int page = 2; !first.meta().isEnd() && page <= 45; page++) {
                KakaoKeywordSearchResponse response = search(job, page);
                placeWriter.saveDocuments(response.documents(), job, rules);
                if (response.meta().isEnd()) {
                    break;
                }
            }
            if (saturated) {
                job.partial(
                        PlaceRefinementErrorCode.KAKAO_RESULT_LIMIT_EXCEEDED,
                        "RESULT_LIMIT_EXCEEDED_AT_MIN_RECT"
                );
            } else {
                job.complete();
            }
            jobRepository.save(job);
        } catch (RuntimeException exception) {
            errorHandler.handle(job.getId(), exception);
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
