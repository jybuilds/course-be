package com.junsang.course_backend.recommendation.controller;

import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.recommendationAreas.RecommendationAreasResponse;
import jakarta.validation.Valid;
import com.junsang.course_backend.recommendation.dto.response.recommendationOptions.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

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
    public ResponseEntity<RecommendationOptionsResponse> getOptions() {
        return ResponseEntity.ok(recommendationService.getOptions());
    }

    // 사용자 추천 입력을 받아 Area를 추천한다.
    @PostMapping("/area")
    @Operation(summary = "사용자 입력 Area 추천")
    public ResponseEntity<RecommendationAreasResponse> recommendArea(
            @Valid @RequestBody RecommendationRequest request
    ) {
        return ResponseEntity.ok(recommendationService.recommendationAreas(request));
    }

}
