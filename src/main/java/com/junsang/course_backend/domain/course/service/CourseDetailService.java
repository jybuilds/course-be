package com.junsang.course_backend.domain.course.service;

import com.junsang.course_backend.domain.course.dto.response.CourseDetailResponse;
import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 대표 코스 상세를 조회한다.
@Service
@RequiredArgsConstructor
public class CourseDetailService {
    private final CourseRepository courseRepository;

    /// 비공개 코스는 아직 대표 코스가 아니므로 없는 코스와 같게 다룬다.
    @Transactional(readOnly = true)
    public CourseDetailResponse findDetail(Long courseId) {
        Course course = courseRepository.findWithTagsById(courseId)
                .filter(Course::isPublished)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        // 같은 트랜잭션에서 같은 코스를 채우는 조회라 앞서 읽은 인스턴스에 일정이 붙는다.
        courseRepository.findWithScheduleItemsById(courseId);
        return CourseDetailResponse.of(course);
    }
}
