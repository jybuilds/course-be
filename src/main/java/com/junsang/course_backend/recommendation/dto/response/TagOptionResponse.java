package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.tag.Tag;

import java.util.List;

/// 프론트에 제공할 대표 태그와 유사 선택지다.
public record TagOptionResponse(
        String code,
        String name,
        List<String> options) {
    public static TagOptionResponse from(Tag tag, List<String> options) {
        return new TagOptionResponse(tag.getCode(), tag.getDisplayName(), options);
    }
}
