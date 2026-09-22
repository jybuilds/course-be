package com.junsang.course_backend.recommendation.unused.controller;

import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.unused.dto.response.CourseRecommendationResponse;
import com.junsang.course_backend.recommendation.unused.service.CourseRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 대표 코스 추천 API다.
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "코스 추천 API - 미사용")
public class CourseRecommendationController {
    private final CourseRecommendationService courseRecommendationService;

    // 사용자 입력과 저장된 코스를 매칭한다.
    @PostMapping("/recommendations")
    @Operation(summary = "대표 코스 추천", deprecated = true)
    public CourseRecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
        return courseRecommendationService.recommend(request);
    }
}
