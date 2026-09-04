package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.entity.NaverBlogCollectionStatus;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 네이버 정제 상태 변경만 짧은 트랜잭션으로 저장한다.
@Service
@RequiredArgsConstructor
public class NaverRefinementWriter {

    private final PlaceCollectionTempRepository tempRepository;

    // Temp를 읽기 전용 트랜잭션으로 조회한다.
    @Transactional(readOnly = true)
    public PlaceCollectionTemp find(Long tempId) {
        return findTemp(tempId);
    }

    // 외부 API 호출 전에 처리 상태를 저장한다.
    @Transactional
    public PlaceCollectionTemp start(Long tempId) {
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.startProcessing();
        return temp;
    }

    // 지역·블로그 검색이 끝난 네이버 정제 결과를 저장한다.
    @Transactional
    public PlaceCollectionTemp complete(
            Long tempId,
            PlaceType placeType,
            boolean placeTypeFinalized,
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress,
            String blogEvidence,
            NaverBlogCollectionStatus blogStatus,
            String blogErrorMessage
    ) {
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.completeNaverEnrichment(
                placeType,
                placeTypeFinalized,
                title,
                searchUrl,
                category,
                address,
                roadAddress,
                blogEvidence,
                blogStatus,
                blogErrorMessage
        );
        return temp;
    }

    // 이전 의심 후보를 검토용으로 저장한다.
    @Transactional
    public PlaceCollectionTemp recordPossibleRelocation(
            Long tempId,
            String title,
            String searchUrl,
            String category,
            String address,
            String roadAddress
    ) {
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.recordPossibleRelocation(title, searchUrl, category, address, roadAddress);
        return temp;
    }

    // 현재 정제 단계의 실패 상태를 저장한다.
    @Transactional
    public PlaceCollectionTemp fail(
            Long tempId,
            PlaceRefinementErrorCode errorCode,
            String errorMessage
    ) {
        PlaceCollectionTemp temp = findTemp(tempId);
        temp.fail(errorCode, errorMessage);
        return temp;
    }

    private PlaceCollectionTemp findTemp(Long tempId) {
        return tempRepository.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("정제할 임시 장소를 찾을 수 없습니다: " + tempId));
    }
}
