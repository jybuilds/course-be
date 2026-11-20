package com.junsang.course_backend.domain.course.controller;

import com.junsang.course_backend.domain.course.dto.response.CourseDetailResponse;
import com.junsang.course_backend.domain.course.dto.response.CoursePublicationResponse;
import com.junsang.course_backend.domain.course.service.CourseDetailService;
import com.junsang.course_backend.domain.course.service.CoursePublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 저장된 코스의 상세 조회와 공개 상태를 다루는 API다.
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseDetailService courseDetailService;
    private final CoursePublicationService coursePublicationService;

    // 대표 코스 상세 화면에 필요한 앵커, 방문 순서, 코스 태그를 조회한다.
    @GetMapping("/{courseId}")
    @Tag(name = "6. 코스 상세")
    @Operation(summary = "코스 상세 조회")
    public CourseDetailResponse findDetail(@PathVariable Long courseId) {
        return courseDetailService.findDetail(courseId);
    }

    // 대표 코스 추천 대상이 되도록 코스를 공개한다.
    @PostMapping("/{courseId}/publish")
    @Tag(name = "5. 코스 저장")
    @Operation(summary = "코스 공개")
    public CoursePublicationResponse publish(@PathVariable Long courseId) {
        return coursePublicationService.publish(courseId);
    }

    // 추천 대상에서 빠지도록 코스를 비공개로 되돌린다.
    @PostMapping("/{courseId}/unpublish")
    @Tag(name = "5. 코스 저장")
    @Operation(summary = "코스 비공개")
    public CoursePublicationResponse unpublish(@PathVariable Long courseId) {
        return coursePublicationService.unpublish(courseId);
    }
}
