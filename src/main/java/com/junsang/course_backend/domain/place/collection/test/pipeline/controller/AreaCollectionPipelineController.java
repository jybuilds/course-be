package com.junsang.course_backend.domain.place.collection.test.pipeline.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.service.OpenAiBatchTaggingService;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoAreaCollectionService;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.dto.PlaceRefinementBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.naver.service.PlaceRefinementService;
import com.junsang.course_backend.domain.place.collection.test.pipeline.dto.AreaCollectionPipelineResponse;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
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
public class AreaCollectionPipelineController {

    private final KakaoAreaCollectionService kakaoAreaCollectionService;
    private final PlaceRefinementService placeRefinementService;
    private final OpenAiBatchTaggingService openAiBatchTaggingService;
    private final PlaceCollectionTempRepository tempRepository;

    // 카카오 수집, 네이버·블로그 정제, OpenAI Batch 제출을 Area 하나에 순서대로 실행한다.
    @PostMapping("/areas/{areaId}")
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
