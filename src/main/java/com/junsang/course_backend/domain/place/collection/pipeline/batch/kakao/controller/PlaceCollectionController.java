package com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.dto.PlaceCollectionResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 정기 수집용 카카오 Area 배치 작업을 직접 실행하는 내부 진입점이다.
@RestController
@RequestMapping("/internal/place-collection/batch")
@RequiredArgsConstructor
@Tag(name = "장소 수집 및 정제 - 미사용")
public class PlaceCollectionController {

    private final KakaoAreaCollectionService kakaoAreaCollectionService;
    private final PlaceCollectionTempRepository tempRepository;

    // 지정한 City의 모든 Area 수집 Job을 동기적으로 실행한다.
    @PostMapping("/cities/{cityId}")
    @Operation(
            summary = "City 전체 장소 수집",
            description = """
                    City에 속한 모든 Area를 순회하며 카카오 장소를 수집해 PlaceCollectionTemp에 저장합니다.
                    이 API는 카카오 수집까지만 수행합니다. 네이버 정제와 AI 태깅은 각각 별도 내부 API로 진행해야 합니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<Void> collectCity(@PathVariable Long cityId) {
        kakaoAreaCollectionService.collectCity(cityId);
        return ResponseEntity.noContent().build();
    }

    // 지정한 Area 하나의 카카오 장소 수집을 동기적으로 실행한다.
    @PostMapping("/areas/{areaId}")
    @Operation(
            summary = "Area 단위 장소 수집",
            description = """
                    지정한 Area 하나의 카카오 수집 프로필을 실행하고 결과를 PlaceCollectionTemp에 저장합니다.
                    City 전체 수집을 대신하는 세부 운영·테스트용 단계입니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<PlaceCollectionResponse> collectArea(@PathVariable Long areaId) {
        kakaoAreaCollectionService.collectArea(areaId);
        return ResponseEntity.ok(new PlaceCollectionResponse(areaId, tempRepository.countByAreaId(areaId)));
    }

}
