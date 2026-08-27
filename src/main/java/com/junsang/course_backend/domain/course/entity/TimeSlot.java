package com.junsang.course_backend.domain.course.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// MVP에서 사용하는 코스 시간대다.
@Getter
@RequiredArgsConstructor
public enum TimeSlot {
    MORNING("아침"),
    AFTERNOON("오후"),
    NIGHT("밤");

    private final String displayName;
}
