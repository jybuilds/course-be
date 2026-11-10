package com.junsang.course_backend.domain.place.controller;

import com.junsang.course_backend.domain.place.entity.Place;
import com.junsang.course_backend.domain.place.entity.PlaceTag;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service.NaverSearchUrlCreator;
import com.junsang.course_backend.domain.place.repository.PlaceRepository;
import com.junsang.course_backend.domain.place.repository.PlaceTagRepository;
import com.junsang.course_backend.domain.place.service.PlaceNaverLinkRefreshService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/// 저장 완료된 장소와 태그를 사람이 빠르게 검토하는 테스트 화면을 제공한다.
@Controller
@RequiredArgsConstructor
@Tag(name = "장소 확인 - 개발용")
public class PlaceCheckController {

    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;
    private final NaverSearchUrlCreator naverSearchUrlCreator;
    private final PlaceNaverLinkRefreshService placeNaverLinkRefreshService;

    // 정적 화면을 현재 서버 context path 아래에서 연다.
    @GetMapping("/check-place")
    public String checkPlace() {
        return "forward:/place-check.html";
    }

    // 저장된 Place의 원본 정보, AI 결과, 링크와 태그를 페이지 단위로 반환한다.
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
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PlaceType placeType
    ) {
        Page<Place> placePage = placeRepository.findAllForCheckOrderByPriority(placeType, PageRequest.of(page, size));
        Set<Long> placeIds = placePage.getContent().stream()
                .map(Place::getId)
                .collect(java.util.stream.Collectors.toSet());

        // 현재 페이지의 태그를 한 번에 읽어 목록 렌더링의 N+1 조회를 막는다.
        Map<Long, List<PlaceCheckTagResponse>> tagsByPlaceId = groupTags(placeIds);

        List<PlaceCheckRowResponse> places = placePage.getContent().stream()
                .map(place -> PlaceCheckRowResponse.from(
                        place,
                        tagsByPlaceId.getOrDefault(place.getId(), List.of()),
                        naverSearchUrlCreator.create(place.getName(), place.getAddressName())
                ))
                .toList();

        return new PlaceCheckPageResponse(
                places,
                placePage.getNumber(),
                placePage.getSize(),
                placePage.getTotalPages(),
                placePage.getTotalElements()
        );
    }

    // 저장된 장소의 네이버 검색 링크만 현재 생성 규칙으로 일괄 갱신한다.
    @PostMapping("/api/places/check/naver-links/rebuild")
    @ResponseBody
    @Operation(
            summary = "저장된 장소의 네이버 링크 일괄 갱신",
            description = "기존 장소명과 지번 주소로 네이버 검색 링크만 다시 생성합니다. 태그·점수·수집 상태는 변경하지 않습니다.",
            deprecated = true
    )
    public PlaceNaverLinkRefreshService.RefreshResult rebuildNaverLinks() {
        return placeNaverLinkRefreshService.refreshAll();
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
            String provider,
            String providerPlaceId,
            Long areaId,
            String areaName,
            String name,
            String placeType,
            List<String> keywords,
            List<String> imageUrls,
            String addressName,
            String roadAddressName,
            String sourceCategoryName,
            String sourceCategoryGroupCode,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String kakaoUrl,
            String naverUrl,
            String phone,
            boolean anchorCandidate,
            boolean active,
            Short priorityExperience,
            Short priorityDistinctiveness,
            Short priorityOutingDemand,
            int totalPriorityScore,
            String operatingHours,
            String operatingDays,
            String naverUrlPreview,
            java.time.LocalDateTime lastSyncedAt,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            List<PlaceCheckTagResponse> tags
    ) {

        private static PlaceCheckRowResponse from(
                Place place,
                List<PlaceCheckTagResponse> tags,
                String naverUrlPreview
        ) {
            return new PlaceCheckRowResponse(
                    place.getId(),
                    place.getProvider().name(),
                    place.getProviderPlaceId(),
                    place.getArea().getId(),
                    place.getArea().getName(),
                    place.getName(),
                    place.getPlaceType().name(),
                    place.getKeywords(),
                    place.getImageUrls(),
                    place.getAddressName(),
                    place.getRoadAddressName(),
                    place.getSourceCategoryName(),
                    place.getSourceCategoryGroupCode(),
                    place.getLatitude(),
                    place.getLongitude(),
                    place.getPlaceUrl(),
                    place.getNaverSearchUrl(),
                    place.getPhone(),
                    place.isAnchorCandidate(),
                    place.isActive(),
                    place.getPriorityExperience(),
                    place.getPriorityDistinctiveness(),
                    place.getPriorityOutingDemand(),
                    place.getPriorityScore(),
                    place.getOperatingHours(),
                    place.getOperatingDays(),
                    naverUrlPreview,
                    place.getLastSyncedAt(),
                    place.getCreatedAt(),
                    place.getUpdatedAt(),
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
