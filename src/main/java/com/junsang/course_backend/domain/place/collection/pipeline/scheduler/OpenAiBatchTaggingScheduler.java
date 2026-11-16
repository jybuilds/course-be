package com.junsang.course_backend.domain.place.collection.pipeline.scheduler;

import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingStatus;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.repository.OpenAiBatchTaggingJobRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.OpenAiBatchTaggingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// 제출된 OpenAI Batch 결과를 주기적으로 수집한다.
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "place-collection.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OpenAiBatchTaggingScheduler {

    private final OpenAiBatchTaggingJobRepository jobRepository;
    private final OpenAiBatchTaggingService openAiBatchTaggingService;

    @Scheduled(fixedDelayString = "${place-collection.ai.batch.retry-poll-millis:60000}")
    public void submitRetryableFailures() {
        try {
            openAiBatchTaggingService.submitRetries();
        } catch (RuntimeException exception) {
            log.warn("AI Batch 재시도 제출 실패: {}", exception.getClass().getSimpleName());
        }
    }

    // 원격 완료·실패를 확인해 Temp가 PROCESSING 상태에 멈지 않도록 한다.
    @Scheduled(fixedDelayString = "${place-collection.ai.batch.poll-interval-millis:60000}")
    public void collectSubmittedBatches() {
        jobRepository.findByStatus(OpenAiBatchTaggingStatus.SUBMITTED).stream()
                .filter(job -> job.getErrorCode() == null || !com.junsang.course_backend.domain.place.collection.pipeline.support.error.CollectionErrorPolicy.POLL_BLOCKED.contains(job.getErrorCode()))
                .forEach(job -> {
            try {
                openAiBatchTaggingService.collect(job.getId());
            } catch (RuntimeException exception) {
                // 일시적인 조회 실패는 SUBMITTED를 유지해 다음 주기에 다시 확인한다.
                log.warn("AI Batch 결과 확인 실패: jobId={}, type={}", job.getId(), exception.getClass().getSimpleName());
            }
        });
    }
}
