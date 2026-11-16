package com.junsang.course_backend.domain.place.collection.pipeline.support.error;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import java.time.LocalDateTime;

/** 분류한 오류와 공급자가 명시한 재시도 하한 시각. 오류 원문 대신 안전한 요약을 저장한다. */
public record CollectionFailure(PlaceRefinementErrorCode code, String message, LocalDateTime retryHint) {
    public CollectionFailure(PlaceRefinementErrorCode code, String message) {
        this(code, message, null);
    }
}
