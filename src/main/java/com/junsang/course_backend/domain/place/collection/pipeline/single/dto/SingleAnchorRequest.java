package com.junsang.course_backend.domain.place.collection.pipeline.single.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/// 카카오 검색 후보 중 사용자가 선택한 장소를 서버가 다시 검증하기 위한 요청이다.
public record SingleAnchorRequest(
        @NotBlank String query,
        @NotBlank String providerPlaceId,
        @NotNull @DecimalMin("33.0") @DecimalMax("39.0") BigDecimal latitude,
        @NotNull @DecimalMin("124.0") @DecimalMax("132.0") BigDecimal longitude
) {
}
