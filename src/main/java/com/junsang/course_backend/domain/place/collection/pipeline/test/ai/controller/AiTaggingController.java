package com.junsang.course_backend.domain.place.collection.pipeline.test.ai.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// OpenAI 장소 태깅과 최종 Place 저장을 직접 실행하는 내부 진입점이다.
@RestController
@RequestMapping("/internal/place-refinement/ai")
@RequiredArgsConstructor
@Tag(name = "장소 수집 및 정제 - 미사용")
public class AiTaggingController {

    private final AiTaggingService aiTaggingService;

    // Temp 한 건을 AI로 태깅하고 최종 Place로 저장한다.
    @PostMapping("/{tempId}")
    @Operation(
            summary = "지정 하나 AI 태깅 (배치 X) - 테스트",
            description = "테스트용으로 Temp 한 건을 즉시 AI 태깅하고 Place에 반영합니다.",
            deprecated = true
    )
    public ResponseEntity<AiTaggingResponse> tag(@PathVariable Long tempId) {
        return ResponseEntity.ok(aiTaggingService.tag(tempId));
    }

    // AI 태깅 대기 데이터를 최대 20개까지 묶어서 처리한다.
    @PostMapping
    @Operation(
            summary = "즉시 AI 태깅 (배치 X) - 테스트",
            description = "테스트용으로 대기 Temp를 동기식 AI 호출로 태깅합니다. 운영에서는 OpenAI Batch 태깅을 사용합니다.",
            deprecated = true
    )
    public ResponseEntity<AiTaggingBatchResponse> tagPending(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(aiTaggingService.tagPending(limit));
    }
}
