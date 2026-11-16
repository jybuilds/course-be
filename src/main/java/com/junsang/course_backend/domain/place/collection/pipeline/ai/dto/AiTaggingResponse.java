package com.junsang.course_backend.domain.place.collection.pipeline.ai.dto;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTempStatus;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;

/// 장소 한 건의 AI 태깅과 최종 저장 결과다.
public record AiTaggingResponse(
        Long tempId,
        boolean success,
        Long placeId,
        PlaceCollectionTempStatus status,
        PlaceRefinementErrorCode errorCode,
        String errorMessage
) {

    // 성공한 Temp와 최종 Place ID를 응답으로 변환한다.
    public static AiTaggingResponse completed(PlaceCollectionTemp temp, Long placeId) {
        return new AiTaggingResponse(
                temp.getId(),
                true,
                placeId,
                temp.getStatus(),
                null,
                null
        );
    }

    // 실패하거나 실행할 수 없는 Temp 상태를 응답으로 변환한다.
    public static AiTaggingResponse failed(PlaceCollectionTemp temp) {
        return new AiTaggingResponse(
                temp.getId(),
                false,
                null,
                temp.getStatus(),
                temp.getErrorCode(),
                temp.getErrorMessage()
        );
    }

    // 현재 상태를 변경하지 않고 실행 불가능한 요청임을 반환한다.
    public static AiTaggingResponse invalidStep(PlaceCollectionTemp temp) {
        return new AiTaggingResponse(
                temp.getId(),
                false,
                null,
                temp.getStatus(),
                PlaceRefinementErrorCode.INVALID_PROCESSING_STEP,
                "AI 태깅을 실행할 수 없는 단계 또는 상태입니다."
        );
    }
}
