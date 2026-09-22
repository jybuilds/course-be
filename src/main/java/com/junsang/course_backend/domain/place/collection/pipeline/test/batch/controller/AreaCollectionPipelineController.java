package com.junsang.course_backend.domain.place.collection.pipeline.test.batch.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.batch.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.naver.service.PlaceRefinementService;
import com.junsang.course_backend.domain.place.collection.pipeline.test.batch.dto.AreaCollectionPipelineResponse;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// Area 한 곳의 초기 수집 파이프라인을 수동 검증하는 내부 API다.
@RestController
@RequestMapping("/internal/place-collection/test")
@RequiredArgsConstructor
@Tag(name = "장소 수집 및 정제 - 미사용")
public class AreaCollectionPipelineController {

    private final KakaoAreaCollectionService kakaoAreaCollectionService;
    private final PlaceRefinementService placeRefinementService;
    private final OpenAiBatchTaggingService openAiBatchTaggingService;
    private final PlaceCollectionTempRepository tempRepository;

    // 카카오 수집, 네이버·블로그 정제, OpenAI Batch 제출을 Area 하나에 순서대로 실행한다.
    @PostMapping("/areas/{areaId}")
    @Operation(
            summary = "Area 통합 수집·정제 테스트",
            description = "테스트용으로 Area 하나의 카카오 수집, 네이버·블로그 정제, OpenAI Batch 제출을 순서대로 실행합니다.",
            deprecated = true
    )
    public ResponseEntity<AreaCollectionPipelineResponse> run(
            @PathVariable Long areaId,
            @RequestParam(defaultValue = "1000") int refinementLimit,
            @RequestParam(defaultValue = "1000") int taggingLimit
    ) {
        kakaoAreaCollectionService.collectArea(areaId);
        PlaceRefinementBatchResponse refinement = placeRefinementService.refinePendingByArea(areaId, refinementLimit);
        Long aiBatchJobId = openAiBatchTaggingService.submitPendingByArea(areaId, taggingLimit);

        return ResponseEntity.ok(new AreaCollectionPipelineResponse(
                areaId,
                tempRepository.countByAreaId(areaId),
                refinement,
                aiBatchJobId
        ));
    }
}
