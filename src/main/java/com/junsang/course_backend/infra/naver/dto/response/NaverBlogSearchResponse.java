package com.junsang.course_backend.infra.naver.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverBlogSearchResponse (
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<NaverBlogItem> items
){
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NaverBlogItem(
            String title,
            String description,
            String postdate
    ) {
    }
}
