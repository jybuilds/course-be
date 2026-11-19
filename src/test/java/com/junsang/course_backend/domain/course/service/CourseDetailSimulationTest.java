package com.junsang.course_backend.domain.course.service;

import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest;
import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest.ScheduleItemRequest;
import com.junsang.course_backend.domain.course.creation.service.CourseCreationService;
import com.junsang.course_backend.domain.course.dto.response.CourseDetailResponse;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// 실제 PostgreSQL에서 코스 상세 조회가 공개 코스만 돌려주는지 확인하는 시뮬레이션이다.
@SpringBootTest
@Transactional
@Sql({"/course-creation-cleanup.sql", "/course-creation-data.sql"})
class CourseDetailSimulationTest {
    private static final long ANCHOR = 931001L;
    private static final long ACTIVITY_NEAR = 931003L;
    private static final long MEAL_NEAR = 931006L;
    private static final long CAFE_NEAR = 931008L;

    @Autowired
    private CourseDetailService detailService;

    @Autowired
    private CoursePublicationService publicationService;

    @Autowired
    private CourseCreationService creationService;

    @Autowired
    private EntityManager entityManager;

    // 1. 공개 코스는 앵커, 방문 순서, 코스 태그를 함께 돌려준다.
    @Test
    void returnsAnchorScheduleAndTags() {
        Long courseId = createPublishedCourse();

        CourseDetailResponse detail = detailService.findDetail(courseId);

        assertThat(detail.title()).isEqualTo("성수 하루 코스");
        assertThat(detail.anchorPlace().placeId()).isEqualTo(ANCHOR);
        assertThat(detail.scheduleItems())
                .as("detail=%s", detail)
                .extracting(item -> item.place().placeId(), CourseDetailResponse.ScheduleItemResponse::role,
                        CourseDetailResponse.ScheduleItemResponse::itemOrder)
                .containsExactly(
                        tuple(ACTIVITY_NEAR, "ACTIVITY", 1),
                        tuple(MEAL_NEAR, "MEAL", 2),
                        tuple(CAFE_NEAR, "CAFE", 3)
                );
        assertThat(detail.courseTags())
                .as("detail=%s", detail)
                .extracting(CourseDetailResponse.CourseTagResponse::code)
                .containsExactlyInAnyOrder("POPUP", "KOREAN_FOOD", "DESSERT");
    }

    // 2. 코스의 도시와 지역은 앵커의 지역을 따라간다.
    @Test
    void returnsCityAndAreaOfTheAnchor() {
        Long courseId = createPublishedCourse();

        CourseDetailResponse detail = detailService.findDetail(courseId);

        assertThat(detail.area().areaId()).isEqualTo(930001L);
        assertThat(detail.area().name()).isEqualTo("코스 생성 시뮬레이션 지역");
        assertThat(detail.city().code()).as("detail=%s", detail).isEqualTo("SEOUL");
    }

    // 3. 코스 태그는 가중치가 높은 것부터 내려준다.
    @Test
    void ordersCourseTagsByWeightDescending() {
        Long courseId = createPublishedCourse();

        List<Integer> weights = detailService.findDetail(courseId).courseTags().stream()
                .map(CourseDetailResponse.CourseTagResponse::weight)
                .toList();

        assertThat(weights).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    // 4. 상세 화면이 지도를 그릴 수 있도록 좌표를 함께 준다.
    @Test
    void includesCoordinatesForEveryPlace() {
        Long courseId = createPublishedCourse();

        CourseDetailResponse detail = detailService.findDetail(courseId);

        assertThat(detail.anchorPlace().latitude()).isNotNull();
        assertThat(detail.anchorPlace().longitude()).isNotNull();
        assertThat(detail.scheduleItems())
                .as("detail=%s", detail)
                .allSatisfy(item -> {
                    assertThat(item.place().latitude()).isNotNull();
                    assertThat(item.place().longitude()).isNotNull();
                });
    }

    // 5. 일정과 태그를 함께 가져와도 일정이 태그 수만큼 중복되지 않는다.
    @Test
    void doesNotDuplicateScheduleItemsByTagCount() {
        Long courseId = createPublishedCourse();

        CourseDetailResponse detail = detailService.findDetail(courseId);

        assertThat(detail.scheduleItems())
                .as("태그 %d개와 함께 조회해도 일정은 3개다. detail=%s", detail.courseTags().size(), detail)
                .hasSize(3)
                .extracting(CourseDetailResponse.ScheduleItemResponse::itemOrder)
                .containsExactly(1, 2, 3);
    }

    // 6. 비공개 코스는 아직 대표 코스가 아니므로 없는 코스처럼 다룬다.
    @Test
    void hidesUnpublishedCourse() {
        Long courseId = createCourse();

        assertThatThrownBy(() -> detailService.findDetail(courseId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
    }

    // 7. 공개를 되돌리면 상세도 다시 감춘다.
    @Test
    void hidesTheCourseAgainWhenUnpublished() {
        Long courseId = createPublishedCourse();
        publicationService.unpublish(courseId);

        assertThatThrownBy(() -> detailService.findDetail(courseId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
    }

    // 8. 없는 코스도 같은 오류로 막는다.
    @Test
    void rejectsUnknownCourse() {
        assertThatThrownBy(() -> detailService.findDetail(999_999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
    }

    private Long createPublishedCourse() {
        Long courseId = createCourse();
        publicationService.publish(courseId);
        // 저장 직후의 영속성 컨텍스트가 올바른 목록을 들고 있으면 조회 쿼리의 문제가 가려진다.
        // 실제 상세 조회처럼 비운 상태에서 읽는다.
        entityManager.flush();
        entityManager.clear();
        return courseId;
    }

    private Long createCourse() {
        return creationService.create(new CourseCreateRequest(
                ANCHOR, "성수 하루 코스", "앵커에서 시작하는 기본 코스",
                List.of(
                        new ScheduleItemRequest(ACTIVITY_NEAR, CourseItemRole.ACTIVITY),
                        new ScheduleItemRequest(MEAL_NEAR, CourseItemRole.MEAL),
                        new ScheduleItemRequest(CAFE_NEAR, CourseItemRole.CAFE)
                )
        )).courseId();
    }
}
