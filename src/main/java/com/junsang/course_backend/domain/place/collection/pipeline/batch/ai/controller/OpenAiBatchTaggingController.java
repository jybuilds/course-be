package com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.controller;

import com.junsang.course_backend.domain.place.collection.pipeline.batch.ai.service.OpenAiBatchTaggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "장소 수집 및 정제 - 미사용")
public class OpenAiBatchTaggingController {

    private final OpenAiBatchTaggingService openAiBatchTaggingService;

    // AI 태깅 대기 Temp를 OpenAI Batch API에 비동기로 제출한다.
    @PostMapping
    @Operation(
            summary = "AI Batch 태깅 제출",
            description = """
                    AI_TAGGING/PENDING 상태의 Temp를 최대 limit건까지 OpenAI Batch에 비동기로 제출하고 로컬 Job ID를 반환합니다.
                    제출만 수행하므로 결과는 즉시 DB에 반영되지 않습니다. Batch 완료 후 결과 반영 API를 호출해야 합니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<Long> submit(@RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(openAiBatchTaggingService.submitPending(limit));
    }

    // 완료된 Temp를 외부 장소 API 재호출 없이 새 AI 정책으로 다시 태깅한다.
    @PostMapping("/re-tag")
    @Operation(
            summary = "AI 재태깅 제출",
            description = """
                    이미 AI 태깅이 완료된 Temp를 카카오·네이버·블로그 원본 자료와 최신 규칙으로 다시 태깅합니다.
                    실패 재시도용이 아니라 태그·분류 정책을 변경한 뒤 기존 장소를 갱신하는 용도입니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<Long> reTag(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(openAiBatchTaggingService.submitCompletedForRetagging(limit));
    }

    // 완료된 OpenAI Batch 결과가 있으면 DB에 반영한다.
    @PostMapping("/{jobId}/collect")
    @Operation(
            summary = "AI Batch 결과 반영",
            description = """
                    Job ID의 OpenAI Batch 완료 여부를 확인하고, 완료된 결과를 Place와 PlaceTag에 저장하거나 갱신합니다.
                    아직 처리 중이면 false를 반환하므로 나중에 다시 호출해야 합니다.
                    """,
            deprecated = true
    )
    public ResponseEntity<Boolean> collect(@PathVariable Long jobId) {
        return ResponseEntity.ok(openAiBatchTaggingService.collect(jobId));
    }
}
