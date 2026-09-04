package com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service;

import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import lombok.Getter;

@Getter
public class PlaceRefinementException extends RuntimeException {

    private final PlaceRefinementErrorCode errorCode;

    public PlaceRefinementException(PlaceRefinementErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
