package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.place.entity.Tag;

/// 프론트에 제공할 활성 태그다.
public record TagResponse(
        String code,
        String name) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getCode(), tag.getDisplayName());
    }
}
