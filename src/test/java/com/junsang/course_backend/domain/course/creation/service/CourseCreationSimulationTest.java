package com.junsang.course_backend.domain.course.creation.service;

import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest;
import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest.ScheduleItemRequest;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseCandidateResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CreatedCourseResponse;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 실제 PostgreSQL에서 직접 코스 생성의 후보 조회와 저장 규칙을 확인하는 시뮬레이션이다.
@SpringBootTest
@Transactional
@Sql({"/course-creation-cleanup.sql", "/course-creation-data.sql"})
class CourseCreationSimulationTest {
    private static final long ANCHOR = 931001L;
    private static final long ANCHOR_CAFE = 931002L;
    private static final long ACTIVITY_NEAR = 931003L;
    private static final long ACTIVITY_FAR = 931004L;
    private static final long ACTIVITY_OUT_OF_RANGE = 931005L;
    private static final long MEAL_NEAR = 931006L;
    private static final long MEAL_FAR = 931007L;
    private static final long CAFE_NEAR = 931008L;
    private static final long CAFE_FAR = 931009L;
    private static final long INACTIVE_MEAL = 931010L;
    private static final long OTHER_AREA_CAFE = 931011L;

    @Autowired
    private CourseCandidateService candidateService;

    @Autowired
    private CourseCreationService creationService;

    // 1. 태그를 고르지 않으면 앵커에서 가까운 순서로 후보가 나오고 반경 밖은 빠진다.
    @Test
    void ordersCandidatesByDistanceAndDropsOnesOutOfRange() {
        List<CourseCandidateResponse> candidates = findCandidates(CourseItemRole.ACTIVITY, null, null, null);

        assertThat(candidates)
                .as("candidates=%s", candidates)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(ACTIVITY_NEAR, ACTIVITY_FAR)
                .doesNotContain(ACTIVITY_OUT_OF_RANGE);
        assertThat(candidates.getFirst().distanceMeters()).isLessThan(candidates.getLast().distanceMeters());
    }

    // 2. 앵커와 이미 고른 장소는 다음 단계 후보에서 제외된다.
    @Test
    void excludesAnchorAndAlreadySelectedPlaces() {
        List<CourseCandidateResponse> candidates =
                findCandidates(CourseItemRole.ACTIVITY, null, List.of(ACTIVITY_NEAR), null);

        assertThat(candidates)
                .as("candidates=%s", candidates)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(ACTIVITY_FAR);
    }

    // 3. 태그를 고르면 거리가 멀어도 태그 가중치가 높은 후보가 앞선다.
    @Test
    void prefersTagMatchOverPureDistance() {
        List<CourseCandidateResponse> candidates =
                findCandidates(CourseItemRole.ACTIVITY, null, null, List.of("EXHIBITION"));

        assertThat(candidates)
                .as("candidates=%s", candidates)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(ACTIVITY_FAR, ACTIVITY_NEAR);
        assertThat(candidates.getFirst().matchedTags()).containsExactly("EXHIBITION");
        assertThat(candidates.getLast().matchedTags()).isEmpty();
    }

    // 3-1. 태그가 하나도 없는 장소는 불일치가 아니라 '판단 근거 없음'으로 보고 중립 점수를 준다.
    @Test
    void givesANeutralTagScoreToPlacesThatHaveNoTagsYet() {
        List<CourseCandidateResponse> candidates =
                findCandidates(CourseItemRole.CAFE, null, null, List.of("BREAD"));

        CourseCandidateResponse untagged = candidates.stream()
                .filter(candidate -> candidate.placeId() == ANCHOR_CAFE)
                .findFirst()
                .orElseThrow();
        CourseCandidateResponse mismatched = candidates.stream()
                .filter(candidate -> candidate.placeId() == CAFE_NEAR)
                .findFirst()
                .orElseThrow();

        assertThat(untagged.scoreBreakdown().tagScore())
                .as("태그 정보가 없는 장소는 태그 배점의 절반을 받는다. untagged=%s", untagged)
                .isEqualTo(30.0);
        assertThat(untagged.matchedTags()).isEmpty();
        assertThat(untagged.unmatchedTags())
                .as("판단 근거가 없으므로 불일치로도 표시하지 않는다")
                .isEmpty();
        assertThat(mismatched.scoreBreakdown().tagScore())
                .as("태그가 있는데 요청과 다르면 0점이다. mismatched=%s", mismatched)
                .isZero();
    }

