package com.junsang.course_backend.recommendation.controller;

import com.junsang.course_backend.recommendation.dto.request.CourseRecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.CourseRecommendationResponse;
import com.junsang.course_backend.recommendation.service.CourseRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 대표 코스 추천 API다.
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseRecommendationController {
    private final CourseRecommendationService courseRecommendationService;

    // 사용자 입력과 저장된 코스를 매칭한다.
    @PostMapping("/recommendations")
    public CourseRecommendationResponse recommend(@Valid @RequestBody CourseRecommendationRequest request) {
        return courseRecommendationService.recommend(request);
    }
}
