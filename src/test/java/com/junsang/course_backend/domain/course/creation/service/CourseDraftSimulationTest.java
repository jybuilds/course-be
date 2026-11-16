package com.junsang.course_backend.domain.course.creation.service;

import com.junsang.course_backend.domain.course.creation.dto.response.CourseCandidateResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseDraftResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseDraftResponse.SlotResponse;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.junsang.course_backend.domain.course.entity.CourseItemRole.ACTIVITY;
import static com.junsang.course_backend.domain.course.entity.CourseItemRole.CAFE;
import static com.junsang.course_backend.domain.course.entity.CourseItemRole.MEAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 실제 PostgreSQL에서 코스 초안이 앞 칸의 추천 장소를 기준으로 이어지는지 확인하는 시뮬레이션이다.
@SpringBootTest
@Transactional
@Sql({"/course-creation-cleanup.sql", "/course-creation-data.sql"})
class CourseDraftSimulationTest {
    private static final long ANCHOR = 931001L;
    private static final long ANCHOR_CAFE = 931002L;
    private static final long ACTIVITY_NEAR = 931003L;
    private static final long MEAL_NEAR = 931006L;
    private static final long MEAL_FAR = 931007L;
    private static final long CAFE_NEAR = 931008L;
    private static final long CAFE_FAR = 931009L;

    @Autowired
    private CourseDraftService draftService;

    // 1. 칸 구성을 주지 않으면 활동 → 식사 → 카페로 만들고, 칸마다 앞 칸의 0번째 장소가 거리 기준이 된다.
    @Test
    void chainsDefaultSlotsFromThePreviousRecommendation() {
        CourseDraftResponse draft = draftService.draft(ANCHOR, null, null, null);

        assertThat(draft.slots())
                .as("draft=%s", draft)
                .extracting(SlotResponse::order, SlotResponse::role, SlotResponse::basePlaceId)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(1, ACTIVITY, ANCHOR),
                        org.assertj.core.api.Assertions.tuple(2, MEAL, ACTIVITY_NEAR),
                        org.assertj.core.api.Assertions.tuple(3, CAFE, MEAL_NEAR)
                );
        assertThat(draft.slots().get(2).places())
                .as("앞 칸에서 추천한 장소는 뒤 칸 후보에 다시 나오지 않는다")
                .extracting(CourseCandidateResponse::placeId)
                .doesNotContain(ACTIVITY_NEAR, MEAL_NEAR);
    }

    // 2. 같은 유형 칸을 두 번 요청하면 앞 칸에서 추천한 장소는 뒤 칸에서 빠진다.
    @Test
    void doesNotRecommendTheSamePlaceTwiceWhenARoleRepeats() {
        CourseDraftResponse draft = draftService.draft(ANCHOR, List.of(ACTIVITY, MEAL, CAFE, MEAL), null, null);

        SlotResponse secondMeal = draft.slots().get(3);
        assertThat(secondMeal.places())
                .as("draft=%s", draft)
                .extracting(CourseCandidateResponse::placeId)
                .containsExactly(MEAL_FAR);
        assertThat(secondMeal.basePlaceId()).isEqualTo(draft.slots().get(2).places().getFirst().placeId());
    }

    // 3. 후보가 없는 칸은 비워 두고, 다음 칸은 마지막으로 추천된 장소를 계속 기준으로 삼는다.
    @Test
    void keepsTheLastRecommendationAsBaseAfterAnEmptySlot() {
        CourseDraftResponse draft = draftService.draft(ANCHOR, List.of(CAFE, CAFE, CAFE, CAFE, MEAL), null, null);

        assertThat(draft.slots().subList(0, 3))
                .as("draft=%s", draft)
                .extracting(slot -> slot.places().getFirst().placeId())
                .containsExactly(ANCHOR_CAFE, CAFE_NEAR, CAFE_FAR);
        assertThat(draft.slots().get(3).places()).as("카페가 모두 추천돼 네 번째 카페 칸은 비어 있다").isEmpty();
        assertThat(draft.slots().get(4).basePlaceId()).isEqualTo(CAFE_FAR);
        assertThat(draft.slots().get(4).places()).isNotEmpty();
    }

    // 4. 비활성이거나 없는 앵커로는 초안을 만들 수 없다.
    @Test
    void rejectsUnknownAnchor() {
        assertThatThrownBy(() -> draftService.draft(999_999L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_ANCHOR_NOT_FOUND);
    }
}
