package com.junsang.course_backend.domain.place.collection.naver.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.naver.dto.PlaceRefinementResponse;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.infra.naver.NaverLocalClient;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalItem;
import com.junsang.course_backend.infra.naver.dto.response.NaverLocalSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/// Temp 장소를 네이버 지역 검색으로 1차 정제하고 AI 태깅 대기로 전환한다.
@Service
@RequiredArgsConstructor
public class PlaceRefinementService {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final PlaceCollectionTempRepository tempRepository;
    private final NaverLocalClient naverLocalClient;
    private final NaverPlaceMatcher naverPlaceMatcher;
    private final NaverPlaceNormalizer naverPlaceNormalizer;
    private final NaverSearchUrlCreator naverSearchUrlCreator;

    // Temp 한 건을 네이버로 정제하고 결과 상태를 반환한다.
    public PlaceRefinementResponse refine(Long tempId) {
        PlaceCollectionTemp temp = findTemp(tempId);
        if (!canRefine(temp)) {
            return PlaceRefinementResponse.invalidStep(temp);
        }

        try {
            temp.startProcessing();
            tempRepository.save(temp);
            NaverLocalItem matched = findMatchedPlace(temp);
            temp.completeNaverEnrichment(
                    naverPlaceNormalizer.cleanTitle(matched.title()),
                    naverSearchUrlCreator.create(temp.getName(), temp.getAddressName()),
                    matched.category(),
                    matched.address(),
                    matched.roadAddress()
            );
            tempRepository.save(temp);
        } catch (PossibleRelocationException exception) {
            recordPossibleRelocation(temp, exception.candidate());
        } catch (PlaceRefinementException exception) {
            fail(temp, exception.getErrorCode(), exception.getMessage());
        } catch (BusinessException exception) {
            fail(temp, PlaceRefinementErrorCode.NAVER_API_REQUEST_FAILED, exception.getMessage());
        } catch (RuntimeException exception) {
            fail(temp, PlaceRefinementErrorCode.UNEXPECTED_ERROR, messageOf(exception));
        }
        return PlaceRefinementResponse.from(temp);
    }

    // 대기 중인 네이버 정제 대상을 ID 순서대로 제한 개수만 처리한다.
    public PlaceRefinementBatchResponse refinePending(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("정제 개수는 1 이상 1000 이하여야 합니다.");
        }
        List<PlaceCollectionTemp> targets = tempRepository.findByProcessingStepAndStatusOrderByIdAsc(
                PlaceCollectionStep.NAVER_ENRICHMENT,
                PlaceCollectionTempStatus.PENDING,
                PageRequest.of(0, limit)
        );
        int successCount = 0;
        for (PlaceCollectionTemp target : targets) {
            if (refine(target.getId()).success()) {
                successCount++;
            }
        }
        return new PlaceRefinementBatchResponse(
                limit,
                targets.size(),
                successCount,
                targets.size() - successCount
        );
    }

    // 장소명에 지번주소의 동명을 보완해 검색하고, 실패하면 전체 주소로 한 번 더 검색한다.
    private NaverLocalItem findMatchedPlace(PlaceCollectionTemp temp) {
        NaverLocalSearchResponse firstResponse = naverLocalClient.search(
                naverSearchUrlCreator.createQuery(temp.getName(), temp.getAddressName())
        );
        try {
            return naverPlaceMatcher.match(temp, firstResponse.items());
        } catch (PlaceRefinementException firstException) {
            NaverLocalItem candidate = naverPlaceMatcher.findPossibleRelocation(temp, firstResponse.items());
            try {
                return naverPlaceMatcher.match(temp, naverLocalClient.search(createAddressQuery(temp)).items());
            } catch (PlaceRefinementException secondException) {
                if (candidate != null) {
                    throw new PossibleRelocationException(candidate);
                }
                throw preferredFailure(firstException, secondException);
            }
        }
    }

    // 이전 또는 다른 지점일 수 있는 네이버 후보를 최종 Place로 만들지 않고 Temp에 남긴다.
    private void recordPossibleRelocation(PlaceCollectionTemp temp, NaverLocalItem candidate) {
        temp.recordPossibleRelocation(
                naverPlaceNormalizer.cleanTitle(candidate.title()),
                naverSearchUrlCreator.create(
                        naverPlaceNormalizer.cleanTitle(candidate.title()),
                        candidate.address()
                ),
                candidate.category(),
                candidate.address(),
                candidate.roadAddress()
        );
        tempRepository.save(temp);
    }

    // 첫 검색에 후보가 있었지만 주소가 다르면, 보조 검색의 빈 결과보다 그 실패 원인을 남긴다.
    private PlaceRefinementException preferredFailure(
            PlaceRefinementException firstException,
            PlaceRefinementException secondException
    ) {
        return firstException.getErrorCode() == PlaceRefinementErrorCode.NAVER_RESULT_NOT_FOUND
                ? secondException
                : firstException;
    }

    // 장소명 검색 보완용으로 도로명주소를 우선 붙인 검색어를 만든다.
    private String createAddressQuery(PlaceCollectionTemp temp) {
        String address = firstNonBlank(temp.getRoadAddressName(), temp.getAddressName());
        if (address == null) {
            throw new PlaceRefinementException(
                    PlaceRefinementErrorCode.NAVER_PLACE_NOT_MATCHED,
                    "네이버 검색에 사용할 카카오 주소가 없습니다."
            );
        }
        return temp.getName() + " " + address;
    }

    // 우선값이 비어 있으면 보조값을 반환한다.
    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    // 네이버 정제를 시작할 수 있는 단계와 상태인지 확인한다.
    private boolean canRefine(PlaceCollectionTemp temp) {
        return temp.getProcessingStep() == PlaceCollectionStep.NAVER_ENRICHMENT
                && (temp.getStatus() == PlaceCollectionTempStatus.PENDING
                || temp.getStatus() == PlaceCollectionTempStatus.FAILED);
    }

    // Temp를 조회하고 존재하지 않으면 입력 오류로 처리한다.
    private PlaceCollectionTemp findTemp(Long tempId) {
        return tempRepository.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("정제할 임시 장소를 찾을 수 없습니다: " + tempId));
    }

    // 실패 원인과 상태를 Temp에 저장한다.
    private void fail(
            PlaceCollectionTemp temp,
            PlaceRefinementErrorCode errorCode,
            String errorMessage
    ) {
        temp.fail(errorCode, errorMessage);
        tempRepository.save(temp);
    }

    // 비어 있는 예외 메시지 대신 예외 이름을 기록한다.
    private String messageOf(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static class PossibleRelocationException extends RuntimeException {

        private final NaverLocalItem candidate;

        private PossibleRelocationException(NaverLocalItem candidate) {
            this.candidate = candidate;
        }

        private NaverLocalItem candidate() {
            return candidate;
        }
    }
}
