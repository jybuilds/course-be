package com.junsang.course_backend.domain.place.collection.budget;

import java.time.Instant;

/** 정상적인 공급자 대기. 장소별 오류 재시도 횟수를 소비하지 않는다. */
public class CollectionDeferredException extends RuntimeException {
    private final Instant retryAt;

    public CollectionDeferredException(Instant retryAt) {
        super("수집 작업 대기");
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