    // 3-2. 태그 정보가 없는 장소가, 더 가깝지만 태그가 어긋나는 장소보다 앞선다.
    @Test
    void ranksAnUntaggedPlaceAboveACloserMismatchedPlace() {
        // CAFE_FAR 기준으로 보면 CAFE_NEAR(태그 불일치)가 ANCHOR_CAFE(태그 없음)보다 가깝다.
        List<CourseCandidateResponse> candidates =
                findCandidates(CourseItemRole.CAFE, CAFE_FAR, null, List.of("BREAD"));

        assertThat(candidates)
                .as("candidates=%s", candidates)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(ANCHOR_CAFE, CAFE_NEAR);
        assertThat(candidates.getFirst().distanceMeters())
                .as("더 멀지만 앞선다는 점이 이 테스트의 핵심이다")
                .isGreaterThan(candidates.getLast().distanceMeters());
    }

    // 3-3. 같은 Area의 카페 중 누구도 갖지 않은 태그는 채점에서 빼서, 잘 맞는 카페의 점수가 희석되지 않는다.
    @Test
    void scoresOnlyTagsThatPlacesOfTheSameTypeHave() {
        List<CourseCandidateResponse> candidates =
                findCandidates(CourseItemRole.CAFE, null, null, List.of("EXHIBITION", "BREAD"));

        assertThat(candidates)
                .as("candidates=%s", candidates)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(CAFE_FAR, ANCHOR_CAFE, CAFE_NEAR);
        CourseCandidateResponse bakery = candidates.getFirst();
        assertThat(bakery.scoreBreakdown().tagScore())
                .as("EXHIBITION을 가진 카페가 없으므로 BREAD 하나로만 채점해 만점이다. bakery=%s", bakery)
                .isEqualTo(60.0);
        assertThat(bakery.matchedTags()).containsExactly("BREAD");
        assertThat(bakery.unmatchedTags()).isEmpty();
    }

    // 3-4. 거리 점수는 가까운 곳끼리는 차이가 작고, 800m를 넘으면 절반 아래로 빠르게 줄어든다.
    @Test
    void dropsProximityScoreQuicklyBeyondWalkingDistance() {
        List<CourseCandidateResponse> candidates = findCandidates(CourseItemRole.ACTIVITY, null, null, null);

        CourseCandidateResponse near = candidates.getFirst();
        CourseCandidateResponse far = candidates.getLast();
        assertThat(near.distanceMeters()).isLessThan(100);
        assertThat(near.scoreBreakdown().proximityScore())
                .as("100m 안쪽은 거리 배점 40점에 거의 가깝다. near=%s", near)
                .isGreaterThan(39.0);
        assertThat(far.distanceMeters()).isGreaterThan(1_000);
        assertThat(far.scoreBreakdown().proximityScore())
                .as("1km를 넘으면 거리 배점의 절반보다 낮다. far=%s", far)
                .isLessThan(20.0);
    }

    // 4. 직전 장소를 넘기면 앵커가 아니라 그 장소를 기준으로 거리를 잰다.
    @Test
    void measuresDistanceFromThePreviousPlace() {
        List<CourseCandidateResponse> fromAnchor = findCandidates(CourseItemRole.MEAL, null, null, null);
        List<CourseCandidateResponse> fromFarActivity =
                findCandidates(CourseItemRole.MEAL, ACTIVITY_FAR, null, null);

        assertThat(fromAnchor)
                .as("fromAnchor=%s", fromAnchor)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(MEAL_NEAR, MEAL_FAR);
        assertThat(fromFarActivity)
                .as("fromFarActivity=%s", fromFarActivity)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(MEAL_FAR, MEAL_NEAR);
    }

    // 5. 비활성 장소와 다른 Area의 장소는 후보에 들어오지 않는다.
    @Test
    void ignoresInactiveAndOtherAreaPlaces() {
        List<CourseCandidateResponse> meals = findCandidates(CourseItemRole.MEAL, null, null, null);
        List<CourseCandidateResponse> cafes = findCandidates(CourseItemRole.CAFE, null, null, null);

        assertThat(meals).extracting(CourseCandidateResponse::placeId).doesNotContain(INACTIVE_MEAL);
        assertThat(cafes).extracting(CourseCandidateResponse::placeId).doesNotContain(OTHER_AREA_CAFE);
    }

    // 6. 해당 유형의 후보가 없으면 예외 대신 빈 목록을 줘서 프론트가 단계를 건너뛸 수 있게 한다.
    @Test
    void returnsEmptyListWhenNoCandidateExistsForTheRole() {
        List<CourseCandidateResponse> candidates = candidateService.findCandidates(
                ANCHOR, CourseItemRole.CAFE, null,
                List.of(ANCHOR_CAFE, CAFE_NEAR, CAFE_FAR), null, null
        );

        assertThat(candidates).as("candidates=%s", candidates).isEmpty();
    }

