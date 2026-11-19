package com.junsang.course_backend.domain.course.controller;

import com.junsang.course_backend.domain.course.dto.response.CoursePublicationResponse;
import com.junsang.course_backend.domain.course.service.CoursePublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 저장된 코스의 공개 상태를 다루는 API다.
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "5. 코스 저장")
public class CourseController {
    private final CoursePublicationService coursePublicationService;

    // 대표 코스 추천 대상이 되도록 코스를 공개한다.
    @PostMapping("/{courseId}/publish")
    @Operation(summary = "코스 공개")
    public CoursePublicationResponse publish(@PathVariable Long courseId) {
        return coursePublicationService.publish(courseId);
    }

    // 추천 대상에서 빠지도록 코스를 비공개로 되돌린다.
    @PostMapping("/{courseId}/unpublish")
    @Operation(summary = "코스 비공개")
    public CoursePublicationResponse unpublish(@PathVariable Long courseId) {
        return coursePublicationService.unpublish(courseId);
    }
}
