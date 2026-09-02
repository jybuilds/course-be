package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

/// AI 태깅 응답과 저장 검증이 공유하는 기준값이다.
public final class AiTaggingPolicy {

    public static final int MIN_TAG_COUNT = 1;
    public static final int MAX_TAG_COUNT = 10;
    public static final int MAX_TAG_WEIGHT = 100;

    private AiTaggingPolicy() {
    }
}
