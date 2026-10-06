package com.junsang.course_backend.recommendation.controller;

import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.RecommendationAreaResponse;
import com.junsang.course_backend.recommendation.dto.response.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 추천 요청에 사용할 선택지 API다.
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "1. 옵션 선택")
public class RecommendationController {
    private final RecommendationService recommendationService;

    // 프론트의 추천 입력 화면에 필요한 선택지를 조회한다.
    @GetMapping("/options")
    @Operation(summary = "추천 입력 선택지 조회")
    public RecommendationOptionsResponse getOptions() {
        return recommendationService.getOptions();
    }

    // 사용자 선택을 기준으로 추천 Area를 반환한다.
    @PostMapping("/areas")
    @Operation(summary = "추천 Area 조회")
    public RecommendationAreaResponse recommendAreas(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommendAreas(request);
    }
}
