package com.junsang.course_backend.recommendation.unused.service;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.stats.entity.CourseStats;
import com.junsang.course_backend.domain.stats.repository.CourseStatsRepository;
import com.junsang.course_backend.domain.place.entity.CourseTag;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.recommendation.dto.request.RecommendationRequest;
import com.junsang.course_backend.recommendation.unused.dto.response.CourseRecommendationResponse;
import com.junsang.course_backend.recommendation.unused.dto.response.CourseRecommendationResponse.RecommendationMode;
import com.junsang.course_backend.recommendation.unused.dto.response.CourseRecommendationResponse.RecommendationReason;
import com.junsang.course_backend.recommendation.unused.dto.response.RecommendedCourseResponse;
import com.junsang.course_backend.recommendation.unused.dto.response.RecommendedCourseResponse.ScoreBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// 저장된 대표 코스 중 사용자 입력에 가장 가까운 코스를 찾는다.
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {
    private static final int RESULT_LIMIT = 5;
    private static final int MAX_COURSES_PER_ANCHOR = 2;

    private final CityRepository cityRepository;
    private final CourseRepository courseRepository;
    private final TagRepository tagRepository;
    private final CourseStatsRepository courseStatsRepository;

    // 관련성, 상황 적합도, 인기도를 계산하고 같은 앵커의 반복 노출을 줄인다.
    @Transactional
    public CourseRecommendationResponse recommend(RecommendationRequest request) {
        if (!cityRepository.existsById(request.cityId())) {
            throw new IllegalArgumentException("존재하지 않는 지역입니다.");
        }

        Set<String> requestedTags = normalizeAndValidateTags(request.tagCodes());
        List<Course> courses = courseRepository.findByAreaCityIdAndIsPublishedTrue(request.cityId())
                .stream()
                .filter(this::isUsable)
                .toList();

        if (courses.isEmpty()) {
            return new CourseRecommendationResponse(
                    RecommendationMode.POPULAR_FALLBACK, RecommendationReason.NO_COURSES, List.of());
        }

        Set<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());
        Map<Long, Long> selectionCounts = courseStatsRepository.findByCourseIdIn(courseIds).stream()
                .collect(Collectors.toMap(stats -> stats.getCourse().getId(), CourseStats::getSelectionCount));

        List<ScoredCourse> scoredCourses = courses.stream()
                .map(course -> score(course, requestedTags, request,
                        selectionCounts.getOrDefault(course.getId(), 0L)))
                .sorted(Comparator.comparingDouble(ScoredCourse::totalScore).reversed()
                        .thenComparing(item -> item.course().getId()))
                .toList();

        List<ScoredCourse> matchingCourses = scoredCourses.stream()
                .filter(course -> course.result().matchType() != RecommendedCourseResponse.MatchType.POPULAR_FALLBACK)
                .toList();
        List<ScoredCourse> fallbackCourses = scoredCourses.stream()
                .filter(course -> course.result().matchType() == RecommendedCourseResponse.MatchType.POPULAR_FALLBACK)
                .toList();

        List<ScoredCourse> selectedCourses = selectWithAnchorLimit(matchingCourses, new ArrayList<>());
        int matchingResultCount = selectedCourses.size();
        selectWithAnchorLimit(fallbackCourses, selectedCourses);

        RecommendationMode mode = matchingResultCount == 0
                ? RecommendationMode.POPULAR_FALLBACK
                : selectedCourses.size() > matchingResultCount ? RecommendationMode.MIXED : RecommendationMode.PERSONALIZED;
        RecommendationReason reason = mode == RecommendationMode.POPULAR_FALLBACK
                ? RecommendationReason.NO_TAG_MATCH
                : mode == RecommendationMode.MIXED
                ? RecommendationReason.INSUFFICIENT_TAG_MATCHES
                : RecommendationReason.NONE;

        courseStatsRepository.increaseImpressionCounts(selectedCourses.stream()
                .map(item -> item.course().getId())
                .collect(Collectors.toSet()));

        List<RecommendedCourseResponse> recommendations = selectedCourses.stream()
                .map(ScoredCourse::toResponse)
                .toList();
        return new CourseRecommendationResponse(mode, reason, recommendations);
    }

    // 대소문자와 공백을 정규화한 뒤 활성 태그인지 확인한다.
    private Set<String> normalizeAndValidateTags(List<String> requestedTagCodes) {
        Set<String> normalized = requestedTagCodes.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.size() != requestedTagCodes.size()) {
            throw new IllegalArgumentException("같은 태그를 중복 선택할 수 없습니다.");
        }
        if (tagRepository.countByCodeInAndIsActiveTrue(normalized) != normalized.size()) {
            throw new IllegalArgumentException("등록되지 않은 태그가 포함되어 있습니다.");
        }
        return normalized;
    }

    // 비활성 장소가 있거나 일정이 비어 있는 대표 코스는 노출하지 않는다.
    private boolean isUsable(Course course) {
        return course.getAnchorPlace().isActive()
                && !course.getScheduleItems().isEmpty()
                && course.getScheduleItems().stream().allMatch(item -> item.getPlace().isActive());
    }

    // 장소 구성에서 미리 생성한 코스 태그로 사용자 선택과의 관련성을 계산한다.
    private ScoredCourse score(
            Course course,
            Set<String> requestedTags,
            RecommendationRequest request,
            long selectionCount
    ) {
        Map<String, Integer> courseWeights = course.getTags().stream()
                .collect(Collectors.toMap(tag -> tag.getTag().getCode(), CourseTag::getWeight, Math::max));

        RecommendationScoreCalculator.Result result = RecommendationScoreCalculator.calculate(
                requestedTags, courseWeights,
                Set.of(), Set.of(),
                request.companionType(), request.timeSlot(), selectionCount
        );
        return new ScoredCourse(course, result);
    }

    // 서로 다른 앵커를 먼저 선택하고 같은 앵커는 최대 두 코스까지만 허용한다.
    private List<ScoredCourse> selectWithAnchorLimit(List<ScoredCourse> candidates, List<ScoredCourse> selected) {
        if (selected.size() >= RESULT_LIMIT) return selected;

        Map<Long, Integer> anchorCounts = new HashMap<>();
        selected.forEach(course -> anchorCounts.merge(course.course().getAnchorPlace().getId(), 1, Integer::sum));

        for (ScoredCourse course : candidates) {
            Long anchorId = course.course().getAnchorPlace().getId();
            if (!selected.contains(course) && !anchorCounts.containsKey(anchorId)) {
                selected.add(course);
                anchorCounts.put(anchorId, 1);
            }
            if (selected.size() == RESULT_LIMIT) return selected;
        }
        for (ScoredCourse course : candidates) {
            Long anchorId = course.course().getAnchorPlace().getId();
            if (!selected.contains(course) && anchorCounts.getOrDefault(anchorId, 0) < MAX_COURSES_PER_ANCHOR) {
                selected.add(course);
                anchorCounts.merge(anchorId, 1, Integer::sum);
            }
            if (selected.size() == RESULT_LIMIT) break;
        }
        return selected;
    }

    private record ScoredCourse(Course course, RecommendationScoreCalculator.Result result) {
        private double totalScore() {
            return result.totalScore();
        }

        private RecommendedCourseResponse toResponse() {
            RecommendationScoreCalculator.Breakdown breakdown = result.breakdown();
            return RecommendedCourseResponse.from(
                    course, result.totalScore(), result.matchType(), result.matchedTags(), result.unmatchedTags(),
                    new ScoreBreakdown(
                            breakdown.tagScore(), breakdown.companionScore(),
                            breakdown.timeSlotScore(), breakdown.popularityScore())
            );
        }
    }
}
