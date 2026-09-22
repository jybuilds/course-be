package com.junsang.course_backend.recommendation.dto.request;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

/// 추천 흐름에서 사용하는 사용자 선택 입력이다.
public record RecommendationRequest(
        @NotNull(message = "지역은 필수입니다.")
        Long cityId,

        @NotNull(message = "동행자 유형은 필수입니다.")
        CompanionType companionType,

        @NotNull(message = "시간대는 필수입니다.")
        TimeSlot timeSlot,

        @NotEmpty(message = "태그를 하나 이상 선택해야 합니다.")
        @Size(max = 10, message = "태그는 최대 10개까지 선택할 수 있습니다.")
        @UniqueElements(message = "같은 태그를 중복 선택할 수 없습니다.")
        List<@NotBlank(message = "태그 코드는 비어 있을 수 없습니다.") String> tagCodes
) {
}
