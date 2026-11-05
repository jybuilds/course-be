package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.course.entity.CompanionType;

/// 프론트에 제공할 동행자 유형 선택지다.
public record CompanionTypeOptionResponse(String code, String name) {
    public static CompanionTypeOptionResponse from(CompanionType companionType) {
        return new CompanionTypeOptionResponse(companionType.name(), companionType.getDisplayName());
    }
}
