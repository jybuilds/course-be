package com.junsang.course_backend.domain.place.collection.pipeline.kakao.dto;

/// Area 단위 수집 테스트가 끝난 뒤 저장 결과를 간단히 확인하는 응답이다.
public record PlaceCollectionResponse(
        Long areaId,
        long storedTempCount
) {
}
