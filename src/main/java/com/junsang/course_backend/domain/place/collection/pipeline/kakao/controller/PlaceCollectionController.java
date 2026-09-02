package com.junsang.course_backend.domain.place.collection.pipeline.kakao.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.kakao.dto.PlaceCollectionResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoKeywordSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 정기 수집용 카카오 Area 배치 작업을 직접 실행하는 내부 진입점이다.
@RestController
@RequestMapping("/internal/place-collection/batch")
@RequiredArgsConstructor
public class PlaceCollectionController {

    private final KakaoAreaCollectionService kakaoAreaCollectionService;
    private final PlaceCollectionTempRepository tempRepository;

    // 지정한 City의 모든 Area 수집 Job을 동기적으로 실행한다.
    @PostMapping("/cities/{cityId}")
    public ResponseEntity<Void> collectCity(@PathVariable Long cityId) {
        kakaoAreaCollectionService.collectCity(cityId);
        return ResponseEntity.noContent().build();
    }

    // 지정한 Area 하나의 카카오 장소 수집을 동기적으로 실행한다.
    @PostMapping("/areas/{areaId}")
    public ResponseEntity<PlaceCollectionResponse> collectArea(@PathVariable Long areaId) {
        kakaoAreaCollectionService.collectArea(areaId);
        return ResponseEntity.ok(new PlaceCollectionResponse(areaId, tempRepository.countByAreaId(areaId)));
    }

    // 지정한 Area에서 카카오 키워드 검색 결과를 저장하지 않고 확인한다.
    @GetMapping("/areas/{areaId}/keyword")
    public ResponseEntity<KakaoKeywordSearchResponse> searchKeyword(
            @PathVariable Long areaId,
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page
    ) {
        return ResponseEntity.ok(kakaoAreaCollectionService.searchKeyword(areaId, query, page));
    }

    // 지정한 Area에서 카카오 카테고리 검색 결과를 저장하지 않고 확인한다.
    @GetMapping("/areas/{areaId}/category")
    public ResponseEntity<KakaoKeywordSearchResponse> searchCategory(
            @PathVariable Long areaId,
            @RequestParam String categoryGroupCode,
            @RequestParam(defaultValue = "1") int page
    ) {
        return ResponseEntity.ok(kakaoAreaCollectionService.searchCategory(areaId, categoryGroupCode, page));
    }
}
