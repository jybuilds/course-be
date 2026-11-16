package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.CollectionFailureClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoCollectionErrorHandler {
    private final KakaoCollectionJobWriter writer;
    public void handle(Long jobId, RuntimeException error) {
        writer.fail(jobId, CollectionFailureClassifier.classify("KAKAO", error,
                PlaceRefinementErrorCode.KAKAO_COLLECTION_PROCESSING_FAILED));
    }
}
