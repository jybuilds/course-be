package com.junsang.course_backend.domain.course.creation.controller;

import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseCandidateResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseDraftResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CreatedCourseResponse;
import com.junsang.course_backend.domain.course.creation.service.CourseCandidateService;
import com.junsang.course_backend.domain.course.creation.service.CourseCreationService;
import com.junsang.course_backend.domain.course.creation.service.CourseDraftService;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 직접 코스 생성 API다.
@RestController
@Validated
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseCreationController {
    private final CourseCandidateService courseCandidateService;
    private final CourseDraftService courseDraftService;
    private final CourseCreationService courseCreationService;

    // 앞 칸의 추천 장소를 기준으로 칸마다 추천순 후보를 이어 붙인 코스 초안을 만든다.
    @GetMapping("/draft")
    public CourseDraftResponse draft(
            @RequestParam @NotNull(message = "앵커 장소는 필수입니다.") Long anchorPlaceId,
            @RequestParam(required = false) @Size(max = 10, message = "칸은 최대 10개까지 만들 수 있습니다.")
            List<CourseItemRole> roles,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) Integer size
    ) {
        return courseDraftService.draft(anchorPlaceId, roles, tagCodes, size);
    }

    // 직전 장소를 기준으로 다음 일정 후보를 조회한다. 후보가 없으면 빈 목록을 준다.
    @GetMapping("/candidates")
    public List<CourseCandidateResponse> findCandidates(
            @RequestParam @NotNull(message = "앵커 장소는 필수입니다.") Long anchorPlaceId,
            @RequestParam @NotNull(message = "일정 유형은 필수입니다.") CourseItemRole role,
            @RequestParam(required = false) Long fromPlaceId,
            @RequestParam(required = false) List<Long> excludePlaceIds,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) Integer size
    ) {
        return courseCandidateService.findCandidates(
                anchorPlaceId, role, fromPlaceId, excludePlaceIds, tagCodes, size
        );
    }

    // 사용자가 확정한 앵커와 일정 장소를 코스로 저장한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedCourseResponse create(@Valid @RequestBody CourseCreateRequest request) {
        return courseCreationService.create(request);
    }
}
