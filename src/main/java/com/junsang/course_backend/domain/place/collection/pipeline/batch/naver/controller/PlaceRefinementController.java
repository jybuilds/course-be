package com.junsang.course_backend.domain.place.collection.pipeline.batch.naver.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service.PlaceRefinementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 네이버 장소 정제를 직접 실행하기 위한 내부 진입점이다.
@RestController
@RequestMapping("/internal/place-refinement")
@RequiredArgsConstructor
@Tag(name = "장소 수집 및 정제 - 미사용")
public class PlaceRefinementController {

    private final PlaceRefinementService placeRefinementService;

    // 네이버 정제 대기 데이터를 제한 개수만 순차 처리한다.
    @PostMapping("/naver")
    @Operation(
            summary = "네이버 일괄 정제",
            description = """
                    NAVER_ENRICHMENT/PENDING 상태의 Temp를 ID 순서로 limit만큼 네이버 장소·블로그 정보로 정제합니다.
                    특정 Area만 처리하지 않고 전체 대기 대상을 처리합니다. 정제 성공 건은 AI 태깅 대기 상태가 됩니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<PlaceRefinementBatchResponse> refinePending(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(placeRefinementService.refinePending(limit));
    }
}
