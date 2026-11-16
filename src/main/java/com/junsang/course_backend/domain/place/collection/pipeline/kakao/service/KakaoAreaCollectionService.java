package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.collection.budget.CollectionDeferredException;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.CollectionSearchType;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 등록된 카카오 구역 Job을 실행하고 포화 구역을 분할한다.
@Service
@RequiredArgsConstructor
public class KakaoAreaCollectionService {
    private static final int PAGE_SIZE = 15;
    private static final int BINARY_SPLIT_THRESHOLD_METERS = 500;
    // 기본 Area 검색은 유지하고, 포화 셀만 약 50m까지 분할한다.
    private static final int MIN_RECT_METERS = 50;
    private final PlaceCollectionJobRepository jobRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final PlaceTypeClassifier placeTypeClassifier;
    private final KakaoPlaceWriter placeWriter;
    private final KakaoCollectionJobWriter jobWriter;
    private final KakaoCollectionErrorHandler errorHandler;

    private void processTree(PlaceCollectionJob job, List<PlaceCategoryRule> rules, java.util.function.BooleanSupplier canContinue) {
        if (job.getStatus() == CollectionJobStatus.SPLIT) {
            jobRepository.findByParentJobIdOrderByIdAsc(job.getId()).forEach(child -> processTree(child, rules, canContinue));
        } else {
            process(job, rules, canContinue);
        }
    }

    public void resumePendingJobs(int limit, java.util.function.BooleanSupplier canContinue) {
        var rules = placeTypeClassifier.findActiveRules();
        for (var job : jobRepository.findRunnable(java.time.LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, limit))) {
            if (!canContinue.getAsBoolean()) return;
            try { process(job, rules, canContinue); }
            catch (CollectionDeferredException waiting) { return; }
        }
    }

    private void checkCapacity(java.util.function.BooleanSupplier canContinue) {
        if (!canContinue.getAsBoolean()) throw new CollectionDeferredException(java.time.Instant.now().plusSeconds(60));
    }

    private void process(PlaceCollectionJob job, List<PlaceCategoryRule> rules, java.util.function.BooleanSupplier canContinue) {
        job = jobWriter.tryStart(job.getId());
        if (job == null) return;
        try {
            checkCapacity(canContinue);
            KakaoKeywordSearchResponse first = search(job, 1);
            job.recordSearchMeta(first.meta().totalCount(), first.meta().pageableCount());
            jobRepository.save(job);

            boolean saturated = first.meta().totalCount() > first.meta().pageableCount();
            if (saturated && !isMinimum(job)) {
                // 500m 초과는 4등분하고, 그 이하부터는 긴 축 기준으로 2등분한다.
                for (PlaceCollectionJob child : jobWriter.split(job.getId(), isAtMost(job, BINARY_SPLIT_THRESHOLD_METERS))) {
                    checkCapacity(canContinue);
                    processTree(child, rules, canContinue);
                }
                return;
            }
            placeWriter.saveDocuments(first.documents(), job, rules);
            for (int page = 2; !first.meta().isEnd() && page <= 45; page++) {
                checkCapacity(canContinue);
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
        } catch (CollectionDeferredException waiting) {
            jobWriter.defer(job.getId(), java.time.LocalDateTime.ofInstant(waiting.retryAt(), java.time.ZoneId.systemDefault()));
            throw waiting;
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
