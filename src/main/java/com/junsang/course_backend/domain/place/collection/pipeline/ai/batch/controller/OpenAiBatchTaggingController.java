package com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.batch.service.OpenAiBatchTaggingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 초기·정기 수집용 OpenAI Batch 태깅 Job을 제출하고 결과를 회수한다.
@RestController
@RequestMapping("/internal/place-collection/batch/ai")
@RequiredArgsConstructor
public class OpenAiBatchTaggingController {

    private final OpenAiBatchTaggingService openAiBatchTaggingService;

    // AI 태깅 대기 Temp를 OpenAI Batch API에 비동기로 제출한다.
    @PostMapping
    public ResponseEntity<Long> submit(@RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(openAiBatchTaggingService.submitPending(limit));
    }

    // 완료된 OpenAI Batch 결과가 있으면 DB에 반영한다.
    @PostMapping("/{jobId}/collect")
    public ResponseEntity<Boolean> collect(@PathVariable Long jobId) {
        return ResponseEntity.ok(openAiBatchTaggingService.collect(jobId));
    }
}
