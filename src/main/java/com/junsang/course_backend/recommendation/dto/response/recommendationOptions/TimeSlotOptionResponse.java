package com.junsang.course_backend.recommendation.dto.response;

import com.junsang.course_backend.domain.course.entity.TimeSlot;

/// 프론트에 제공할 시간대 선택지다.
public record TimeSlotOptionResponse(
        String code,
        String name) {
    public static TimeSlotOptionResponse from(TimeSlot timeSlot) {
        return new TimeSlotOptionResponse(timeSlot.name(), timeSlot.getDisplayName());
    }
}
