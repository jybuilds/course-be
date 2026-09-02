package com.junsang.course_backend.infra.kakao.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(
        KakaoSearchMeta meta,
        List<KakaoPlaceDocument> documents
) {
}
