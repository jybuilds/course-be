package com.junsang.course_backend.infra.kakao.dto.request;

import java.math.BigDecimal;

public record KakaoKeywordSearchRequest(
        String query,
        BigDecimal minLongitude,
        BigDecimal minLatitude,
        BigDecimal maxLongitude,
        BigDecimal maxLatitude,
        int size,
        int page
) {
    public String rect() { return minLongitude + "," + minLatitude + "," + maxLongitude + "," + maxLatitude; }
}
