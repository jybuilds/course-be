package com.junsang.course_backend.domain.place.controller;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/// 저장 완료된 장소와 태그를 사람이 빠르게 검토하는 테스트 화면을 제공한다.
@Controller
@RequiredArgsConstructor
@Tag(name = "장소 확인 - 개발용")
public class PlaceCheckController {

    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;

    // 정적 화면을 현재 서버 context path 아래에서 연다.
    @GetMapping("/check-place")
    public String checkPlace() {
        return "forward:/place-check.html";
    }

    // 현재 Place에 저장된 링크와 태그를 페이지 단위로 반환한다.
    @GetMapping("/api/places/check")
    @ResponseBody
    @Transactional(readOnly = true)
    @Operation(
            summary = "저장된 장소 확인",
            description = "개발용 장소 확인 화면에서 사용할 Place와 태그 데이터를 페이지 단위로 조회합니다.",
            deprecated = true
    )
    public PlaceCheckPageResponse getPlaces(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        Page<Place> placePage = placeRepository.findAllByOrderByIdAsc(PageRequest.of(page, size));
        Set<Long> placeIds = placePage.getContent().stream()
                .map(Place::getId)
                .collect(java.util.stream.Collectors.toSet());

        // 현재 페이지의 태그를 한 번에 읽어 목록 렌더링의 N+1 조회를 막는다.
        Map<Long, List<PlaceCheckTagResponse>> tagsByPlaceId = groupTags(placeIds);

        List<PlaceCheckRowResponse> places = placePage.getContent().stream()
                .map(place -> PlaceCheckRowResponse.from(place, tagsByPlaceId.getOrDefault(place.getId(), List.of())))
                .toList();

        return new PlaceCheckPageResponse(
                places,
                placePage.getNumber(),
                placePage.getSize(),
                placePage.getTotalPages(),
                placePage.getTotalElements()
        );
    }

    // PlaceTag 결과를 Place ID별로 묶어 응답 조립에 사용한다.
    private Map<Long, List<PlaceCheckTagResponse>> groupTags(Set<Long> placeIds) {
        Map<Long, List<PlaceCheckTagResponse>> tagsByPlaceId = new HashMap<>();

        if (placeIds.isEmpty()) {
            return tagsByPlaceId;
        }

        for (PlaceTag placeTag : placeTagRepository.findWithTagByPlaceIdIn(placeIds)) {
            tagsByPlaceId.computeIfAbsent(placeTag.getPlace().getId(), ignored -> new ArrayList<>())
                    .add(new PlaceCheckTagResponse(
                            placeTag.getTag().getCode(),
                            placeTag.getTag().getDisplayName(),
                            placeTag.getWeight()
                    ));
        }

        return tagsByPlaceId;
    }

    public record PlaceCheckPageResponse(
            List<PlaceCheckRowResponse> places,
            int page,
            int size,
            int totalPages,
            long totalElements
    ) {
    }

    public record PlaceCheckRowResponse(
            Long id,
            String name,
            String placeType,
            String addressName,
            String roadAddressName,
            String kakaoUrl,
            String naverUrl,
            List<PlaceCheckTagResponse> tags
    ) {

        private static PlaceCheckRowResponse from(Place place, List<PlaceCheckTagResponse> tags) {
            return new PlaceCheckRowResponse(
                    place.getId(),
                    place.getName(),
                    place.getPlaceType().name(),
                    place.getAddressName(),
                    place.getRoadAddressName(),
                    place.getPlaceUrl(),
                    place.getNaverSearchUrl(),
                    tags
            );
        }
    }

    public record PlaceCheckTagResponse(
            String code,
            String displayName,
            int weight
    ) {
    }
}
