package com.junsang.course_backend.domain.place.collection.pipeline.naver.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.collection.pipeline.support.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NaverRefinementErrorHandler {
    private final NaverRefinementWriter writer;
    public CollectionFailure classify(RuntimeException error) {
        if (error instanceof PlaceRefinementException refinement)
            return new CollectionFailure(refinement.getErrorCode(), "네이버 장소 매칭 실패: " + refinement.getErrorCode());
        return CollectionFailureClassifier.classify("NAVER", error, PlaceRefinementErrorCode.UNEXPECTED_ERROR);
    }
    public PlaceCollectionTemp handle(Long tempId, RuntimeException error) {
        return writer.fail(tempId, classify(error));
    }
}
