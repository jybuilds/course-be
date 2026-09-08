package com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.service;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.CollectionJobStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.entity.CollectionSearchType;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.entity.PlaceCollectionProfile;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.repository.PlaceCollectionProfileRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.LegalDongAreaResolver;
import com.junsang.course_backend.domain.place.collection.pipeline.common.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.infra.kakao.KakaoLocalClient;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoCategorySearchRequest;
import com.junsang.course_backend.infra.kakao.dto.request.KakaoKeywordSearchRequest;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        // 프로필별 exists 조회 대신 Area의 실행 중 Job을 한 번에 읽어 City 수집의 N+1을 줄인다.
        Set<Long> runningProfileIds = new HashSet<>(jobRepository.findCollectionProfileIdsByAreaIdAndStatusIn(
                area.getId(),
                List.of(CollectionJobStatus.READY, CollectionJobStatus.RUNNING)
        ));
        for (PlaceCollectionProfile profile : profiles) {
            if (runningProfileIds.contains(profile.getId())) {
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
                    job.partial(
                            PlaceRefinementErrorCode.KAKAO_RESULT_LIMIT_EXCEEDED,
                            "RESULT_LIMIT_EXCEEDED_AT_MIN_RECT"
                    );
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
        } catch (BusinessException exception) {
            // 카카오 클라이언트가 이미 분류한 외부 API 오류를 Job 조회용 코드로 보존한다.
            job.fail(kakaoErrorCode(exception), exception.getMessage());
            jobRepository.save(job);
        } catch (RuntimeException exception) {
            // 카카오 응답 후 저장·분할 중 발생한 예외는 수집 처리 오류로 남긴다.
            job.fail(
                    PlaceRefinementErrorCode.KAKAO_COLLECTION_PROCESSING_FAILED,
                    messageOf(exception)
            );
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
        List<ResolvedDocument> resolvedDocuments = documents.stream()
                .map(document -> new ResolvedDocument(
                        document,
                        legalDongAreaResolver.resolveAreaCode(document.addressName())
                ))
                .filter(ResolvedDocument::isCollectable)
                .toList();
        if (resolvedDocuments.isEmpty()) {
            return;
        }

        // 한 페이지의 Area·기존 Temp를 한 번에 읽어 장소마다 발생하던 조회 N+1을 막는다.
        Map<String, Area> areasByCode = findAreasByCode(resolvedDocuments);
        Map<String, PlaceCollectionTemp> tempsByProviderPlaceId = findTempsByProviderPlaceId(resolvedDocuments);
        List<PlaceCollectionTemp> tempsToSave = new ArrayList<>();
        for (ResolvedDocument resolved : resolvedDocuments) {
            KakaoPlaceDocument document = resolved.document();
            Area area = areasByCode.get(resolved.areaCode());
            if (area == null) {
                throw new IllegalStateException("카카오 주소에 매핑된 Area를 찾을 수 없습니다: " + resolved.areaCode());
            }
            PlaceTypeClassifier.PlaceTypeClassification classification = placeTypeClassifier.classifyForCollection(
                    job.getSearchType(),
                    job.getPlaceType(),
                    document.categoryName(),
                    document.placeName(),
                    rules
            );
            PlaceCollectionTemp temp = tempsByProviderPlaceId.get(document.id());
            if (temp != null) {
                temp.refreshKakao(
                        area,
                        classification.placeType(),
                        classification.finalized(),
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
            } else {
                temp = PlaceCollectionTemp.create(
                        area,
                        PlaceProvider.KAKAO,
                        document.id(),
                        classification.placeType(),
                        classification.finalized(),
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
                tempsByProviderPlaceId.put(document.id(), temp);
            }
            tempsToSave.add(temp);
        }
        tempRepository.saveAll(tempsToSave);
    }

    // 페이지에 등장한 Area 코드만 IN 조회해 주소마다 Area를 다시 조회하지 않는다.
    private Map<String, Area> findAreasByCode(List<ResolvedDocument> documents) {
        return areaRepository.findByCodeIn(distinctAreaCodes(documents)).stream()
                .collect(Collectors.toMap(Area::getCode, Function.identity()));
    }

    // 카카오 provider ID 목록으로 기존 Temp를 한 번에 조회한다.
    private Map<String, PlaceCollectionTemp> findTempsByProviderPlaceId(List<ResolvedDocument> documents) {
        return tempRepository.findByProviderAndProviderPlaceIdIn(
                        PlaceProvider.KAKAO,
                        distinctProviderPlaceIds(documents)
                )
                .stream()
                .collect(Collectors.toMap(PlaceCollectionTemp::getProviderPlaceId, Function.identity()));
    }

    private Set<String> distinctAreaCodes(List<ResolvedDocument> documents) {
        return documents.stream()
                .map(ResolvedDocument::areaCode)
                .collect(Collectors.toSet());
    }

    private Set<String> distinctProviderPlaceIds(List<ResolvedDocument> documents) {
        return documents.stream()
                .map(resolved -> resolved.document().id())
                .collect(Collectors.toSet());
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

    // 인프라 HTTP 오류를 운영 조회용 수집 오류 코드로 변환한다.
    private PlaceRefinementErrorCode kakaoErrorCode(BusinessException exception) {
        return switch (exception.getErrorCode()) {
            case KAKAO_API_KEY_NOT_CONFIGURED -> PlaceRefinementErrorCode.KAKAO_API_KEY_NOT_CONFIGURED;
            case KAKAO_LOCAL_API_EMPTY_RESPONSE -> PlaceRefinementErrorCode.KAKAO_LOCAL_API_EMPTY_RESPONSE;
            case KAKAO_LOCAL_API_REQUEST_FAILED -> PlaceRefinementErrorCode.KAKAO_LOCAL_API_REQUEST_FAILED;
            default -> PlaceRefinementErrorCode.KAKAO_COLLECTION_PROCESSING_FAILED;
        };
    }

    // 비어 있는 예외 메시지 대신 예외 클래스명을 남긴다.
    private String messageOf(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record ResolvedDocument(KakaoPlaceDocument document, String areaCode) {

        private boolean isCollectable() {
            return areaCode != null
                    && document.id() != null
                    && document.x() != null
                    && document.y() != null;
        }
    }
}
