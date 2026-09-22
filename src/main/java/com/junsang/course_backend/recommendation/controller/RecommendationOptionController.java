package com.junsang.course_backend.recommendation.controller;

import com.junsang.course_backend.recommendation.dto.response.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.service.RecommendationOptionService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 추천 요청에 사용할 선택지 API다.
@RestController
@RequestMapping("/api/v1/recommendation-options")
@RequiredArgsConstructor
@Tag(name = "1. 옵션 선택")
public class RecommendationOptionController {
    private final RecommendationOptionService recommendationOptionService;

    // 프론트의 추천 입력 화면에 필요한 선택지를 조회한다.
    @GetMapping
    @Operation(summary = "추천 입력 선택지 조회")
    public RecommendationOptionsResponse getOptions() {
        return recommendationOptionService.getOptions();
    }
}
