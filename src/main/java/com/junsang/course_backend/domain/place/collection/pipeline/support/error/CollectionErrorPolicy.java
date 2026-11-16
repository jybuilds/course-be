package com.junsang.course_backend.domain.place.collection.pipeline.support.error;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import java.time.LocalDateTime;
import java.util.Set;

/** 재시도 허용 목록과 간격의 단일 기준. null은 자동 재시도 금지다. */
public final class CollectionErrorPolicy {
    public static final Set<PlaceRefinementErrorCode> POLL_BLOCKED = Set.of(
            PlaceRefinementErrorCode.OPENAI_API_KEY_NOT_CONFIGURED,
            PlaceRefinementErrorCode.AI_API_AUTH_FAILED,
            PlaceRefinementErrorCode.AI_API_INVALID_REQUEST,
            PlaceRefinementErrorCode.AI_BATCH_PROVIDER_UNRESOLVED);
    public static final Set<PlaceRefinementErrorCode> AI_RETRYABLE = Set.of(
            PlaceRefinementErrorCode.OPENAI_BATCH_TEMPORARY_FAILURE,
            PlaceRefinementErrorCode.OPENAI_API_EMPTY_RESPONSE,
            PlaceRefinementErrorCode.AI_API_TEMPORARY_FAILURE,
            PlaceRefinementErrorCode.AI_API_RATE_LIMITED,
            PlaceRefinementErrorCode.AI_API_QUOTA_EXHAUSTED,
            PlaceRefinementErrorCode.AI_RESPONSE_PARSE_FAILED,
            PlaceRefinementErrorCode.AI_RESPONSE_TARGET_MISMATCH,
            PlaceRefinementErrorCode.AI_RESPONSE_MISSING,
            PlaceRefinementErrorCode.AI_RESPONSE_DUPLICATE,
            PlaceRefinementErrorCode.AI_RESPONSE_INCOMPLETE,
            PlaceRefinementErrorCode.AI_BATCH_OUTPUT_INVALID,
            PlaceRefinementErrorCode.AI_RESPONSE_INVALID,
            PlaceRefinementErrorCode.AI_TAG_VALIDATION_FAILED,
            PlaceRefinementErrorCode.AI_SCORE_VALIDATION_FAILED,
            PlaceRefinementErrorCode.AI_STORAGE_FAILED,
            PlaceRefinementErrorCode.AI_BATCH_EXPIRED,
            PlaceRefinementErrorCode.AI_BATCH_CANCELLED,
            PlaceRefinementErrorCode.AI_COLLECTION_INTERRUPTED);
    public static final Set<PlaceRefinementErrorCode> NAVER_RETRYABLE = Set.of(
            PlaceRefinementErrorCode.NAVER_STORAGE_FAILED,
            PlaceRefinementErrorCode.NAVER_API_TEMPORARY_FAILURE,
            PlaceRefinementErrorCode.NAVER_API_RATE_LIMITED,
            PlaceRefinementErrorCode.NAVER_API_QUOTA_EXHAUSTED);
    public static final Set<PlaceRefinementErrorCode> KAKAO_RETRYABLE = Set.of(
            PlaceRefinementErrorCode.COLLECTION_INTERRUPTED,
            PlaceRefinementErrorCode.KAKAO_STORAGE_FAILED,
            PlaceRefinementErrorCode.KAKAO_API_TEMPORARY_FAILURE,
            PlaceRefinementErrorCode.KAKAO_API_RATE_LIMITED,
            PlaceRefinementErrorCode.KAKAO_API_QUOTA_EXHAUSTED);

    public static LocalDateTime nextRetry(CollectionFailure failure, int attempts, LocalDateTime now) {
        var code = failure.code();
        boolean naver = NAVER_RETRYABLE.contains(code);
        boolean quota = code.name().endsWith("QUOTA_EXHAUSTED");
        if (!AI_RETRYABLE.contains(code) && !KAKAO_RETRYABLE.contains(code) && !naver) return null;
        if (naver && attempts >= 3) return null;
        if (quota) {
            return failure.retryHint() != null && failure.retryHint().isAfter(now)
                    ? failure.retryHint().plusMinutes(1) : now.plusHours(24);
        }
        LocalDateTime next = attempts < 3 ? now.plusMinutes(5) : now.plusHours(24);
        return failure.retryHint() != null && failure.retryHint().isAfter(next) ? failure.retryHint() : next;
    }

    private CollectionErrorPolicy() { }
}
