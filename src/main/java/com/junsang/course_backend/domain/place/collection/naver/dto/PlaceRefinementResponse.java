package com.junsang.course_backend.domain.place.collection.naver.dto;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionStep;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;

/// 네이버 단건 정제 결과다.
public record PlaceRefinementResponse(
        Long tempId,
        boolean success,
        PlaceCollectionStep processingStep,
        PlaceCollectionTempStatus status,
        String naverTitle,
        String naverSearchUrl,
        PlaceRefinementErrorCode errorCode,
        String errorMessage
) {

    // 저장된 Temp 상태를 API 응답으로 변환한다.
    public static PlaceRefinementResponse from(PlaceCollectionTemp temp) {
        boolean success = temp.getProcessingStep() == PlaceCollectionStep.AI_TAGGING
                && temp.getStatus() == PlaceCollectionTempStatus.PENDING;
        return new PlaceRefinementResponse(
                temp.getId(),
                success,
                temp.getProcessingStep(),
                temp.getStatus(),
                temp.getNaverTitle(),
                temp.getNaverSearchUrl(),
                temp.getErrorCode(),
                temp.getErrorMessage()
        );
    }

    // 현재 데이터를 변경하지 않고 잘못된 단계 요청을 반환한다.
    public static PlaceRefinementResponse invalidStep(PlaceCollectionTemp temp) {
        return new PlaceRefinementResponse(
                temp.getId(),
                false,
                temp.getProcessingStep(),
                temp.getStatus(),
                temp.getNaverTitle(),
                temp.getNaverSearchUrl(),
                PlaceRefinementErrorCode.INVALID_PROCESSING_STEP,
                "네이버 정제를 실행할 수 없는 단계 또는 상태입니다."
        );
    }
}
