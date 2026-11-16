package com.junsang.course_backend.domain.course.creation.service;

import com.junsang.course_backend.domain.course.creation.dto.response.CourseCandidateResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseDraftResponse;
import com.junsang.course_backend.domain.course.creation.dto.response.CourseDraftResponse.SlotResponse;
import com.junsang.course_backend.domain.course.entity.CourseItemRole;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/// 앞 칸의 추천 장소를 기준으로 다음 칸 후보를 이어 붙여 코스 초안을 만든다.
@Service
@RequiredArgsConstructor
public class CourseDraftService {
    // 칸 구성을 지정하지 않으면 기획서의 기본 일정인 활동 → 식사 → 카페로 만든다.
    static final List<CourseItemRole> DEFAULT_ROLES =
            List.of(CourseItemRole.ACTIVITY, CourseItemRole.MEAL, CourseItemRole.CAFE);

    private final PlaceRepository placeRepository;
    private final CourseCandidateService courseCandidateService;

    /// 칸마다 추천순 후보를 구하고, 0번째 장소를 다음 칸의 거리 기준과 제외 목록에 넣는다.
    @Transactional(readOnly = true)
    public CourseDraftResponse draft(
            Long anchorPlaceId,
            List<CourseItemRole> roles,
            List<String> tagCodes,
            Integer size
    ) {
        Place anchor = placeRepository.findById(anchorPlaceId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_ANCHOR_NOT_FOUND));
        List<CourseItemRole> slotRoles = roles == null || roles.isEmpty() ? DEFAULT_ROLES : roles;

        List<SlotResponse> slots = new ArrayList<>();
        List<Long> recommendedPlaceIds = new ArrayList<>();
        Long basePlaceId = anchor.getId();
        for (CourseItemRole role : slotRoles) {
            List<CourseCandidateResponse> places = courseCandidateService.findCandidates(
                    anchor.getId(), role, basePlaceId, List.copyOf(recommendedPlaceIds), tagCodes, size
            );
            slots.add(new SlotResponse(slots.size() + 1, role, basePlaceId, places));
            // 후보가 없는 칸은 비워 두고, 다음 칸은 마지막으로 추천된 장소를 계속 기준으로 삼는다.
            if (places.isEmpty()) continue;
            basePlaceId = places.getFirst().placeId();
            recommendedPlaceIds.add(basePlaceId);
        }
        return new CourseDraftResponse(anchor.getId(), anchor.getName(), slots);
    }
}
