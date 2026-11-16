package com.junsang.course_backend.domain.course.creation.dto.request;

import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/// 사용자가 앵커와 일정 장소를 직접 골라 코스를 저장하는 요청이다.
public record CourseCreateRequest(
        @NotNull(message = "앵커 장소는 필수입니다.")
        Long anchorPlaceId,

        @NotBlank(message = "코스 제목은 필수입니다.")
        @Size(max = 200, message = "코스 제목은 200자를 넘을 수 없습니다.")
        String title,

        @Size(max = 2000, message = "코스 설명은 2000자를 넘을 수 없습니다.")
        String description,

        @NotEmpty(message = "일정 장소를 하나 이상 선택해야 합니다.")
        @Size(max = 10, message = "일정 장소는 최대 10개까지 담을 수 있습니다.")
        @Valid
        List<ScheduleItemRequest> scheduleItems
) {
    /// 배열에 담긴 순서가 그대로 코스의 방문 순서가 된다.
    public record ScheduleItemRequest(
            @NotNull(message = "일정 장소 ID는 필수입니다.")
            Long placeId,

            @NotNull(message = "일정 유형은 필수입니다.")
            CourseItemRole role
    ) {
    }
}
