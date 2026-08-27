package com.junsang.course_backend.recommendation.service;

import com.junsang.course_backend.domain.place.repository.CityRepository;
import com.junsang.course_backend.domain.place.repository.TagOptionRepository;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.domain.course.entity.CompanionType;
import com.junsang.course_backend.domain.course.entity.TimeSlot;
import com.junsang.course_backend.recommendation.dto.response.CompanionTypeOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.CityOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.RecommendationOptionsResponse;
import com.junsang.course_backend.recommendation.dto.response.TagOptionResponse;
import com.junsang.course_backend.recommendation.dto.response.TimeSlotOptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 추천 입력 화면에 필요한 선택지를 조합한다.
@Service
@RequiredArgsConstructor
public class RecommendationOptionService {
    private final CityRepository cityRepository;
    private final TagRepository tagRepository;
    private final TagOptionRepository tagOptionRepository;

    // 도시, 태그, 시간대 선택지를 반환한다.
    @Transactional(readOnly = true)
    public RecommendationOptionsResponse getOptions() {
        return new RecommendationOptionsResponse(
                cityRepository.findAll().stream().map(CityOptionResponse::from).toList(),
                tagRepository.findAll().stream()
                        .filter(tag -> tag.isActive())
                        .sorted(java.util.Comparator.comparingInt(tag -> tag.getDisplayOrder()))
                        .map(tag -> TagOptionResponse.from(
                                tag,
                                tagOptionRepository.findByTagIdAndIsActiveTrueOrderByDisplayOrder(tag.getId())
                                        .stream().map(option -> option.getOptionName()).toList()))
                .toList(),
                java.util.Arrays.stream(TimeSlot.values()).map(TimeSlotOptionResponse::from).toList(),
                java.util.Arrays.stream(CompanionType.values()).map(CompanionTypeOptionResponse::from).toList(),
                com.junsang.course_backend.recommendation.dto.response.SelectionRulesResponse.defaults()
        );
    }
}
