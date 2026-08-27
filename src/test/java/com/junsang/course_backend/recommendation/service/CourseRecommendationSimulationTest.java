package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.domain.course.service.CourseTagGenerationService;
import com.junsang.course_backend.recommendation.dto.request.CourseRecommendationRequest;
import com.junsang.course_backend.recommendation.dto.response.CourseRecommendationResponse;
import com.junsang.course_backend.recommendation.dto.response.CourseRecommendationResponse.RecommendationMode;
import com.junsang.course_backend.recommendation.dto.response.CourseRecommendationResponse.RecommendationReason;
import com.junsang.course_backend.recommendation.dto.response.RecommendedCourseResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// 실제 PostgreSQL에서 추천 순위, 자동 태깅, 통계 집계를 확인하는 테스트 전용 시뮬레이션이다.
@SpringBootTest
@Transactional
@Sql("/recommendation-simulation-data.sql")
class CourseRecommendationSimulationTest {
    @Autowired
    private CourseRecommendationService recommendationService;

    @Autowired
    private CourseTagGenerationService courseTagGenerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void generateCourseTags() {
        jdbcTemplate.queryForList("SELECT id FROM courses WHERE id BETWEEN 920001 AND 920007", Long.class)
                .forEach(courseTagGenerationService::recalculate);
        entityManager.flush();
    }

    // 1. 같은 쇼핑 앵커라도 실제 빵집이 포함된 코스를 먼저 추천하고 노출을 집계한다.
    @Test
    void recommendsTheCourseWhoseActualItemsMatchMultipleTags() {
        CourseRecommendationResponse response = recommendSeoul(List.of("SHOPPING", "BREAD"));

        assertThat(response.recommendationMode())
                .as("response=%s", response)
                .isEqualTo(RecommendationMode.PERSONALIZED);
        assertThat(response.courses())
                .extracting(RecommendedCourseResponse::title)
                .containsExactly(
                        "성수 쇼핑 빵 데이트",
                        "여의도 쇼핑 빵 코스",
                        "코엑스 실내 쇼핑 코스",
                        "서촌 빵 전시 코스",
                        "성수 쇼핑 집중 코스"
                );
        assertThat(response.courses().getFirst().score()).isEqualTo(74.74);
        assertThat(response.courses().getFirst().matchType())
                .isEqualTo(RecommendedCourseResponse.MatchType.FULL_MATCH);
        assertThat(impressionCount(920001)).isEqualTo(4);
        assertThat(impressionCount(920002)).isEqualTo(10);
        assertThat(impressionCount(920005)).isEqualTo(7);
        assertThat(impressionCount(920006)).isEqualTo(1);
    }

    // 2. 태그가 하나면 관련 코스 안에서 상황 적합도와 선택 수가 순위를 가른다.
    @Test
    void usesContextAndPopularityWhenOnlyOneTagIsSelected() {
        CourseRecommendationResponse response = recommendSeoul(List.of("SHOPPING"));

        assertThat(response.recommendationMode()).isEqualTo(RecommendationMode.MIXED);
        assertThat(response.reason()).isEqualTo(RecommendationReason.INSUFFICIENT_TAG_MATCHES);
        assertThat(response.courses().getFirst().title()).isEqualTo("성수 쇼핑 집중 코스");
        assertThat(response.courses().getFirst().score()).isEqualTo(75.58);
    }

    // 3. 일치 태그가 없으면 사용자 선택과 무관한 도시 인기 코스임을 명시한다.
    @Test
    void returnsAnExplicitPopularFallbackWhenNoCourseMatches() {
        CourseRecommendationResponse response = recommendSeoul(List.of("GAME"));

        assertThat(response.recommendationMode()).isEqualTo(RecommendationMode.POPULAR_FALLBACK);
        assertThat(response.reason()).isEqualTo(RecommendationReason.NO_TAG_MATCH);
        assertThat(response.courses()).allMatch(course ->
                course.matchType() == RecommendedCourseResponse.MatchType.POPULAR_FALLBACK);
        assertThat(response.courses().getFirst().title()).isEqualTo("한강 인기 산책 코스");
    }

    // 4. 일부만 일치하면 관련 코스를 먼저 두고 부족한 결과를 인기 코스로 보충한다.
    @Test
    void returnsMixedResultsWhenOnlyOneCourseMatches() {
        CourseRecommendationResponse response = recommendSeoul(List.of("EXHIBITION"));

        assertThat(response.recommendationMode()).isEqualTo(RecommendationMode.MIXED);
        assertThat(response.reason()).isEqualTo(RecommendationReason.INSUFFICIENT_TAG_MATCHES);
        assertThat(response.courses().getFirst().title()).isEqualTo("서촌 빵 전시 코스");
        assertThat(response.courses().getFirst().matchType())
                .isEqualTo(RecommendedCourseResponse.MatchType.FULL_MATCH);
        assertThat(response.courses().subList(1, response.courses().size())).allMatch(course ->
                course.matchType() == RecommendedCourseResponse.MatchType.POPULAR_FALLBACK);
    }

    // 5. 빵집 세 곳은 BREAD를 핵심 태그로 만들고 식사와 쇼핑도 부가 태그로 남긴다.
    @Test
    void generatesTagsAndKeepsTheContextualRoleForTheDaejeonBreadTour() {
        assertThat(courseTagWeight(920007, "BREAD")).isEqualTo(76);
        assertThat(courseTagWeight(920007, "KOREAN_FOOD")).isEqualTo(52);
        assertThat(courseTagWeight(920007, "SHOPPING")).isEqualTo(52);
        assertThat(placeType(912001)).isEqualTo("CAFE");
        assertThat(itemRole(920007, 912001)).isEqualTo("ACTIVITY");
    }

    private CourseRecommendationResponse recommendSeoul(List<String> tagCodes) {
        Long cityId = jdbcTemplate.queryForObject(
                "SELECT id FROM cities WHERE code = 'SEOUL'", Long.class);
        return recommendationService.recommend(new CourseRecommendationRequest(
                cityId, tagCodes, CompanionType.LOVER, TimeSlot.AFTERNOON));
    }

    private long impressionCount(long courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT impression_count FROM course_stats WHERE course_id = ?", Long.class, courseId);
    }

    private int courseTagWeight(long courseId, String tagCode) {
        return jdbcTemplate.queryForObject("""
                SELECT course_tag.weight
                FROM course_tags AS course_tag
                JOIN tags AS tag ON tag.id = course_tag.tag_id
                WHERE course_tag.course_id = ? AND tag.code = ?
                """, Integer.class, courseId, tagCode);
    }

    private String placeType(long placeId) {
        return jdbcTemplate.queryForObject(
                "SELECT place_type FROM places WHERE id = ?", String.class, placeId);
    }

    private String itemRole(long courseId, long placeId) {
        return jdbcTemplate.queryForObject(
                "SELECT item_role FROM course_items WHERE course_id = ? AND place_id = ?",
                String.class, courseId, placeId);
    }
}
