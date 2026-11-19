package com.junsang.course_backend.domain.course.service;

import com.junsang.course_backend.domain.course.dto.response.CoursePublicationResponse;
import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 저장된 코스의 공개 여부를 전환한다. 공개된 코스만 대표 코스 추천 대상이 된다.
@Service
@RequiredArgsConstructor
public class CoursePublicationService {
    private final CourseRepository courseRepository;
    private final CourseTagGenerationService courseTagGenerationService;

    /// 공개 직전에 코스 태그를 다시 계산한다. 저장 이후 장소 태그가 수집되었다면 그 결과를 반영해야
    /// 추천에서 태그로 매칭될 수 있다.
    @Transactional
    public CoursePublicationResponse publish(Long courseId) {
        Course course = findCourse(courseId);
        courseTagGenerationService.recalculate(course.getId());
        course.publish();
        return CoursePublicationResponse.of(course);
    }

    /// 이미 비공개인 코스도 그대로 둔다. 같은 요청을 반복해도 결과가 같다.
    @Transactional
    public CoursePublicationResponse unpublish(Long courseId) {
        Course course = findCourse(courseId);
        course.unpublish();
        return CoursePublicationResponse.of(course);
    }

    private Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
    }
}
