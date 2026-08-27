package com.junsang.course_backend.domain.place.collection;

import com.junsang.course_backend.domain.place.entity.*;
import com.junsang.course_backend.domain.place.repository.*;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import java.math.BigDecimal;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// City의 Area를 순회하며 카카오 장소를 수집하고 중복 없이 갱신한다.
@Service
@RequiredArgsConstructor
public class KakaoAreaCollectionService {
    private static final int PAGE_SIZE = 15;
    private static final int MIN_RECT_METERS = 500;
    private final CityRepository cityRepository;
    private final AreaRepository areaRepository;
    private final PlaceRepository placeRepository;
    private final PlaceCollectionProfileRepository profileRepository;
    private final PlaceCollectionJobRepository jobRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final LegalDongAreaResolver legalDongAreaResolver;

    public void collectCity(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new IllegalArgumentException("도시를 찾을 수 없습니다: " + cityId);
        }

        List<PlaceCollectionProfile> profiles = profileRepository.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO);
        for (Area area : areaRepository.findByCityIdOrderById(cityId)) {
            for (PlaceCollectionProfile profile : profiles) {
                if (jobRepository.existsByAreaIdAndCollectionProfileIdAndStatusIn(
                        area.getId(), profile.getId(), List.of(CollectionJobStatus.READY, CollectionJobStatus.RUNNING))) {
                    continue;
                }

                process(jobRepository.save(PlaceCollectionJob.initial(area, profile)));
            }
        }
    }

    // Job 하나를 끝까지 처리하며 포화된 경우에만 자식 Job으로 내려간다.
    private void process(PlaceCollectionJob job) {
        try {
            job.start();
            jobRepository.save(job);
            KakaoKeywordSearchResponse first = search(job, 1);

            // 포화 상태.
            if (first.meta().totalCount() > first.meta().pageableCount()) {
                if (isMinimum(job)) {
                    job.fail("RESULT_LIMIT_EXCEEDED_AT_MIN_RECT");
                    jobRepository.save(job);
                    return;
                }

                // 포화 상태면 자식 split해서 다시 process 진행.
                for (PlaceCollectionJob child : job.split()) {
                    process(jobRepository.save(child));
                }
                jobRepository.save(job);
                return;
            }
            saveDocuments(first.documents(), job);
            for (int page = 2; !first.meta().isEnd() && page <= 45; page++) {
                KakaoKeywordSearchResponse response = search(job, page);
                saveDocuments(response.documents(), job);
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

    private void saveDocuments(List<KakaoPlaceDocument> documents, PlaceCollectionJob job) {
        for (KakaoPlaceDocument document : documents) {
            String areaCode = legalDongAreaResolver.resolveAreaCode(document.addressName());
            if (areaCode == null || document.id() == null || document.x() == null || document.y() == null) {
                continue;
            }
            Area area = areaRepository.findByCode(areaCode).orElseThrow();
            placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.KAKAO, document.id()).ifPresentOrElse(
                    place -> place.refresh(area, job.getPlaceType(), document.placeName(), document.addressName(), document.roadAddressName(), document.y(), document.x(), document.placeUrl(), document.phone()),
                    () -> placeRepository.save(Place.create(PlaceProvider.KAKAO, document.id(), area, job.getPlaceType(), document.placeName(), document.addressName(), document.roadAddressName(), document.y(), document.x(), document.placeUrl(), document.phone(), false, null, null)));
        }
    }

    // 좌표로 가로, 세로 길이 계산후 MIN_RECT_METERS 보다 작은지 판단.
    private boolean isMinimum(PlaceCollectionJob job) {
        double height = job.getMaxLatitude().subtract(job.getMinLatitude()).doubleValue() * 111_000;
        double width = job.getMaxLongitude().subtract(job.getMinLongitude()).doubleValue() * 111_000 * Math.cos(Math.toRadians(job.getMinLatitude().add(job.getMaxLatitude()).doubleValue() / 2));

        return Math.max(width, height) <= MIN_RECT_METERS;
    }
}
