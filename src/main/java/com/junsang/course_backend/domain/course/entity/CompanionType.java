package com.junsang.course_backend.domain.course.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 코스 추천에 사용하는 동행자 유형이다.
@Getter
@RequiredArgsConstructor
public enum CompanionType {
    FRIEND("친구"),
    LOVER("연인"),
    FAMILY("가족");

    private final String displayName;
}
