package com.junsang.course_backend.infra.naver.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverLocalSearchResponse(
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<NaverLocalItem> items
) {
}
