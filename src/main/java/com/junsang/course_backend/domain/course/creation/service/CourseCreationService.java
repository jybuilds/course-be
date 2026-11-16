package com.junsang.course_backend.domain.course.creation.service;

import com.junsang.course_backend.domain.course.creation.dto.request.CourseCreateRequest;
import com.junsang.course_backend.domain.course.creation.dto.response.CreatedCourseResponse;
import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.repository.CourseRepository;
import com.junsang.course_backend.domain.course.service.CourseTagGenerationService;
import com.junsang.course_backend.domain.place.entity.CourseTag;
import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.Tag;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.global.exception.BusinessException;
import com.junsang.course_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// 사용자가 고른 앵커와 일정 장소를 코스로 저장한다.
@Service
@RequiredArgsConstructor
public class CourseCreationService {
    // 첫 일정의 순서 값이다. CourseItem은 0 이상을 요구한다.
    private static final int FIRST_ITEM_ORDER = 1;

    private final CourseRepository courseRepository;
    private final PlaceRepository placeRepository;
    private final CourseTagGenerationService courseTagGenerationService;

    /// 앵커의 Area를 코스 지역으로 삼고 요청 순서대로 일정을 저장한 뒤 코스 태그를 계산한다.
    @Transactional
    public CreatedCourseResponse create(CourseCreateRequest request) {
        Place anchor = findActivePlace(request.anchorPlaceId(), ErrorCode.COURSE_ANCHOR_NOT_FOUND);
        List<Long> itemPlaceIds = request.scheduleItems().stream()
                .map(CourseCreateRequest.ScheduleItemRequest::placeId)
                .toList();
        validateNoDuplicatePlace(anchor.getId(), itemPlaceIds);

        Map<Long, Place> placesById = loadActivePlaces(itemPlaceIds);

        Course course = Course.create(
                anchor.getArea(),
                anchor,
                request.title(),
                request.description()
        );
        // 요청 배열의 순서를 그대로 방문 순서로 쓰기 때문에 건너뛴 단계가 있어도 빈 순서가 생기지 않는다.
        int itemOrder = FIRST_ITEM_ORDER;
        for (CourseCreateRequest.ScheduleItemRequest item : request.scheduleItems()) {
            course.addScheduleItem(placesById.get(item.placeId()), item.role(), itemOrder++);
        }

        Course saved = courseRepository.save(course);
        // 저장 직후 코스 태그를 만들어야 대표 코스 추천에서 태그 매칭 대상이 된다.
        courseTagGenerationService.recalculate(saved.getId());

        return CreatedCourseResponse.of(saved, generatedTagCodes(saved));
    }

    // 같은 장소가 앵커와 일정에 중복으로 들어가지 않도록 막는다.
    private void validateNoDuplicatePlace(Long anchorPlaceId, List<Long> itemPlaceIds) {
        Set<Long> seen = new LinkedHashSet<>();
        seen.add(anchorPlaceId);
        for (Long placeId : itemPlaceIds) {
            if (!seen.add(placeId)) throw new BusinessException(ErrorCode.COURSE_DUPLICATE_PLACE);
        }
    }

    // 요청한 일정 장소가 모두 존재하고 활성 상태인지 한 번의 조회로 확인한다.
    private Map<Long, Place> loadActivePlaces(List<Long> placeIds) {
        Map<Long, Place> placesById = placeRepository.findAllById(placeIds).stream()
                .filter(Place::isActive)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (placesById.size() != placeIds.size()) {
            throw new BusinessException(ErrorCode.COURSE_PLACE_NOT_FOUND);
        }
        return placesById;
    }

    private Place findActivePlace(Long placeId, ErrorCode notFoundCode) {
        return placeRepository.findById(placeId)
                .filter(Place::isActive)
                .orElseThrow(() -> new BusinessException(notFoundCode));
    }

    private Set<String> generatedTagCodes(Course course) {
        return course.getTags().stream()
                .map(CourseTag::getTag)
                .map(Tag::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
