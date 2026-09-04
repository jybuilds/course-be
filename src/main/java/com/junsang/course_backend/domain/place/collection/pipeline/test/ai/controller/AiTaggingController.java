package com.junsang.course_backend.domain.place.collection.pipeline.test.ai.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingBatchResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.dto.AiTaggingResponse;
import com.junsang.course_backend.domain.place.collection.pipeline.common.ai.service.AiTaggingService;
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
public class AiTaggingController {

    private final AiTaggingService aiTaggingService;

    // Temp 한 건을 AI로 태깅하고 최종 Place로 저장한다.
    @PostMapping("/{tempId}")
    public ResponseEntity<AiTaggingResponse> tag(@PathVariable Long tempId) {
        return ResponseEntity.ok(aiTaggingService.tag(tempId));
    }

    // AI 태깅 대기 데이터를 최대 20개까지 묶어서 처리한다.
    @PostMapping
    public ResponseEntity<AiTaggingBatchResponse> tagPending(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(aiTaggingService.tagPending(limit));
    }
}
