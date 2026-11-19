package com.junsang.course_backend.domain.course.service;

import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest;
import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest.ScheduleItemRequest;
import com.junsang.course_backend.domain.course.creation.service.CourseCreationService;
import com.junsang.course_backend.domain.course.dto.response.CoursePublicationResponse;
import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 실제 PostgreSQL에서 코스 공개 전환이 추천 대상 조회에 반영되는지 확인하는 시뮬레이션이다.
@SpringBootTest
@Transactional
@Sql({"/course-creation-cleanup.sql", "/course-creation-data.sql"})
class CoursePublicationSimulationTest {
    private static final long ANCHOR = 931001L;
    private static final long ACTIVITY_NEAR = 931003L;
    private static final long MEAL_NEAR = 931006L;
    // 시드에서 태그를 주지 않은 장소다. 공개 시점의 태그 재계산을 확인할 때 쓴다.
    private static final long UNTAGGED_ACTIVITY = 931005L;
    private static final long CREATION_AREA = 930001L;

    @Autowired
    private CoursePublicationService publicationService;

    @Autowired
    private CourseCreationService creationService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceTagRepository placeTagRepository;

    @Autowired
    private EntityManager entityManager;

    // 1. 공개하면 대표 코스 추천 대상 조회에 들어온다.
    @Test
    void makesTheCourseVisibleToRecommendation() {
        Long courseId = createCourse(ACTIVITY_NEAR, MEAL_NEAR);
        assertThat(publishedCourseIds()).as("저장 직후에는 비공개다").doesNotContain(courseId);

        CoursePublicationResponse published = publicationService.publish(courseId);

        assertThat(published.published()).isTrue();
        assertThat(publishedCourseIds()).as("published=%s", published).contains(courseId);
    }

    // 2. 비공개로 되돌리면 추천 대상에서 빠진다.
    @Test
    void removesTheCourseFromRecommendationWhenUnpublished() {
        Long courseId = createCourse(ACTIVITY_NEAR, MEAL_NEAR);
        publicationService.publish(courseId);

        CoursePublicationResponse unpublished = publicationService.unpublish(courseId);

        assertThat(unpublished.published()).isFalse();
        assertThat(publishedCourseIds()).as("unpublished=%s", unpublished).doesNotContain(courseId);
    }

    // 3. 같은 요청을 반복해도 결과가 같다.
    @Test
    void keepsTheSameResultWhenTheSameRequestRepeats() {
        Long courseId = createCourse(ACTIVITY_NEAR, MEAL_NEAR);

        publicationService.publish(courseId);
        assertThat(publicationService.publish(courseId).published()).isTrue();

        publicationService.unpublish(courseId);
        assertThat(publicationService.unpublish(courseId).published()).isFalse();
    }

    // 4. 저장 이후에 장소 태그가 수집되면 공개 시점에 코스 태그로 반영된다.
    @Test
    void recalculatesCourseTagsWhenPublishing() {
        Long courseId = createCourse(UNTAGGED_ACTIVITY);
        assertThat(courseTagsOf(courseId)).as("태그 없는 장소만 담았으므로 코스 태그도 없다").isEmpty();

        givenPlaceTag(UNTAGGED_ACTIVITY, "BOOKSTORE");

        CoursePublicationResponse published = publicationService.publish(courseId);

        assertThat(published.tags()).as("published=%s", published).containsExactly("BOOKSTORE");
    }

    // 5. 없는 코스를 공개하려 하면 막는다.
    @Test
    void rejectsUnknownCourse() {
        assertThatThrownBy(() -> publicationService.publish(999_999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
    }

    private Long createCourse(long... itemPlaceIds) {
        List<ScheduleItemRequest> items = Arrays.stream(itemPlaceIds)
                .mapToObj(placeId -> new ScheduleItemRequest(placeId, CourseItemRole.ACTIVITY))
                .toList();
        return creationService.create(
                new CourseCreateRequest(ANCHOR, "공개 전환 테스트 코스", null, items)
        ).courseId();
    }

    private void givenPlaceTag(long placeId, String tagCode) {
        Place place = placeRepository.findById(placeId).orElseThrow();
        Tag tag = entityManager
                .createQuery("select tag from Tag tag where tag.code = :code", Tag.class)
                .setParameter("code", tagCode)
                .getSingleResult();
        placeTagRepository.save(PlaceTag.create(place, tag, 100));
    }

    private List<Long> publishedCourseIds() {
        return courseRepository.findByAreaCityIdAndIsPublishedTrue(seoulCityId()).stream()
                .map(Course::getId)
                .toList();
    }

    private Long seoulCityId() {
        return entityManager
                .createQuery("select area.city.id from Area area where area.id = :areaId", Long.class)
                .setParameter("areaId", CREATION_AREA)
                .getSingleResult();
    }

    private List<String> courseTagsOf(Long courseId) {
        return CoursePublicationResponse.of(courseRepository.findById(courseId).orElseThrow())
                .tags().stream().toList();
    }
}
