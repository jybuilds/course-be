package com.junsang.course_backend.domain.course.service;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.domain.tag.PlaceTag;
import com.junsang.course_backend.domain.tag.Tag;
import com.junsang.course_backend.domain.tag.repository.PlaceTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// 코스에 실제 포함된 장소 태그를 코스 검색용 태그로 요약한다.
@Service
@RequiredArgsConstructor
public class CourseTagGenerationService {
    private static final double MAX_WEIGHT_RATIO = 0.4;
    private static final double COVERAGE_RATIO = 60.0;
    private static final int MIN_STORED_WEIGHT = 30;

    private final CourseRepository courseRepository;
    private final PlaceTagRepository placeTagRepository;

    // 앵커와 일정에서 중복 장소를 제거한 뒤 태그 강도와 등장 비율을 합산한다.
    @Transactional
    public void recalculate(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코스입니다."));

        Set<Long> placeIds = course.getScheduleItems().stream()
                .map(item -> item.getPlace().getId())
                .collect(Collectors.toSet());
        placeIds.add(course.getAnchorPlace().getId());

        Map<Long, List<PlaceTag>> tagsByTagId = placeTagRepository.findByPlaceIdIn(placeIds).stream()
                .filter(placeTag -> placeTag.getTag().isActive())
                .collect(Collectors.groupingBy(placeTag -> placeTag.getTag().getId()));

        Map<Tag, Integer> calculatedWeights = new LinkedHashMap<>();
        tagsByTagId.values().forEach(placeTags -> {
            int maxWeight = placeTags.stream().mapToInt(PlaceTag::getWeight).max().orElse(0);
            long taggedPlaceCount = placeTags.stream().map(tag -> tag.getPlace().getId()).distinct().count();
            int weight = (int) Math.round(
                    maxWeight * MAX_WEIGHT_RATIO + taggedPlaceCount * COVERAGE_RATIO / placeIds.size());
            if (weight >= MIN_STORED_WEIGHT) calculatedWeights.put(placeTags.getFirst().getTag(), weight);
        });

        course.synchronizeTags(calculatedWeights);
    }
}