    // 7. 저장하면 요청 순서대로 order가 붙고 코스 태그가 자동 생성되며 기본은 비공개다.
    @Test
    void savesScheduleInRequestOrderAndGeneratesCourseTags() {
        CreatedCourseResponse created = creationService.create(new CourseCreateRequest(
                ANCHOR, "성수 하루 코스", "앵커에서 시작하는 기본 코스",
                List.of(
                        new ScheduleItemRequest(ACTIVITY_NEAR, CourseItemRole.ACTIVITY),
                        new ScheduleItemRequest(MEAL_NEAR, CourseItemRole.MEAL),
                        new ScheduleItemRequest(CAFE_NEAR, CourseItemRole.CAFE)
                )
        ));

        assertThat(created.courseId()).isNotNull();
        assertThat(created.published()).as("직접 생성 코스는 비공개로 시작한다").isFalse();
        assertThat(created.scheduleItems())
                .as("created=%s", created)
                .extracting(CreatedCourseResponse.ScheduleItemResponse::placeId,
                        CreatedCourseResponse.ScheduleItemResponse::itemOrder)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(ACTIVITY_NEAR, 1),
                        org.assertj.core.api.Assertions.tuple(MEAL_NEAR, 2),
                        org.assertj.core.api.Assertions.tuple(CAFE_NEAR, 3)
                );
        assertThat(created.generatedTags())
                .as("장소 태그에서 코스 태그가 만들어져야 추천 대상이 된다")
                .isNotEmpty();
    }

    // 8. 단계를 건너뛴 코스도 order가 1부터 빈틈없이 매겨진다.
    @Test
    void renumbersOrderWhenAStepIsSkipped() {
        CreatedCourseResponse created = creationService.create(new CourseCreateRequest(
                ANCHOR, "카페를 건너뛴 코스", null,
                List.of(
                        new ScheduleItemRequest(ACTIVITY_NEAR, CourseItemRole.ACTIVITY),
                        new ScheduleItemRequest(MEAL_NEAR, CourseItemRole.MEAL)
                )
        ));

        assertThat(created.scheduleItems())
                .as("created=%s", created)
                .extracting(CreatedCourseResponse.ScheduleItemResponse::itemOrder)
                .containsExactly(1, 2);
    }

    // 9. 앵커가 카페여도 마지막 카페 일정을 그대로 담을 수 있다.
    @Test
    void allowsACafeScheduleEvenWhenTheAnchorIsACafe() {
        CreatedCourseResponse created = creationService.create(new CourseCreateRequest(
                ANCHOR_CAFE, "카페에서 시작하는 코스", null,
                List.of(
                        new ScheduleItemRequest(ACTIVITY_NEAR, CourseItemRole.ACTIVITY),
                        new ScheduleItemRequest(MEAL_NEAR, CourseItemRole.MEAL),
                        new ScheduleItemRequest(CAFE_NEAR, CourseItemRole.CAFE)
                )
        ));

        assertThat(created.anchorPlaceId()).isEqualTo(ANCHOR_CAFE);
        assertThat(created.scheduleItems())
                .as("created=%s", created)
                .extracting(CreatedCourseResponse.ScheduleItemResponse::role)
                .containsExactly("ACTIVITY", "MEAL", "CAFE");
    }

    // 10. 앵커와 같은 장소를 일정에 다시 담으면 저장을 막는다.
    @Test
    void rejectsDuplicatePlaceWithinACourse() {
        CourseCreateRequest request = new CourseCreateRequest(
                ANCHOR, "중복 장소 코스", null,
                List.of(
                        new ScheduleItemRequest(ANCHOR, CourseItemRole.ACTIVITY),
                        new ScheduleItemRequest(MEAL_NEAR, CourseItemRole.MEAL)
                )
        );

        assertThatThrownBy(() -> creationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_DUPLICATE_PLACE);
    }

    // 11. 비활성 장소는 코스에 담을 수 없다.
    @Test
    void rejectsInactivePlaceInSchedule() {
        CourseCreateRequest request = new CourseCreateRequest(
                ANCHOR, "폐업 장소 코스", null,
                List.of(new ScheduleItemRequest(INACTIVE_MEAL, CourseItemRole.MEAL))
        );

        assertThatThrownBy(() -> creationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_PLACE_NOT_FOUND);
    }

    private List<CourseCandidateResponse> findCandidates(
            CourseItemRole role,
            Long fromPlaceId,
            List<Long> excludePlaceIds,
            List<String> tagCodes
    ) {
        return candidateService.findCandidates(ANCHOR, role, fromPlaceId, excludePlaceIds, tagCodes, null);
    }
}
